package customlistener;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import customlistener.monitoring.MonitoringUtil;

import java.io.IOException;
import java.lang.management.*;
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static customlistener.JUnit5Listener.*;



@JsonSerialize(using = TestInfo.InfoSerializer.class)
public class TestInfo {
    private static final int MAX_NAME_LENGTH = 50;
    private final String name;
    private final long startTime;
    private long endTime;
    private boolean failed;


    private Severity severity = Severity.LOW;
    private long measurementCount;


    //cpu
    private double initialCpuLoad;
    private double finalCpuLoad;

    //timed cpu
    private double averageCpuLoad;

    private Timer cpuLoadTimer;

    //timed memory
    private long averageCommittedMemory;

    private long averageUsedMemory;

    //Compilation time
    private long initialCompilationTime;
    private long finalCompilationTime;

    //GC
    private long initialGarbageCollections;
    private long finalGarbageCollections;
    private long initialGarbageCollectionTime;
    private long finalGarbageCollectionTime;

    // G1 Specific
    private long initialG1FullCount;
    private long finalG1FullCount;
    private long initialG1NewCount;
    private long finalG1NewCount;
    private long initialG1OldCount;
    private long finalG1OldCount;

    private long initialG1FullTime;
    private long finalG1FullTime;
    private long initialG1NewTime;
    private long finalG1NewTime;
    private long initialG1OldTime;
    private long finalG1OldTime;

    //classloading
    private long initialLoadedClassCount;
    private long finalLoadedClassCount;
    private long totalClassLoadCountInitial;
    private long totalClassCountFinal;
    private long totalClassUnloadCountFinal;
    private long totalClassUnloadCountInitial;
    //thread
    private int initialThreadCount;
    private int finalThreadCount;
    private int peakThreadCount;
    private long initialThreadCpuTime;
    private long finalThreadCpuTime;
    private long testThreadId;

    public TestInfo(String name, long startTime) {
        this.name = name;
        this.startTime = startTime;
    }

