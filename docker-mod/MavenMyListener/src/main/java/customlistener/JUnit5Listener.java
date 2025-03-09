package customlistener;

import customlistener.lightweightProfiler.LightweightTestInfos;
import customlistener.monitoring.*;
import customlistener.monitoring.data.ByteBuddyManager;
import customlistener.output.DataExporter;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class JUnit5Listener implements TestExecutionListener,Extension {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    public static final String DETAIL_LEVEL_PROPERTY = "detailLevel";
    public static final String DETAIL_LEVEL_ELABORATE = "elaborate";
    public static final String GATHER_ADVANCED_DETAILS_PROPERTY = "gatherAdvancedDetails";
    public static final String ATTACH_BYTE_BUDDY= "attachByteBuddy";
    public static String[] BYTE_BUDDY_ARGS;

    private boolean gatherAdvancedDetails = false;
    private Map<String, TestInfo> testInfoMap = new LinkedHashMap<>();
    private String absoluteLogFilePath;
    private String csvLogFilePath;
    private String methodDataFilePath;
    private boolean buildFailed = false;

    private final ExecutorService singleThreadExecutor;
    private final MonitoringUtil monitoringUtil;
    private final LightweightTestInfos lightweightTestInfos;

    private boolean collectingMethodData = false;
    private boolean onlyMethodMonitoring = false;

    public JUnit5Listener() {
        System.out.println("JUnit5Listener constructor");

        System.out.println("Detail Level: " + System.getProperty(DETAIL_LEVEL_PROPERTY));
        System.out.println("Gather Advanced details: " + System.getProperty(GATHER_ADVANCED_DETAILS_PROPERTY));
        System.out.println("Byte Buddy arguments: " + System.getProperty(ATTACH_BYTE_BUDDY));

        this.monitoringUtil = new MonitoringUtil();

        detectOSAndLoadProperties();

        //System.out.println("HEYO");

        if(System.getProperty(GATHER_ADVANCED_DETAILS_PROPERTY) != null) {
            gatherAdvancedDetails = System.getProperty(GATHER_ADVANCED_DETAILS_PROPERTY).equals("true");
        }
        //System.out.println("HEYO1");

        if (gatherAdvancedDetails) {
            this.singleThreadExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("JUnit5Listener-SnapshotThread"));
            this.lightweightTestInfos = null;
        } else {
            this.lightweightTestInfos = new LightweightTestInfos();
            this.singleThreadExecutor = null;
        }
        //System.out.println("HEYO2");


        //attachShutdownHook();

    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        byteBuddySetup();
        this.onlyMethodMonitoring = onlyMethodMonitoring();
        System.out.println("Test Plan Execution Started");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        listAllThreads();
        dumpLogs();
//        if (buildFailed) {
//            dumpLogs();
//        } else {
//            System.out.println("All tests passed. Cleanup tasks are being executed.");
//        }
        if (gatherAdvancedDetails) {
            shutdownExecutor();
        }
        System.out.println("Test plan execution finished. Logs have been dumped.");
    }

    private void shutdownExecutor() {
        singleThreadExecutor.shutdown();
        try {
            if (!singleThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                singleThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            singleThreadExecutor.shutdownNow();
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!testIdentifier.isTest() || onlyMethodMonitoring) return;

        if (gatherAdvancedDetails) {
            // Byte Buddy profiling if enabled, attach only if "all" mode
            startTestSnapshot(testIdentifier);

        } else {
            lightweightTestInfos.setStartTime(getFullyQualifiedName(testIdentifier), System.currentTimeMillis());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!testIdentifier.isTest() || onlyMethodMonitoring) return;

        if (!gatherAdvancedDetails) {
            lightweightTestInfos.setEndTime(getFullyQualifiedName(testIdentifier), System.currentTimeMillis());
            if (testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
                lightweightTestInfos.setTestStatus(getFullyQualifiedName(testIdentifier), false);
            }
        } else {
            // Advanced profiling flow
            finishTestSnapshot(testIdentifier, testExecutionResult);
        }
    }

    private boolean onlyMethodMonitoring() {
        return BYTE_BUDDY_ARGS.length == 1 && BYTE_BUDDY_ARGS[0].equals("all");
    }

    private void startTestSnapshot(TestIdentifier testIdentifier) {
        String key = getFullyQualifiedName(testIdentifier);
        TestInfo testInfo = new TestInfo(key, System.currentTimeMillis());
        testInfoMap.put(key, testInfo);
        Thread thread = Thread.currentThread();
        runInSingleThreadExecutor(() -> testInfo.startSnapshot(monitoringUtil, thread));
    }

    private void finishTestSnapshot(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        String key = getFullyQualifiedName(testIdentifier);
        TestInfo testInfo = testInfoMap.get(key);
        if (testInfo != null) {
            testInfo.setEndTime(System.currentTimeMillis());
            if (testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
                testInfo.setFailed(true);
                buildFailed = true;
            }
            runInSingleThreadExecutor(() -> testInfo.endSnapshot(monitoringUtil));
        }
    }

    private void runInSingleThreadExecutor(Runnable task) {
        if (singleThreadExecutor != null) {
            singleThreadExecutor.submit(task);
        }
    }

    private void dumpLogs() {
        //System.out.println("U DUMP LOGSU");

        DataExporter.exportMethodData(methodDataFilePath);
        if(!onlyMethodMonitoring) {
            if(!gatherAdvancedDetails) {
                DataExporter.generateReport(absoluteLogFilePath, csvLogFilePath, lightweightTestInfos.convertFromLightweightToNormal());
            }else{
                DataExporter.generateReport(absoluteLogFilePath, csvLogFilePath, testInfoMap);
            }
        }
    }

    private String getFullyQualifiedName(TestIdentifier testIdentifier) {
        return testIdentifier.getParentId().orElse("") + "." + testIdentifier.getDisplayName();
    }

    private String initFilePath(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            path = file.getAbsolutePath();
        }
        File logDir = new File(file.getParent());
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        return path;
    }


    private void detectOSAndLoadProperties() {
        System.out.println("Detecting OS and loading properties");
        this.absoluteLogFilePath = initFilePath("/home/listener/output/logs/plainText/test-results.log");
        this.csvLogFilePath = initFilePath("/home/listener/output/logs/csv/test-results.csv");
        this.methodDataFilePath = initFilePath("/home/listener/output/logs/csv/method-data.csv");
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String excludedThreadName;

        NamedThreadFactory(String excludedThreadName) {
            this.excludedThreadName = excludedThreadName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(excludedThreadName);
            return t;
        }
    }

    private void byteBuddySetup() {
        if (System.getProperty(ATTACH_BYTE_BUDDY) != null) {
            //System.out.println("TU SMO");
            BYTE_BUDDY_ARGS = System.getProperty(ATTACH_BYTE_BUDDY).split(",");
            ByteBuddyManager.attachByteBuddy(); // Attach Byte Buddy based on args
        }else{
            BYTE_BUDDY_ARGS = new String[0];
        }
    }

    private void listAllThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            System.out.println("Thread: " + t.getName() + " | Daemon: " + t.isDaemon() + " | State: " + t.getState());
        }
    }

    private void attachShutdownHook() {
        System.out.println("IN SHUTDOWN HOOK");
        Runtime.getRuntime().addShutdownHook(new Thread(this::dumpLogs));

    }
}