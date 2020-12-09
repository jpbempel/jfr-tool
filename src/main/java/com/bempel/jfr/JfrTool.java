package com.bempel.jfr;

import static org.openjdk.jmc.common.item.ItemToolkit.accessor;
import static org.openjdk.jmc.flightrecorder.JfrAttributes.EVENT_STACKTRACE;

import com.bempel.jfr.jdk.ConstantMap;
import com.bempel.jfr.jdk.LongMap;
import com.bempel.jfr.jdk.RecordedClass;
import com.bempel.jfr.jdk.RecordingFile;
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
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;

@CommandLine.Command(subcommands = {Stats.class, Dump.class, FlameGraph.class},
        mixinStandardHelpOptions = true, version = "1.0")
public class JfrTool {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JfrTool()).execute(args);
        System.exit(exitCode);
    }
}

@CommandLine.Command(name = "stats")
class Stats implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "JfrFile", description = "JFR file")
    String jfrFileName;

    @Override
    public Integer call() throws Exception {
        RecordingFile recordingFile = new RecordingFile(Paths.get(jfrFileName));
        LongMap<ConstantMap> constantPools = recordingFile.getConstantPools();
        TreeMap<Integer, PoolStats> sortedMap = new TreeMap<>();
        for (Map.Entry<Long, ConstantMap> entry : constantPools.entrySet()) {
            ConstantMap constantMap = entry.getValue();
            int count = constantMap.size();
            long totalSize = 0;
            Set<Object> duplicates = new HashSet<>();
            for (Map.Entry<Long, Object> e : constantMap.entrySet()) {
                Object o = e.getValue();
                duplicates.add(o);
                if (o instanceof String) {
                    totalSize += ((String)o).length() + 1; // *2 ?
                } else if (o instanceof RecordedClass){
                    totalSize += 4;
                }
            }
            sortedMap.put(count, new PoolStats(entry.getKey(), entry.getValue(), count, duplicates.size(), totalSize));
        }
        System.out.println("Constant pool name count distinct");
        for (Map.Entry<Integer, PoolStats> entry : sortedMap.descendingMap().entrySet()) {
            PoolStats stats = entry.getValue();
            String distinctStr = "";
            if (stats.count != stats.distinctCount) {
                distinctStr = String.valueOf(stats.distinctCount);
            }
            System.out.printf("%s %,d %s\n", stats.map.getName(), stats.count, distinctStr);
        }
        return 0;
    }
}

@CommandLine.Command(name = "dump")
class Dump implements Callable<Integer> {
    @CommandLine.Parameters(paramLabel = "constantPoolName", description = "Constant pool name to dump")
    String constantPoolName;
    @CommandLine.Parameters(paramLabel = "JfrFileName", description = "JFR file")
    String jfrFileName;

    @Override
    public Integer call() throws Exception {
        RecordingFile recordingFile = new RecordingFile(Paths.get(jfrFileName));
        LongMap<ConstantMap> constantPools = recordingFile.getConstantPools();
        for (Map.Entry<Long, ConstantMap> entryMap : constantPools.entrySet()) {
            ConstantMap map = entryMap.getValue();
            if (constantPoolName.equals(map.getName())) {
                for (Map.Entry<Long, Object> entry : map.entrySet()) {
                    System.out.printf("%s\n", entry.getValue());
                }
                return 0;
            }
        }
        return -1;
    }
}

@CommandLine.Command(name = "flamegraph")
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
        IItemFilter types = ItemFilters.type(eventNames);
        IItemCollection execSamples = events.apply(types);
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
