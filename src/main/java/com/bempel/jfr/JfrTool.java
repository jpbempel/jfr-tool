package com.bempel.jfr;

import static org.openjdk.jmc.common.item.Attribute.attr;
import static org.openjdk.jmc.common.item.ItemToolkit.accessor;
import static org.openjdk.jmc.flightrecorder.JfrAttributes.EVENT_STACKTRACE;

import com.bempel.jfr.jdk.ChunkParser;
import com.bempel.jfr.jdk.ConstantMap;
import com.bempel.jfr.jdk.LongMap;
import com.bempel.jfr.jdk.RecordingFile;
import org.openjdk.jmc.common.IDisplayable;
import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCMethod;
import org.openjdk.jmc.common.IMCStackTrace;
import org.openjdk.jmc.common.IMCType;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.IItemFilter;
import org.openjdk.jmc.common.item.IItemIterable;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.item.ItemFilters;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.flightrecorder.JfrAttributes;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.jdk.JdkAttributes;
import org.openjdk.jmc.flightrecorder.jdk.JdkFilters;
import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(subcommands = {Stats.class, Dump.class, FlameGraph.class, GC.class},
        mixinStandardHelpOptions = true, version = "1.0")
public class JfrTool {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JfrTool()).execute(args);
        System.exit(exitCode);
    }
}

@CommandLine.Command(name = "stats", description = "Displays statistics about constant pools")
class Stats implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "JfrFile", description = "JFR file")
    String jfrFileName;

    @Override
    public Integer call() throws Exception {
        RecordingFile recordingFile = new RecordingFile(Paths.get(jfrFileName));
        // force to load all chunks
        while (recordingFile.hasMoreEvents()) {
            recordingFile.readEvent();
        }
        List<ChunkParser> chunks = recordingFile.getChunks();
        Map<String, PoolStats> poolStatsMap = new HashMap<>();
        for (ChunkParser chunk : chunks) {
            for (Map.Entry<Long, ConstantMap> entry : chunk.getConstantPools().entrySet()) {
                ConstantMap constantMap = entry.getValue();
                int count = constantMap.size();

                Set<Object> duplicates = new HashSet<>();
                for (Map.Entry<Long, Object> e : constantMap.entrySet()) {
                    Object o = e.getValue();
                    duplicates.add(o);
                }
                PoolStats poolStats = poolStatsMap.get(constantMap.getName());
                Long refPoolSize = chunk.getPoolSizes().get(constantMap.getName());
                long poolSize = refPoolSize != null ? refPoolSize : 0;
                if (poolStats == null) {
                    poolStats = new PoolStats(entry.getKey(), entry.getValue(), count, duplicates.size(), poolSize);
                    poolStatsMap.put(constantMap.getName(), poolStats);
                } else {
                    poolStats.count += count;
                    poolStats.distinctCount += duplicates.size();
                    poolStats.size += poolSize;
                }
            }
        }
        System.out.println("Constant pool name size(B) count distinct");
        List<PoolStats> statsList = poolStatsMap.values().stream()
                .sorted(Comparator.<PoolStats>comparingInt(poolStats -> poolStats.count).reversed())
                .collect(Collectors.toList());
        long poolTotalSize = 0;
        for (PoolStats stats : statsList) {
            String distinctStr = "";
            if (stats.count != stats.distinctCount) {
                distinctStr = String.valueOf(stats.distinctCount);
            }
            poolTotalSize += stats.size;
            System.out.printf("%s %,d %,d %s\n", stats.map.getName(), stats.size, stats.count, distinctStr);
        }
        System.out.printf("Total pools size: %,d\n", poolTotalSize);
        return 0;
    }
}

@CommandLine.Command(name = "dump", description = "Dumps data from a constant pool")
class Dump implements Callable<Integer> {
    @CommandLine.Parameters(paramLabel = "constantPoolName", description = "Constant pool name to dump")
    String constantPoolName;
    @CommandLine.Parameters(paramLabel = "JfrFileName", description = "JFR file")
    String jfrFileName;

