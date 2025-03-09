package customlistener;

import customlistener.lightweightProfiler.LightweightTestInfos;
import customlistener.monitoring.MonitoringUtil;
import customlistener.monitoring.data.ByteBuddyManager;
import customlistener.output.DataExporter;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class JUnit4Listener extends RunListener {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    public static final String DETAIL_LEVEL_PROPERTY = "detailLevel";
    public static final String GATHER_ADVANCED_DETAILS_PROPERTY = "gatherAdvancedDetails";
    public static final String ATTACH_BYTE_BUDDY = "attachByteBuddy";
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

    public JUnit4Listener() {
        System.out.println("JUnit4Listener constructor");

        System.out.println("Detail Level: " + System.getProperty(DETAIL_LEVEL_PROPERTY));
        System.out.println("Gather Advanced details: " + System.getProperty(GATHER_ADVANCED_DETAILS_PROPERTY));
        System.out.println("Byte Buddy arguments: " + System.getProperty(ATTACH_BYTE_BUDDY));

        this.monitoringUtil = new MonitoringUtil();

        detectOSAndLoadProperties();

        if (System.getProperty(GATHER_ADVANCED_DETAILS_PROPERTY) != null) {
            gatherAdvancedDetails = System.getProperty(GATHER_ADVANCED_DETAILS_PROPERTY).equals("true");
        }

        if (gatherAdvancedDetails) {
            this.singleThreadExecutor = Executors
                    .newSingleThreadExecutor(new NamedThreadFactory("JUnit4Listener-SnapshotThread"));
            this.lightweightTestInfos = null;
        } else {
            this.lightweightTestInfos = new LightweightTestInfos();
            this.singleThreadExecutor = null;
        }

        // attachShutdownHook();
    }

    @Override
    public void testRunStarted(Description description) {
        byteBuddySetup();
        this.onlyMethodMonitoring = onlyMethodMonitoring();
        System.out.println("Test Run Started");
    }

    @Override
    public void testRunFinished(Result result) {
        listAllThreads();
        dumpLogs();

        if (gatherAdvancedDetails) {
            shutdownExecutor();
        }
        System.out.println("Test run finished. Logs have been dumped.");
    }

    @Override
    public void testStarted(Description description) {
        if (onlyMethodMonitoring)
            return;

        if (gatherAdvancedDetails) {
            startTestSnapshot(description);
        } else {
            lightweightTestInfos.setStartTime(description.getDisplayName(), System.currentTimeMillis());
        }
    }

    @Override
    public void testFinished(Description description) {
        if (onlyMethodMonitoring)
            return;

        if (!gatherAdvancedDetails) {
            lightweightTestInfos.setEndTime(description.getDisplayName(), System.currentTimeMillis());
        } else {
            finishTestSnapshot(description, null); // JUnit 4 does not provide a direct test result here
        }
    }

    @Override
    public void testFailure(org.junit.runner.notification.Failure failure) {
        String testName = failure.getDescription().getDisplayName();
        if (gatherAdvancedDetails) {
            finishTestSnapshot(failure.getDescription(), failure);
        } else {
            lightweightTestInfos.setTestStatus(testName, false);
        }
        buildFailed = true;
    }

    private boolean onlyMethodMonitoring() {
        return BYTE_BUDDY_ARGS.length == 1 && BYTE_BUDDY_ARGS[0].equals("all");
    }

    private void startTestSnapshot(Description description) {
        String key = description.getDisplayName();
        TestInfo testInfo = new TestInfo(key, System.currentTimeMillis());
        testInfoMap.put(key, testInfo);
        Thread thread = Thread.currentThread();
        runInSingleThreadExecutor(() -> testInfo.startSnapshot(monitoringUtil, thread));
    }

    private void finishTestSnapshot(Description description, org.junit.runner.notification.Failure failure) {
        String key = description.getDisplayName();
        TestInfo testInfo = testInfoMap.get(key);
        if (testInfo != null) {
            testInfo.setEndTime(System.currentTimeMillis());
            if (failure != null) {
                testInfo.setFailed(true);
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
        DataExporter.exportMethodData(methodDataFilePath);
        if (!onlyMethodMonitoring) {
            if (!gatherAdvancedDetails) {
                DataExporter.generateReport(absoluteLogFilePath, csvLogFilePath,
                        lightweightTestInfos.convertFromLightweightToNormal());
            } else {
                DataExporter.generateReport(absoluteLogFilePath, csvLogFilePath, testInfoMap);
            }
        }
    }

    private void detectOSAndLoadProperties() {
        System.out.println("Detecting OS and loading properties");
        this.absoluteLogFilePath =
        initFilePath("/home/listener/output/logs/plainText/test-results.log");
        this.csvLogFilePath =
        initFilePath("/home/listener/output/logs/csv/test-results.csv");
        this.methodDataFilePath =
        initFilePath("/home/listener/output/logs/csv/method-data.csv");
    }

    private String initFilePath(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            path = file.getAbsolutePath();
        }
        File logDir = new File(file.getParent());
        if (!logDir.exists()) {
            logDir.mkdirs(); // Create directories if they don't exist
        }
        try {
            if (!file.exists()) {
                file.createNewFile(); // Create the file if it doesn't exist
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create file: " + path, e);
        }
        return path;
    }

    private void byteBuddySetup() {
        if (System.getProperty(ATTACH_BYTE_BUDDY) != null) {
            BYTE_BUDDY_ARGS = System.getProperty(ATTACH_BYTE_BUDDY).split(",");
            ByteBuddyManager.attachByteBuddy();
        } else {
            BYTE_BUDDY_ARGS = new String[0];
        }
    }

    private void listAllThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            System.out.println("Thread: " + t.getName() + " | Daemon: " + t.isDaemon() + " | State: " + t.getState());
        }
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

    private void attachShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::dumpLogs));
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
}
