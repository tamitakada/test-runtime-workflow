package parser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;


public class TestMethodData {
    private String name;

    private Instant start;
    private Instant end;

    double averageUserCpu;
    double averageSystemCpu;
    double averageCommittedHeap;
    double averageUsedHeap;
    int classesLoaded;
    int compiledMethods;
    HashMap<String, Integer> garbageCollections;

    long fileReadDuration = 0;
    long fileWriteDuration = 0;
    long socketReadDuration = 0;
    long socketWriteDuration = 0;

    double totalActiveThreads = 0;
    double activeDaemonThreads = 0;

    long totalThreadSleep = 0;

    long activeThreads = 0;

    public TestMethodData(String name, Instant start, Instant end) {
        this.name = name;
        this.start = start;
        this.end = end;

        averageUserCpu = 0;
        averageSystemCpu = 0;
        averageCommittedHeap = 0;
        averageUsedHeap = 0;
        classesLoaded = 0;
        compiledMethods = 0;
        garbageCollections = new HashMap<String, Integer>();
    }

    public String getName() { return name; }
    public Instant getStart() { return start; }
    public Instant getEnd() { return end; }
    public Duration getDuration() { return Duration.between(start, end); }

    public String toCsvString(Instant firstTestClassStart, int order) {
        String str = name + "," + order + "," + (Duration.between(firstTestClassStart, start).toMillis()) + "," + (getDuration().toMillis()) + "," + averageUserCpu + "," + averageSystemCpu + ","
            + averageCommittedHeap + "," + averageUsedHeap + "," + classesLoaded + "," + compiledMethods + "," + fileReadDuration + "," + fileWriteDuration + ","
            + socketReadDuration + "," + socketWriteDuration;

        ArrayList<String> sortedKeys = new ArrayList<>(garbageCollections.keySet());
        sortedKeys.sort(null);
        for (String k : sortedKeys) {
            str += "," + garbageCollections.get(k);
        }

        return str;
    }
}