    @Override
    public Integer call() throws Exception {
        RecordingFile recordingFile = new RecordingFile(Paths.get(jfrFileName));
        List<ChunkParser> chunks = recordingFile.getChunks();
        for (ChunkParser chunk : chunks) {
            for (Map.Entry<Long, ConstantMap> entryMap : chunk.getConstantPools().entrySet()) {
                ConstantMap map = entryMap.getValue();
                if (constantPoolName.equals(map.getName())) {
                    for (Map.Entry<Long, Object> entry : map.entrySet()) {
                        System.out.printf("%s\n", entry.getValue());
                    }
                    return 0;
                }
            }
        }
        return -1;
    }
}

@CommandLine.Command(name = "flamegraph", description = "Generates collapsed/folded stacktraces to be able to transform with flamgraph.pl script")
class FlameGraph implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "JfrFileName", description = "JFR file")
    File jfrFile;

    @CommandLine.Option(names = {"-e", "--events"}, paramLabel = "EVENT", description = "event names for filtering on for stacktraces")
    String[] eventNames;

    @Override
    public Integer call() throws Exception {
        IMemberAccessor<IMCStackTrace, IItem> ACCESSOR_STACKTRACE = accessor(EVENT_STACKTRACE);
        IItemCollection events = JfrLoaderToolkit.loadEvents(jfrFile);
        if (eventNames == null) {
            eventNames = new String[] { "jdk.ExecutionSample" };
        }
        IItemCollection execSamples;
        if ("all".equals(eventNames[0])) {
            execSamples = events;
        } else {
            IItemFilter types = ItemFilters.type(eventNames);
            execSamples = events.apply(types);
        }
        if (!execSamples.hasItems()) {
            System.out.println("No events found for " + Arrays.toString(eventNames));
            return -1;
        }
        for (IItemIterable chunk : execSamples) {
            for (IItem sample : chunk) {
                IMCStackTrace stackTrace = ACCESSOR_STACKTRACE.getMember(sample);
                if (stackTrace == null) {
                    continue;
                }
                List<? extends IMCFrame> frames = stackTrace.getFrames();
                List<String> list = new ArrayList<>();
                for (IMCFrame frame : frames) {
                    String fullClassName = "UnknownType";
                    String methodName = "unknown";
                    IMCMethod method = frame.getMethod();
                    if (method != null) {
                        IMCType type = method.getType();
                        if (type != null) {
                            String fullName = type.getFullName();
                            if (fullName != null) {
                                fullClassName = fullName;
                            }
                        }
                        String tmpMethodName = method.getMethodName();
                        if (tmpMethodName != null) {
                            methodName =tmpMethodName;
                        }
                    }
                    list.add(fullClassName + "." + methodName);
                }
                Collections.reverse(list);
                System.out.println(String.join(";", list) + " 1");
            }
        }
        return 0;
    }
}

@CommandLine.Command(name = "gc", description = "Dumps GC information")
class GC implements Callable<Integer> {

    public static final String GC_G1_HEAP_SUMMARY = "jdk.G1HeapSummary";
    @CommandLine.Parameters(paramLabel = "JfrFileName", description = "JFR file")
    File jfrFile;

    @CommandLine.Option(names = {"-o", "--output"}, required = true, paramLabel = "FORMAT", description = "output format")
    String format;

    @Override
    public Integer call() throws Exception {
        IItemCollection events = JfrLoaderToolkit.loadEvents(jfrFile);
        IItemCollection gcConfigEvents = events.apply(JdkFilters.GC_CONFIG);
        if (!gcConfigEvents.hasItems()) {
            System.out.println("No GC config events");
            return -1;
        }
        IItemIterable chunk = gcConfigEvents.iterator().next();
        IItem config = chunk.iterator().next();
        IMemberAccessor<String, IItem> ACCESSOR_YOUNG = accessor(JdkAttributes.YOUNG_COLLECTOR);
        IMemberAccessor<String, IItem> ACCESSOR_OLD = accessor(JdkAttributes.OLD_COLLECTOR);
        String youngGCName = ACCESSOR_YOUNG.getMember(config);
        String oldGCName = ACCESSOR_OLD.getMember(config);
        System.out.printf("young: %s, old: %s\n", youngGCName, oldGCName);
        if (youngGCName.equals("G1New")) {
            return g1(events);
        }
        return null;
    }

