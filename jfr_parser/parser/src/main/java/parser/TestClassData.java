package parser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;


public class TestClassData {
    private String name;

    private Instant start;
    private Instant end;

    private ArrayList<TestMethodData> methods;
    
    HashMap<String, Integer> nonTestMethodCounts;
    int stackTraceCount;

    double averageUserCpu;
    double averageSystemCpu;

    long averageCommittedHeap;
    long averageUsedHeap;
    double averageUsedHeapRatio;

    int classesLoaded;
    int compiledMethods;

    HashMap<String, Integer> garbageCollections;
    HashMap<String, Long> garbageCollectionPauseTimes;

    ArrayList<Integer> gcIds = new ArrayList<>();
    long totalGcPauseTime = 0;
    double gcPercent = 0;

    long fileReadDuration = 0;
    long fileWriteDuration = 0;
    long socketReadDuration = 0;
    long socketWriteDuration = 0;

    double totalActiveThreads;
    double activeDaemonThreads;

    long totalThreadSleep = 0;

    long activeThreads = 0;

    public TestClassData(String name, Instant start) {
        this.name = name;
        this.start = start;
        end = start;
        methods = new ArrayList<TestMethodData>();
        nonTestMethodCounts = new HashMap<>();

        averageUserCpu = 0;
        averageSystemCpu = 0;
        averageCommittedHeap = 0;
        averageUsedHeap = 0;
        averageUsedHeapRatio = 0;
        classesLoaded = 0;
        compiledMethods = 0;
        stackTraceCount = 0;
        garbageCollections = new HashMap<String, Integer>();
        garbageCollectionPauseTimes = new HashMap<>();

        activeDaemonThreads = 0;
        totalActiveThreads = 0;
    }

    public String getName() { return name; }
    public Instant getStart() { return start; }
    public Instant getEnd() { return end; }
    public Duration getDuration() { return Duration.between(start, end); }
    public ArrayList<TestMethodData> getMethods() { return methods; }

    public void addTestMethod(TestMethodData method) {
        if (method.getEnd().compareTo(end) > 0) end = method.getEnd();

        for (int i = 0; i < methods.size(); i++) {
            if (method.getStart().compareTo(methods.get(i).getStart()) < 0) {
                methods.add(i, method);
                return;
            }
        }
        methods.add(method);
    }

    public String toCsvString(Instant firstTestClassStart, int order) {
        String str = name + "," + order + "," + (Duration.between(firstTestClassStart, start).toMillis()) + "," + (getDuration().toMillis()) + "," 
            + averageUserCpu + "," + averageSystemCpu + ","
            + averageCommittedHeap + "," + averageUsedHeap + "," + averageUsedHeapRatio + ","
            + classesLoaded + "," + compiledMethods + "," + fileReadDuration + "," + fileWriteDuration + ","
            + socketReadDuration + "," + socketWriteDuration + "," + totalActiveThreads + "," + activeDaemonThreads + "," + totalThreadSleep;

        // ArrayList<String> sortedKeys = new ArrayList<>(garbageCollections.keySet());
        // sortedKeys.sort(null);

        // for (String k : sortedKeys) {
        //     str += "," + garbageCollections.get(k) + "," + garbageCollectionPauseTimes.get(k);
        // }

        str += "," + gcIds.size() + "," + totalGcPauseTime + "," + gcPercent;

        return str;
    }

    public String toCsvStringOnlyStats() {
        return (getDuration().toMillis()) + "," 
            + averageUserCpu + "," + averageSystemCpu + ","
            + averageCommittedHeap + "," + averageUsedHeap + "," + averageUsedHeapRatio + ","
            + classesLoaded + "," + compiledMethods + "," + fileReadDuration + "," + fileWriteDuration + ","
            + socketReadDuration + "," + socketWriteDuration + "," + totalActiveThreads + "," + activeDaemonThreads + "," + totalThreadSleep
            + "," + gcIds.size() + "," + totalGcPauseTime + "," + gcPercent;
    }
}