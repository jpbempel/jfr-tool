# JFR tool

Tool for inspecting JDK Flight Recorder files

## Commands
* stats
* dump
* flamegraph

### stats
Displays statistics about constant pools
```
$ java -jar jfr-tool.jar stats recording.jfr
Constant pool name size(B) count distinct
jdk.types.StackTrace 42,920,257 56,289
jdk.types.Symbol 1,056,435 22,947
jdk.types.Method 273,319 16,282
java.lang.Class 69,127 6,927
java.lang.Thread 168,768 4,532
jdk.types.Package 26,089 3,325
jdk.types.VMOperationType 6,540 336
jdk.types.Module 3,528 264
jdk.types.ThreadGroup 67,119 176
jdk.types.GCCause 3,232 144
jdk.types.ClassLoader 532 125
jdk.types.CompilerPhaseType 2,460 120
jdk.types.MetaspaceObjectType 768 56
jdk.types.GCName 588 52
jdk.types.G1HeapRegionType 444 36
jdk.types.ThreadState 768 36
jdk.types.FlagValueOrigin 540 36
jdk.types.InflateCause 484 28
jdk.types.NetworkInterfaceName 316 28
jdk.types.ReferenceType 428 24
jdk.types.CodeBlobType 440 20 16
jdk.types.G1YCType 184 16
jdk.types.FrameType 192 16
jdk.types.NarrowOopMode 256 16
jdk.types.GCThresholdUpdater 164 8
jdk.types.MetadataType 76 8
jdk.types.GCWhen 92 8
java.lang.String 187 4
jdk.types.OldObjectArray 0 0
jdk.types.OldObjectRootSystem 0 0
jdk.types.OldObjectRootType 0 0
jdk.types.Reference 0 0
jdk.types.ZStatisticsCounterType 0 0
jdk.types.ZStatisticsSamplerType 0 0
jdk.types.OldObjectGcRoot 0 0
jdk.types.OldObjectField 0 0
jdk.types.ShenandoahHeapRegionState 0 0
jdk.types.OldObject 0 0
Total pools size: 44,603,333
```

### dump
Dumps data from a constant pool

```
$ java -jar jfr-tool.jar dump jdk.types.Symbol recording.jfr
search
linkFirst
checkElementIndex
(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipFile$ZipFileInputStream;Ljava/util/zip/ZipFile$CleanableResource;I)V
(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipFile$ZipFileInputStream;Ljava/util/zip/ZipFile$CleanableResource;Ljava/util/zip/Inflater;I)V
(Ljava/io/InputStream;Ljava/util/zip/Inflater;I)V
(Ljava/lang/String;)Ljava/util/LinkedList;
guard_LLL_Z
(Ljava/lang/invoke/VarHandle;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/invoke/VarHandle$AccessDescriptor;)Z
guard_L_I
(Ljava/lang/invoke/VarHandle;Ljava/lang/Object;Ljava/lang/invoke/VarHandle$AccessDescriptor;)I
guard_LJJ_Z
(Ljava/lang/invoke/VarHandle;Ljava/lang/Object;JJLjava/lang/invoke/VarHandle$AccessDescriptor;)Z
[...]
```

### flamegraph
Generates collapsed/folded stacktraces to be able to transform with flamgraph.pl script

```
$ java -jar jfr-tool.jar flamegraph --events=jdk.ExecutionSample,jdk.AllocationInNewTLAB recording.jfr
```

### gc
Dumps GC information
```
$ java -jar jfr-tool.jar gc -o log recording.jfr
```