    private int g1(IItemCollection events) {
        IItemFilter filter = ItemFilters.or(
                ItemFilters.type(JdkTypeIDs.GC_COLLECTOR_G1_GARBAGE_COLLECTION),
                ItemFilters.type(GC_G1_HEAP_SUMMARY),
                ItemFilters.type(JdkTypeIDs.GARBAGE_COLLECTION),
                ItemFilters.type(JdkTypeIDs.HEAP_SUMMARY)
                );
        IItemCollection g1events = events.apply(filter);
        if (!g1events.hasItems()) {
            System.out.println("No G1 GC events");
            return -1;
        }
        // G1GarbageCollection event

        IMemberAccessor<IQuantity, IItem> ACCESSOR_GCID = accessor(JdkAttributes.GC_ID);
        IMemberAccessor<String, IItem> ACCESSOR_TYPE = accessor(attr("type", "ATTR_GC_TYPE", "gc type", UnitLookup.PLAIN_TEXT));
        IMemberAccessor<IQuantity, IItem> ACCESSOR_START_TIME = accessor(JfrAttributes.START_TIME);
        IMemberAccessor<IQuantity, IItem> ACCESSOR_DURATION = accessor(JfrAttributes.DURATION);
        IMemberAccessor<String, IItem> ACCESSOR_CAUSE = accessor(JdkAttributes.GC_CAUSE);
        // G1HeapSummary
        IMemberAccessor<String, IItem> ACCESSOR_WHEN = accessor(JdkAttributes.GC_WHEN);
        IMemberAccessor<IQuantity, IItem> ACCESSOR_EDEN_USED = accessor(attr("edenUsedSize", "ATTR_EDEN_USED", "eden used", UnitLookup.MEMORY));
        IMemberAccessor<IQuantity, IItem> ACCESSOR_SURVIVOR_USED = accessor(attr("survivorUsedSize", "ATTR_SURVIVOR_USED", "survivor used", UnitLookup.MEMORY));
        IMemberAccessor<IQuantity, IItem> ACCESSOR_HEAP_USED = accessor(JdkAttributes.HEAP_USED);
        IMemberAccessor<IQuantity, IItem> ACCESSOR_HEAP_COMMITTED = accessor(JdkAttributes.GC_HEAPSPACE_COMMITTED);

        Map<Long, GCInfo> gcInfos = new HashMap<>();
        for (IItemIterable chunk : g1events) {
            for (IItem event : chunk) {
                long gcId = ACCESSOR_GCID.getMember(event).longValue();
                GCInfo gcInfo = gcInfos.computeIfAbsent(gcId, GCInfo::new);
                String eventType = event.getType().getIdentifier();
                gcInfo.startTime = ACCESSOR_START_TIME.getMember(event);
                if (GC_G1_HEAP_SUMMARY.equals(eventType)) {
                    String when = ACCESSOR_WHEN.getMember(event);
                    if ("Before GC".equals(when)) {
                        gcInfo.edenBefore = ACCESSOR_EDEN_USED.getMember(event);
                        gcInfo.survivorBefore = ACCESSOR_SURVIVOR_USED.getMember(event);
                    } else {
                        gcInfo.edenAfter = ACCESSOR_EDEN_USED.getMember(event);
                        gcInfo.survivorAfter = ACCESSOR_SURVIVOR_USED.getMember(event);
                    }
                } else if (JdkTypeIDs.HEAP_SUMMARY.equals(eventType)) {
                    String when = ACCESSOR_WHEN.getMember(event);
                    if ("Before GC".equals(when)) {
                        gcInfo.heapBefore = ACCESSOR_HEAP_USED.getMember(event);
                        gcInfo.heapCommittedBefore = ACCESSOR_HEAP_COMMITTED.getMember(event);
                    } else {
                        gcInfo.heapAfter = ACCESSOR_HEAP_USED.getMember(event);
                        gcInfo.heapCommittedAfter = ACCESSOR_HEAP_COMMITTED.getMember(event);
                    }
                } else if (JdkTypeIDs.GARBAGE_COLLECTION.equals(eventType)) {
                    gcInfo.duration = ACCESSOR_DURATION.getMember(event);
                    gcInfo.cause = ACCESSOR_CAUSE.getMember(event);
                } else if (JdkTypeIDs.GC_COLLECTOR_G1_GARBAGE_COLLECTION.equals(eventType)) {
                    gcInfo.type = ACCESSOR_TYPE.getMember(event);
                }
            }
        }
        ArrayList<GCInfo> gcInfoList = new ArrayList<>(gcInfos.values());
        gcInfoList.sort(Comparator.comparingLong(gcInfo -> gcInfo.gcId));
        gcInfoList.forEach(GC::printGCDetails);
        return 0;
    }