    public TestInfo(String name, long startTime, long endTime, boolean status){
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.failed = status;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getFormattedStartTime() {
        return formatTime(startTime);
    }

    public String getFormattedEndTime() {
        return formatTime(endTime);
    }

    public Severity determineSeverity(long leakedObjects) {
        if (leakedObjects == 0) {
            return Severity.LOW;
        } else if (leakedObjects <= 10) {
            return Severity.MEDIUM;
        } else {
            return Severity.HIGH;
        }
    }
    public void startTimedMonitoring(long interval,com.sun.management.OperatingSystemMXBean osMXBean, MemoryMXBean memoryMXBean) {
        cpuLoadTimer = new Timer(true);
        updateAverages(osMXBean, memoryMXBean);
        cpuLoadTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateAverages(osMXBean, memoryMXBean);

            }
        }, 0, interval);
        updateAverages(osMXBean, memoryMXBean);
    }

    private void updateAverages(com.sun.management.OperatingSystemMXBean osMXBean, MemoryMXBean memoryMXBean){
        double currentCpuLoad = osMXBean.getProcessCpuLoad() * 100;
        long currentCommittedMemory = memoryMXBean.getHeapMemoryUsage().getCommitted();
        long currentUsedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        measurementCount++;
        updateAverageCpuLoad(currentCpuLoad);
        updateAverageCommittedMemory(currentCommittedMemory);
        updateAverageUsedMemory(currentUsedMemory);
    }
    // Incremental average for CPU load
    public void updateAverageCpuLoad(double currentCpuLoad) {
        averageCpuLoad += (currentCpuLoad - averageCpuLoad) / measurementCount;
    }

    // Incremental average for committed memory
    public void updateAverageCommittedMemory(long currentCommittedMemory) {
        averageCommittedMemory += (currentCommittedMemory - averageCommittedMemory) / measurementCount;
    }

    // Incremental average for used memory
    public void updateAverageUsedMemory(long currentUsedMemory) {
        averageUsedMemory += (currentUsedMemory - averageUsedMemory) / measurementCount;
    }

    public void stopTimedMonitoring() {
        if (cpuLoadTimer != null) {
            cpuLoadTimer.cancel();
        }
    }

    public void startSnapshot(MonitoringUtil resources, Thread testThread) {
        //Beans
        this.testThreadId = testThread.getId();
        MemoryMXBean memoryMXBean = resources.getMemoryMXBean();
        List<GarbageCollectorMXBean> gcMXBeans = resources.getGcMXBeans();
        com.sun.management.OperatingSystemMXBean osMXBean = resources.getOsMXBean();
        ClassLoadingMXBean classLoadingMXBean = resources.getClassLoadingMXBean();
        ThreadMXBean threadMXBean = resources.getThreadMXBean();
        CompilationMXBean compilationMXBean = resources.getCompilationMXBean();
        int availableProcessors = resources.getAvailableProcessors();

        startTimedMonitoring(1, osMXBean, memoryMXBean);

        // GC
        this.initialGarbageCollections = 0;
        this.initialGarbageCollectionTime = 0;
        this.initialG1FullCount = 0;
        this.initialG1NewCount = 0;
        this.initialG1OldCount = 0;
        this.initialG1FullTime = 0;
        this.initialG1NewTime = 0;
        this.initialG1OldTime = 0;

        for (GarbageCollectorMXBean gcMXBean : gcMXBeans) {
            String gcName = gcMXBean.getName();
            long collectionCount = gcMXBean.getCollectionCount();
            long collectionTime = gcMXBean.getCollectionTime();

            this.initialGarbageCollections += collectionCount;
            this.initialGarbageCollectionTime += collectionTime;

            if (gcName.contains("G1")) {
                if(gcName.equals("G1 Old Generation")) {
                    this.initialG1OldCount = collectionCount;
                    this.initialG1OldTime = collectionTime;
                } else if (gcName.equals("G1 Young Generation")) {
                    this.initialG1NewCount = collectionCount;
                    this.initialG1NewTime = collectionTime;
                } else if (gcName.equals("G1 Full GC")) {
                    this.initialG1FullCount = collectionCount;
                    this.initialG1FullTime = collectionTime;
                }
            }
        }

        //Classloading
        this.initialLoadedClassCount = classLoadingMXBean.getLoadedClassCount();
        this.totalClassLoadCountInitial = classLoadingMXBean.getTotalLoadedClassCount();

        //Thread
        this.initialThreadCount = threadMXBean.getThreadCount();
        this.peakThreadCount = threadMXBean.getPeakThreadCount();
        this.initialThreadCpuTime = threadMXBean.getThreadCpuTime(testThreadId);

        //Compilation
        this.initialCompilationTime = compilationMXBean.getTotalCompilationTime();
    }

    public void endSnapshot(MonitoringUtil resources) {
        //Beans
        MemoryMXBean memoryMXBean = resources.getMemoryMXBean();
        List<GarbageCollectorMXBean> gcMXBeans = resources.getGcMXBeans();
        com.sun.management.OperatingSystemMXBean osMXBean = resources.getOsMXBean();
        ClassLoadingMXBean classLoadingMXBean = resources.getClassLoadingMXBean();
        ThreadMXBean threadMXBean = resources.getThreadMXBean();
        CompilationMXBean compilationMXBean = resources.getCompilationMXBean();
        int availableProcessors = resources.getAvailableProcessors();

        stopTimedMonitoring();

        // GC
        this.finalGarbageCollections = 0;
        this.finalGarbageCollectionTime = 0;
        this.finalG1FullCount = 0;
        this.finalG1NewCount = 0;
        this.finalG1OldCount = 0;
        this.finalG1FullTime = 0;
        this.finalG1NewTime = 0;
        this.finalG1OldTime = 0;

        for (GarbageCollectorMXBean gcMXBean : gcMXBeans) {
            String gcName = gcMXBean.getName();
            long collectionCount = gcMXBean.getCollectionCount();
            long collectionTime = gcMXBean.getCollectionTime();

            this.finalGarbageCollections += collectionCount;
            this.finalGarbageCollectionTime += collectionTime;

            if (gcName.contains("G1")) {
                if(gcName.equals("G1 Old Generation")) {
                    this.finalG1OldCount = collectionCount;
                    this.finalG1OldTime = collectionTime;
                } else if (gcName.equals("G1 Young Generation")) {
                    this.finalG1NewCount = collectionCount;
                    this.finalG1NewTime = collectionTime;
                } else if (gcName.equals("G1 Full GC")) {
                    this.finalG1FullCount = collectionCount;
                    this.finalG1FullTime = collectionTime;
                }
            }
        }

        //Classloading
        this.finalLoadedClassCount = classLoadingMXBean.getLoadedClassCount();
        this.totalClassCountFinal = classLoadingMXBean.getTotalLoadedClassCount();

        //Thread
        this.finalThreadCount = threadMXBean.getThreadCount();
        this.finalThreadCpuTime = threadMXBean.getThreadCpuTime(testThreadId);

        //Compilation
        this.finalCompilationTime = compilationMXBean.getTotalCompilationTime();
    }


    @Override
    public String toString() {
        return  "\"" + name.replace("\"", "\"\"") + "\"," +
                getFormattedStartTime() + "," +
                getFormattedEndTime() + "," +
                failed + "," +
                severity + "," +
                //cpu
                averageCpuLoad + "," +
                //memory
                averageCommittedMemory + "," +
                averageUsedMemory + "," +
                //classloading
                (totalClassCountFinal - totalClassLoadCountInitial) + "," +
                (totalClassUnloadCountFinal - totalClassUnloadCountInitial) + "," +
                //compiled methods
                (finalCompilationTime - initialCompilationTime) + "," +
                //GC
                (finalGarbageCollections - initialGarbageCollections) + "," +
                (finalGarbageCollectionTime - initialGarbageCollectionTime)  + "," +
                (finalG1FullCount - initialG1FullCount) + "," +
                (finalG1NewCount - initialG1NewCount) + "," +
                (finalG1OldCount - initialG1OldCount) + "," +

                //Thread
                (finalThreadCount - initialThreadCount) + "," +
                peakThreadCount + "," +
                (finalThreadCpuTime - initialThreadCpuTime) + ",";
    }
    public static String formatName(String name) {
        String detailLevel = System.getProperty(DETAIL_LEVEL_PROPERTY, "");
        if (!DETAIL_LEVEL_ELABORATE.equals(detailLevel) && name.length() > MAX_NAME_LENGTH){
            return "..." + name.substring(name.length() - MAX_NAME_LENGTH);
        }
        return name;
    }

    private static String formatTime(long timeInMillis) {
        return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(timeInMillis));
    }

    public static class InfoSerializer extends JsonSerializer<TestInfo> {
        @Override
        public void serialize(TestInfo testInfo, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("name", testInfo.name);
            gen.writeStringField("startTime", testInfo.getFormattedStartTime());
            gen.writeStringField("endTime", testInfo.getFormattedEndTime());
            gen.writeBooleanField("failed", testInfo.failed);
            gen.writeStringField("severity", testInfo.severity.toString());

            //cpu
            gen.writeNumberField("cpuLoad", testInfo.averageCpuLoad);

            //memory
            gen.writeNumberField("Avg committed heap", testInfo.averageCommittedMemory);
            gen.writeNumberField("Avg used heap", testInfo.averageUsedMemory);

            //classloading
            gen.writeNumberField("classCountChange", (testInfo.totalClassCountFinal - testInfo.totalClassLoadCountInitial));
            gen.writeNumberField("classUnloadCountChange", (testInfo.totalClassUnloadCountFinal - testInfo.totalClassUnloadCountInitial));

            //compiled methods
            gen.writeNumberField("Compilation time", (testInfo.finalCompilationTime - testInfo.initialCompilationTime));

            //GC
            gen.writeNumberField("garbageCollectionCountChange", (testInfo.finalGarbageCollections - testInfo.initialGarbageCollections));
            gen.writeNumberField("gcTimeChange", (testInfo.finalGarbageCollectionTime - testInfo.initialGarbageCollectionTime));
            gen.writeNumberField("gcG1Full", (testInfo.finalG1FullCount - testInfo.initialG1FullCount));
            gen.writeNumberField("gcG1New", (testInfo.finalG1NewCount - testInfo.initialG1NewCount));
            gen.writeNumberField("gcG1Old", (testInfo.finalG1OldCount - testInfo.initialG1OldCount));



            //thread
            gen.writeNumberField("threadCountChange", testInfo.finalThreadCount - testInfo.initialThreadCount);
            gen.writeNumberField("peakThreadCount", testInfo.peakThreadCount);
            gen.writeNumberField("threadCpuTimeChange", testInfo.finalThreadCpuTime - testInfo.initialThreadCpuTime);

            gen.writeEndObject();
        }
    }

    public String toJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }
}