    private static void printGC(GCInfo gcInfo) {
        // 2020-12-29T16:52:27.911-0100: [GC (Allocation Failure)  1037636K->324K(1164288K), 0.0003633 secs]
        // 2020-12-29T17:27:56.142-0100: [GC pause (G1 Evacuation Pause) (young) 153M->318K(256M), 0.0005993 secs]
        System.out.printf("%s: [GC (%s) %dK->%dK(%dK), %f secs]\n",
                Instant.ofEpochMilli(gcInfo.startTime.longValue() / 1000000).atZone(ZoneId.of("UTC")).toLocalDateTime(),
                gcInfo.cause,
                gcInfo.heapBefore.longValue() / 1024,
                gcInfo.heapAfter.longValue() / 1024,
                0,
                gcInfo.duration.doubleValueIn(UnitLookup.SECOND));
    }

    private static void printGCDetails(GCInfo gcInfo) {
        // 2020-12-29T17:25:34.319-0100: [GC (Allocation Failure) [PSYoungGen: 572928K->0K(547840K)] 573256K->328K(722944K), 0.0004012 secs] [Times: user=0.01 sys=0.00, real=0.01 secs]
        // 2020-12-29T17:32:25.327-0100: [GC pause (G1 Evacuation Pause) (young), 0.0013296 secs]
        // [Eden: 153.0M(153.0M)->0.0B(153.0M) Survivors: 0.0B->0.0B Heap: 153.3M(256.0M)->312.5K(256.0M)]
        // [Times: user=0.00 sys=0.00, real=0.00 secs]

        System.out.printf("%s: [GC(%d) (%s) (%s), %f secs] [Heap: %dK(%dK)->%dK(%dK)]\n",
                Instant.ofEpochMilli(gcInfo.startTime.longValue() / 1000000).atZone(ZoneId.of("UTC")).toLocalDateTime(),
                gcInfo.gcId,
                gcInfo.cause,
                gcInfo.type == null ? "Mixed" : gcInfo.type,
                gcInfo.duration.doubleValueIn(UnitLookup.SECOND),
                gcInfo.heapBefore.longValue() / 1024,
                gcInfo.heapCommittedBefore.longValue() / 1024,
                gcInfo.heapAfter.longValue() / 1024,
                gcInfo.heapCommittedAfter.longValue() / 1024
                );
    }

    static class GCInfo {
        final long gcId;
        IQuantity startTime;
        String type;
        String cause;
        IQuantity duration;
        IQuantity edenBefore;
        IQuantity edenAfter;
        IQuantity survivorBefore;
        IQuantity survivorAfter;
        IQuantity heapBefore;
        IQuantity heapCommittedBefore;
        IQuantity heapAfter;
        IQuantity heapCommittedAfter;

        public GCInfo(long gcId) {
            this.gcId = gcId;
        }

        @Override
        public String toString() {
            return "GCInfo{" +
                    "gcId='" + gcId + '\'' +
                    ", type='" + type + '\'' +
                    ", cause='" + cause + '\'' +
                    ", duration=" + duration.displayUsing(IDisplayable.AUTO) +
                    ", edenBefore=" + edenBefore.displayUsing(IDisplayable.AUTO) +
                    ", edenAfter=" + edenAfter.displayUsing(IDisplayable.AUTO) +
                    ", survivorBefore=" + survivorBefore.displayUsing(IDisplayable.AUTO) +
                    ", survivorAfter=" + survivorAfter.displayUsing(IDisplayable.AUTO) +
                    ", heapBefore=" + heapBefore.displayUsing(IDisplayable.AUTO) +
                    ", heapAfter=" + heapAfter.displayUsing(IDisplayable.AUTO) +
                    '}';
        }
    }
}

class PoolStats {
    Long id;
    ConstantMap map;
    int count;
    int distinctCount;
    long size;

    public PoolStats(Long id, ConstantMap map, int count, int distinctCount, long size) {
        this.id = id;
        this.map = map;
        this.count = count;
        this.distinctCount = distinctCount;
        this.size = size;
    }
}
