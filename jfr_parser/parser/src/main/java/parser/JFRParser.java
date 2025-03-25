package parser;

import jdk.jfr.consumer.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.io.*;
import org.json.JSONObject;

import java.lang.IllegalArgumentException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;

import parser.Utils;
import parser.StatsTests;


public class JFRParser {

    static int max_cores = Runtime.getRuntime().availableProcessors();

    static boolean includeMethodData = false;
    static boolean debug = false;

    private static DecimalFormat df = new DecimalFormat("#.##");

    // dest directory names
    private static String destRoot;
    private static String[] destOrders;
    private static String[][] destTrials;
    
    // args[0] = JFR/listener file source dir path
    // args[1] = output dir path
    // Optional args[2] = test class to profile

    // order -> test class name -> attr, data
    private static HashMap<String, HashMap<String, HashMap<String, Double>>> data = new HashMap<>();


    public static void main(String[] args) throws IOException, InterruptedException {
        ArrayList<ArrayList<String[]>> filesToProcess = new ArrayList<>();

        File dir = new File(args[0]);
        destRoot = args[1];

        File[] sortedOrders = dir.listFiles();
        Arrays.sort(sortedOrders, (File f1, File f2) -> f1.getName().compareTo(f2.getName()));

        for (File orderDir : sortedOrders) {
            if (orderDir.isDirectory()) {
                filesToProcess.add(new ArrayList<String[]>());

                File[] allFiles = orderDir.listFiles();
                Arrays.sort(allFiles, (File f1, File f2) -> f1.getName().compareTo(f2.getName()));

                data.put(orderDir.getName(), new HashMap<>());

                for (File trialDir : allFiles) {
                    if (trialDir.isDirectory()) {
                        File jfrFile = Utils.findFileWith(trialDir, ".jfr");
                        File listenerFile = Utils.findFile(trialDir, "test-results.log");

                        if (jfrFile == null) {
                            throw new FileNotFoundException("Did not find JFR file in " + trialDir.getAbsolutePath());
                        } else if (listenerFile == null) {
                            throw new FileNotFoundException("Did not find listener file test-results.log in " + trialDir.getAbsolutePath());
                        }

                        File gcFile = Utils.findFile(trialDir, "gc.log");
                        filesToProcess.get(filesToProcess.size() - 1).add(new String[]{
                            orderDir.getName(),
                            trialDir.getName(),
                            jfrFile.getAbsolutePath(), 
                            listenerFile.getAbsolutePath(), 
                            gcFile == null ? "" : gcFile.getAbsolutePath()
                        });
                    }
                }
            }
        }

        destOrders = filesToProcess.stream().map((ords) -> ords.get(0)[0]).toArray(String[]::new);
        destTrials = filesToProcess.stream()
            .map((ords) -> ords.stream().map((t) -> t[1]).toArray(String[]::new))
            .toArray(String[][]::new);

        Thread[] threads = new Thread[max_cores];

        int baseLen = filesToProcess.size() / threads.length;
        int mod = filesToProcess.size() % threads.length;

        ArrayList<TestClassData> testClassesFirst = getTestTimes(filesToProcess.get(0).get(0)[3]);
        TestClassData[][][] allClassesByOrder = new TestClassData[filesToProcess.size()][filesToProcess.get(0).size()][testClassesFirst.size()];
        allClassesByOrder[0][0] = testClassesFirst.toArray(TestClassData[]::new);

        for (int i = 0; i < threads.length; i++) {
            int ordLen = baseLen + (i < mod ? 1 : 0);
            int ordStart = i * baseLen + (i < mod ? i : mod);
            threads[i] = new Thread(() -> {
                for (int ord = ordStart; ord < ordStart+ordLen; ord++) {
                    for (int trial = 0; trial < filesToProcess.get(ord).size(); trial++) {
                        System.out.println("started ord: " + ord + ", trial: " + trial);
                        try {
                            ArrayList<TestClassData> testClasses;
                            if (ord != 0 || trial != 0) {
                                testClasses = getTestTimes(filesToProcess.get(ord).get(trial)[3]);
                                allClassesByOrder[ord][trial] = testClasses.toArray(TestClassData[]::new);
                            }
                            else testClasses = testClassesFirst;
                            
                            writeTestSuiteSummary(
                                new RecordingFile(Path.of(filesToProcess.get(ord).get(trial)[2])),
                                filesToProcess.get(ord).get(trial)[4], 
                                testClasses,
                                Paths.get(destRoot, destOrders[ord], destTrials[ord][trial]).toString()
                            );
                        } catch (Exception e) {
                            System.out.println("Error: " + e);
                        }

                        System.out.println("finished ord: " + ord + ", trial: " + trial);
                    }

                    // String fullOrder = "";
                    // for (TestClassData tc : allClassesByOrder[ord][0]) {
                    //     fullOrder += tc.getName() + "#";
                    // }

                    // Arrays.stream(allClassesByOrder[ord][0])
                    //     .forEach((tc) -> fullOrder += );
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) t.join();

        for (int ord = 0; ord < allClassesByOrder.length; ord++) {
            Utils.writeResults(
                Paths.get(destRoot, destOrders[ord], "all").toString(), 
                "average.csv", 
                StatsProcessor.averageTestClassStats(data.get(destOrders[ord]), allClassesByOrder[ord])
            );
        }

        // HashMap<String, ArrayList<TestClassData>> byTestClass = Utils.mapTestClassData(allClassesByOrder);

        // Utils.writeResults(
        //     destRoot, 
        //     "average.csv", 
        //     StatsProcessor.averageTestClassStats(
        //         null, 
        //         Arrays.stream(allClassesByOrder).flatMap(Arrays::stream).toArray(TestClassData[][]::new)
        //     )
        // );
        // writeKTVals(allClassesByOrder, byTestClass, destRoot);
        // writeUTests(allClassesByOrder, destRoot);
        writeUTestsTestClasses(allClassesByOrder, destRoot);
    }

    // private static void writeTestClassData(TestClassData[][][] orders) {
    //     Arrays.stream(orders)
    //         .forEach((order) -> {
    //             String content = "Test class,Trial,Order,Average user CPU %,Average system CPU %,Average committed heap (B),Average used heap (B),Average used heap ratio,Classes loaded,Methods compiled,File read duration (ms),File write duration (ms),Socket read duration (ms),Socket write duration(ms),Active total thread count,Active daemon thread count,Thread sleep total,GC count,GC pause time,GC % of test time\n";
    //             int[] reorder = Utils.getTestClassOrder(baseOrder, order);
    //             for (int tr = 0; tr < order.length; tr++) {
    //                 for (int i : reorder) {
    //                     content += order[tr][i].getName() + "," + tr + "," + i + "," + order[tr][i].toCsvStringOnlyStats();
    //                 }
    //             }
    //         });


    //     for (int ord = 0; ord < orders.length; ord++) {



    //         Arrays.stream(orders[ord])
    //             .sorted((tc1, tc2) -> baseOrder.get(tc1.getName()).compareTo(tc2.getName()));
    //     }
    // }

    private static void writeKTVals(TestClassData[][][] orders, Map<String, ArrayList<TestClassData>> byTestClass, String dest) {
        String header = "Test class,Average user CPU %,Average system CPU %,Average committed heap (B),Average used heap (B),Average used heap ratio,Classes loaded,Methods compiled,File read duration (ms),File write duration (ms),Socket read duration (ms),Socket write duration(ms),Active total thread count,Active daemon thread count,Thread sleep total,GC count,GC pause time,GC % of test time\n";
        
        String byClassContent = header;
        Map<String, Double[]> ktByClass = StatsTests.getKTValsAcrossTestClasses(byTestClass);
        for (String tc : ktByClass.keySet()) {
            byClassContent += tc;
            for (Double coeffs : ktByClass.get(tc)) {
                byClassContent += "," + (Double.isNaN(coeffs) ? "-" : String.format("%.3f", coeffs));
            }
            byClassContent += "\n";
        }
        Utils.writeResults(dest, Path.of("all", "kt_by_class.csv").toString(), byClassContent);

        for (int i = 0; i < orders.length; i++) {
            String byOrderContent = "Trial,Average user CPU %,Average system CPU %,Average committed heap (B),Average used heap (B),Average used heap ratio,Classes loaded,Methods compiled,File read duration (ms),File write duration (ms),Socket read duration (ms),Socket write duration(ms),Active total thread count,Active daemon thread count,Thread sleep total,GC count,GC pause time,GC % of test time\n";
            for (int tr = 0; tr < orders[i].length; tr++) {
                byOrderContent += tr+1 + ",";
                double[] coeffs = StatsTests.getKTVals(orders[i][tr]);
                for (int j = 0; j < coeffs.length; j++)
                    byOrderContent += (Double.isNaN(coeffs[j]) ? "-" : String.format("%.3f", coeffs[j])) 
                        + (j < coeffs.length - 1 ? "," : "\n");
            }
            Utils.writeResults(dest, Path.of(destOrders[i], "all", "kt_by_order.csv").toString(), byOrderContent);
        }
    }

    private static void writeUTests(TestClassData[][][] dataByOrder, String dest) {
        String content = "Order 1,Order 2,Avg Order 1 Duration (s),Order 1 stddev,Avg Order 2 Duration (s),Order 2 stddev,Duration p-value";
        long[][] averageOrderDurations = Arrays.stream(dataByOrder)
            .map((order) -> StatsProcessor.getMeanAndDeviation(Arrays.stream(order)
                    .mapToLong((trial) -> StatsTests.testSuiteDuration(trial).toSeconds())
                    .toArray()))
            .toArray(long[][]::new);

        for (long[] x : averageOrderDurations) {
            System.out.println("Order len: " + x[0] + ", " + x[1]);
        }

        List<Double> pValues = StatsTests.uTestAllOrderPairs(dataByOrder);
        int pValIndex = 0;
        for (int i = 0; i < dataByOrder.length; i++) {
            for (int j = i+1; j < dataByOrder.length; j++) {
                content += "\n" + destOrders[i] + "," + destOrders[j] + ","
                    + averageOrderDurations[i][0] + "," + averageOrderDurations[i][1] + ","
                    + averageOrderDurations[j][0] + "," + averageOrderDurations[j][1] + ","
                    + pValues.get(pValIndex);
                pValIndex++;
            }
        }
        Utils.writeResults(Paths.get(dest, "all").toString(), "u_tests_full_test_suite.csv", content);
    }

    private static void writeUTestsTestClasses(TestClassData[][][] dataByOrder, String dest) {
        String countContent = "Order 1,Order 2,Duration,User CPU %,System CPU %,Committed heap,Used heap,Used heap ratio,Classes loaded,Methods compiled,File read duration,File write duration,Socket read duration,Socket write duration,Active threads,Active daemons,Thread sleep,GC count,GC pause time,GC % test time\n";
        String content = "Order 1,Order 2,Test Class,Duration,User CPU %,System CPU %,Committed heap,Used heap,Used heap ratio,Classes loaded,Methods compiled,File read duration,File write duration,Socket read duration,Socket write duration,Active threads,Active daemons,Thread sleep,GC count,GC pause time,GC % test time";
        String[] attrs ={"duration", "userCpu", "sysCpu", "committedHeap", "usedHeap", "usedHeapRatio", "classesLoaded", "compiledMethods", "fileRead", "fileWrite", "socketRead", "socketWrite", "activeThread", "activeDaemon", "threadSleep", "gcCounts", "gcPauseTime", "gcPausePercent"};
        String effectSizes = "Order 1,Order 2,Test Class,Duration,User CPU %,System CPU %,Committed heap,Used heap,Used heap ratio,Classes loaded,Methods compiled,File read duration,File write duration,Socket read duration,Socket write duration,Active threads,Active daemons,Thread sleep,GC count,GC pause time,GC % test time";
        for (int i = 0; i < dataByOrder.length; i++) {
            if (destOrders[i].equals("none")) {
                System.out.println("HERE!");
            }

            for (int j = i+1; j < dataByOrder.length; j++) {
                double[][][] pValues = StatsTests.uTestOrderPairTestClasses(dataByOrder[i], dataByOrder[j]);

                if (pValues.length > 0) {
                    try {
                        countContent += "" + destOrders[i] + "," + destOrders[j] + ",";
                        for (int k = 0; k < pValues[0].length; k++) {
                            int count = 0;
                            for (int l = 0; l < pValues.length; l++) {
                                if (pValues[l][k][0] < 0.05) count++;
                            }
                            countContent += count + " (" + String.format("%.3f", (double)count / pValues.length * 100) + "%)"
                                + (k < pValues[0].length - 1 ? "," : "");
                        }
                        countContent += "\n";
                        
                        double[] pValueSums = new double[pValues[0].length];
                        for (int k = 0; k < pValues.length; k++) {
                            content += "\n" + destOrders[i] + "," + destOrders[j] + "," + dataByOrder[i][0][k].getName();
                            effectSizes += "\n" + destOrders[i] + "," + destOrders[j] + "," + dataByOrder[i][0][k].getName();
                            for (int l = 0; l < pValues[k].length; l++) {
                                content += "," + pValues[k][l][0];
                                pValueSums[l] += pValues[k][l][0];
                            }

                            for (int l = 0; l < pValues[k].length; l++) {
                                if (pValues[k][l][0] < 0.05) {
                                    double o1Var = Math.pow(data.get(destOrders[i]).get(dataByOrder[i][0][k].getName()).get(attrs[l] + "Dev"), 2);
                                    double o2Var = Math.pow(data.get(destOrders[j]).get(dataByOrder[i][0][k].getName()).get(attrs[l] + "Dev"), 2);
                                    double pooledDev = Math.sqrt((o1Var * (destTrials[i].length - 1) + o2Var * (destTrials[j].length - 1)) / (destTrials[i].length + destTrials[j].length));
                                    double cohens = (data.get(destOrders[i]).get(dataByOrder[i][0][k].getName()).get(attrs[l] + "Mean") - data.get(destOrders[j]).get(dataByOrder[i][0][k].getName()).get(attrs[l] + "Mean")) / pooledDev;
                                    effectSizes += "," + String.format("%.2f", cohens);
                                }
                                else effectSizes += ", - ";
                            }
                        }
                    } catch (Exception e) {

                    }
                }
            }
        }
        Utils.writeResults(Paths.get(dest, "all").toString(), "u_test_counts.csv", countContent);
        Utils.writeResults(Paths.get(dest, "all").toString(), "u_tests_for_test_classes.csv", content);
        Utils.writeResults(Paths.get(dest, "all").toString(), "cohens_for_test_classes.csv", effectSizes);
    }

    private static void addTestData(ArrayList<TestClassData> testClasses, Instant start, Instant end, String className, String methodName) {
        if (testClasses.isEmpty() 
            || (!className.equals(testClasses.get(testClasses.size() - 1).getName())
                && !testClasses.stream().anyMatch((t) -> t.getName().equals(className)))) {
            testClasses.add(new TestClassData(className, start));
        }
        TestMethodData newMethod = new TestMethodData(methodName, start, end);
        testClasses.get(testClasses.size() - 1).addTestMethod(newMethod);
    }

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static String gcDurationRe = "\\[([^\\]]*)\\] GC\\(([0-9]+)\\) (.*) ([0-9|\\.]+)ms";
    private static Pattern gcDurationPattern = Pattern.compile(gcDurationRe, Pattern.DOTALL);
    private static String gcRe = "\\[([^\\]]*)\\] GC\\(([0-9]+)\\) (.*)";
    private static Pattern gcPattern = Pattern.compile(gcRe, Pattern.DOTALL);

    public static void getGcData(String path, ArrayList<TestClassData> testClasses) throws FileNotFoundException {
        long testSuiteGcPauseTime = 0;
        long testClassGcPauseTime = 0;
        Scanner scanner = new Scanner(new File(path));
        while (scanner.hasNextLine()) {
            String unparsedGcLine = scanner.nextLine();
            Matcher matcher = gcDurationPattern.matcher(unparsedGcLine);

            Instant gcStartTime = null;
            int gcId = 0;
            Duration gcPauseLen = Duration.ofMillis(0);

            if (matcher.matches()) {
                gcId = Integer.parseInt(matcher.group(2));
                gcStartTime = ZonedDateTime.parse(matcher.group(1), formatter).toInstant();
                if (!matcher.group(3).toLowerCase().contains("concurrent")) {
                    gcPauseLen = Duration.ofMillis((long)(Double.parseDouble(matcher.group(4))));
                    testSuiteGcPauseTime += gcPauseLen.toMillis();
                }
            } else {
                matcher = gcPattern.matcher(unparsedGcLine);
                if (matcher.matches()) {
                    gcId = Integer.parseInt(matcher.group(2));
                    gcStartTime = ZonedDateTime.parse(matcher.group(1), formatter).toInstant();
                }
            }

            if (gcStartTime != null) {
                for (TestClassData tc : testClasses) {
                    if (tc.getStart().compareTo(gcStartTime) <= 0 && tc.getEnd().compareTo(gcStartTime) > 0) {
                        if (tc.getEnd().compareTo(gcStartTime.plus(gcPauseLen)) < 0) System.out.println("GC ID " + gcId + " falls between test classes");
                        else {
                            if (!tc.gcIds.contains(gcId)) tc.gcIds.add(gcId);
                            testClassGcPauseTime += gcPauseLen.toMillis();
                            tc.totalGcPauseTime += gcPauseLen.toMillis();
                            tc.gcPercent = (double) tc.totalGcPauseTime / tc.getDuration().toMillis() * 100;
                        }
                    }
                }
            }
        }
        System.out.println("Total test class GC pause time (ms): " + testClassGcPauseTime);
        System.out.println("Total test suite GC pause time (ms): " + testSuiteGcPauseTime);
    }

    public static ArrayList<TestClassData> getTestTimes(String path) throws FileNotFoundException, IllegalArgumentException {
        ArrayList<TestClassData> testClasses = new ArrayList<TestClassData>();
         
        Scanner scanner = new Scanner(new File(path));
        scanner.nextLine(); // Skip headers

        while (scanner.hasNextLine()) {
            JSONObject testJson = new JSONObject(scanner.nextLine());

            String timePattern = "yyyy-MM-dd HH:mm:ss.SSS";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timePattern);

            LocalDateTime startOffset = LocalDateTime.parse(testJson.getString("startTime"), formatter);
            Instant start = startOffset.toInstant(ZoneOffset.UTC);

            LocalDateTime endOffset = LocalDateTime.parse(testJson.getString("endTime"), formatter);
            Instant end = endOffset.toInstant(ZoneOffset.UTC);

            String unparsedName = testJson.getString("name");

            String parameterizedRe = "\\[engine:[^\\]]*\\]/\\[[^:]*:([^\\]]*)\\]/\\[[^:]*:([^\\]\\(]*)\\([^\\]]*\\)\\].\\[[0-9]+\\] (.+)";
            Pattern parameterized = Pattern.compile(parameterizedRe, Pattern.DOTALL);
            
            String general3PartRe = "\\[engine:[^\\]]*\\]/\\[[^:]*:([^\\]]*)\\]/\\[[^:]*:[^\\]]*\\]\\.(.*)";
            Pattern general3Part = Pattern.compile(general3PartRe, Pattern.DOTALL);
            
            String general2PartRe = "\\[engine:[^\\]]*\\]/\\[[^:]*:([^\\]]*)\\].(.+)";
            Pattern general2Part = Pattern.compile(general2PartRe, Pattern.DOTALL);
            
            Matcher nameMatcher = parameterized.matcher(unparsedName);
            if (nameMatcher.matches()) {
                addTestData(
                    testClasses, 
                    start, end,
                    nameMatcher.group(1), 
                    nameMatcher.group(2) + "(" + nameMatcher.group(3) + ")"
                );
            } else {
                nameMatcher = general3Part.matcher(unparsedName);
                if (nameMatcher.matches()) {
                    addTestData(
                        testClasses, 
                        start, end,
                        nameMatcher.group(1), 
                        nameMatcher.group(2)
                    );
                } else {
                    nameMatcher = general2Part.matcher(unparsedName);
                    if (nameMatcher.matches()) {
                        addTestData(
                            testClasses, 
                            start, end,
                            nameMatcher.group(1), 
                            nameMatcher.group(2)
                        );
                    }
                    else 
                        throw new IllegalArgumentException("Unexpected format in listener data: " + unparsedName);
                }
            }
        }

        scanner.close();

        // Ensure classes are sorted by start time
        testClasses.sort((c1, c2) -> c1.getStart().compareTo(c1.getStart()));

        return testClasses;
    }

    private static ArrayList<RecordedEvent> getSortedAndFilteredEvents(HashMap<String, ArrayList<RecordedEvent>> eventData, String key) {
        return eventData.get(key).stream()
            .filter((e) -> e.hasField("startTime") && e.getInstant("startTime") != null)
            .sorted((e1, e2) -> e1.getInstant("startTime").compareTo(e2.getInstant("startTime")))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static ArrayList<Integer> findStopsBetween(Instant start, Instant end, ArrayList<RecordedEvent> events) {
        ArrayList<Integer> stops = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            Instant eStart = events.get(i).getInstant("startTime");
            if (start.compareTo(eStart) <= 0 && end.compareTo(eStart) >= 0) stops.add(i);
        }
        return stops;
    }

    private static int[] findClosestStops(Instant to, ArrayList<RecordedEvent> events) {
        for (int i = 0; i < events.size(); i++) {
            Instant eStart = events.get(i).getInstant("startTime");   
            if (to.compareTo(eStart) >= 0) { // If time is on or after this event's start...
                if (i == events.size() - 1) return new int[]{i}; // If this is the last event, last event is the closest stop
                else {
                    Instant nextStart = events.get(i + 1).getInstant("startTime");
                    // If time is between events i and i + 1, those are the stops that bound it
                    if (to.compareTo(nextStart) <= 0) return new int[]{i, i + 1};
                }
            }
            else return new int[]{0}; // If time is before first event, first event is the closest stop
        }
        throw new IllegalArgumentException("No events");
    }

    private static int countEventsBetween(Instant start, Instant end, ArrayList<RecordedEvent> events) {
        int total = 0;
        for (int i = 0; i < events.size(); i++) {
            Instant eStart = events.get(i).getInstant("startTime");
            if (start.compareTo(eStart) <= 0 && end.compareTo(eStart) >= 0) total++;
        }
        return total;
    }

    private static long getTotalDurationBetween(Instant start, Instant end, ArrayList<RecordedEvent> events) {
        long total = 0;
        for (int i = 0; i < events.size(); i++) {
            Instant eStart = events.get(i).getInstant("startTime");
            if (start.compareTo(eStart) <= 0 && end.compareTo(eStart) >= 0) {
                total += events.get(i).getLong("duration");
            }
        }
        return total;
    }

    private static double[] getCpuAverages(ArrayList<RecordedEvent> cpuEvents, Instant start, Instant end) {
        double[] averages = new double[2];

        int[] startStops = findClosestStops(start, cpuEvents);
        int[] endStops = findClosestStops(end, cpuEvents);
        
        double startUserCpu = startStops.length == 1 
            ? cpuEvents.get(startStops[0]).getDouble("jvmUser") * 100
            : (cpuEvents.get(startStops[0]).getDouble("jvmUser") * 100 + cpuEvents.get(startStops[1]).getDouble("jvmUser") * 100) / 2;
        double endUserCpu = endStops.length == 1 
            ? cpuEvents.get(endStops[0]).getDouble("jvmUser") * 100
            : (cpuEvents.get(endStops[0]).getDouble("jvmUser") * 100 + cpuEvents.get(endStops[1]).getDouble("jvmUser") * 100) / 2;
        
        averages[0] = (startUserCpu + endUserCpu) / 2;

        double startSysCpu = startStops.length == 1 
            ? cpuEvents.get(startStops[0]).getDouble("jvmSystem") * 100
            : (cpuEvents.get(startStops[0]).getDouble("jvmSystem") * 100 + cpuEvents.get(startStops[1]).getDouble("jvmSystem") * 100) / 2;
        double endSysCpu = endStops.length == 1 
            ? cpuEvents.get(endStops[0]).getDouble("jvmSystem") * 100
            : (cpuEvents.get(endStops[0]).getDouble("jvmSystem") * 100 + cpuEvents.get(endStops[1]).getDouble("jvmSystem") * 100) / 2;
        
        averages[1] = (startSysCpu + endSysCpu) / 2;

        return averages;
    }

    private static long[] getHeapAverages(ArrayList<RecordedEvent> heapEvents, Instant start, Instant end) {
        long[] averages = new long[2];

        int[] startStops = findClosestStops(start, heapEvents);
        int[] endStops = findClosestStops(end, heapEvents);
        
        long startCommitted = startStops.length == 1 
            ? (heapEvents.get(startStops[0]).getLong("heapSpace.committedSize"))
            : (heapEvents.get(startStops[0]).getLong("heapSpace.committedSize") + heapEvents.get(startStops[1]).getLong("heapSpace.committedSize")) / 2;
        long endCommitted = endStops.length == 1 
            ? (heapEvents.get(endStops[0]).getLong("heapSpace.committedSize"))
            : (heapEvents.get(endStops[0]).getLong("heapSpace.committedSize") + heapEvents.get(endStops[1]).getLong("heapSpace.committedSize")) / 2;
        
        averages[0] = (startCommitted + endCommitted) / 2;

        long startUsed = startStops.length == 1 
            ? (heapEvents.get(startStops[0]).getLong("heapUsed"))
            : (heapEvents.get(startStops[0]).getLong("heapUsed") + heapEvents.get(startStops[1]).getLong("heapUsed")) / 2;
        long endUsed = endStops.length == 1 
            ? (heapEvents.get(endStops[0]).getLong("heapUsed"))
            : (heapEvents.get(endStops[0]).getLong("heapUsed") + heapEvents.get(endStops[1]).getLong("heapUsed")) / 2;
        
        averages[1] = (startUsed + endUsed) / 2;

        return averages;
    }

    private static long getActiveThreadCount(Instant start, Instant end, HashMap<String, ArrayList<RecordedEvent>> events) {
        return events.get("jdk.ThreadStart").stream()
            .filter((t) -> {
                if (t.hasField("thread.osThreadId") && t.getStartTime().compareTo(start) <= 0) { // starts before start
                    for (RecordedEvent e1 : events.get("jdk.ThreadEnd")) {
                        if (e1.hasField("thead.osThreadId") && e1.getLong("thread.osThreadId")==(t.getLong("thread.osThreadId"))) {
                            if (e1.getStartTime().compareTo(start) < 0) return false; // doesn't end before start
                            else break;
                        }
                    } // or isn't recorded to have ended

                    for (RecordedEvent e2 : events.get("jdk.ThreadPark")) {
                        // and gets unparked before end
                        if (e2.hasField("thead.osThreadId") && e2.getLong("eventThread.osThreadId")==(t.getLong("thread.osThreadId"))) {
                            return e2.getStartTime().plus(e2.getDuration()).compareTo(end) <= 0;
                        }      
                    }

                    // or isn't parked
                    return true;
                }
                return false;
            })
            .count();
    }

    private static double getThreadCountAverage(ArrayList<RecordedEvent> threadEvents, Instant start, Instant end, String threadType) {
        ArrayList<Integer> threadStops = findStopsBetween(start, end, threadEvents);
        if (threadStops.isEmpty()) return 0;
        long sum = 0;
        for (int i : threadStops)
            sum += threadEvents.get(i).getLong(threadType);
        return ((double) sum) / threadStops.size();
    }

    private static long getThreadSleepTotal(Instant start, Instant end, ArrayList<RecordedEvent> events) {
        return events.stream()
            .filter((t) -> t.getStartTime().compareTo(end) <= 0 
                            && t.getStartTime().plus(t.getDuration()).compareTo(start) >= 0)
            .reduce(
                (long) 0,
                ((sum, e) -> {
                    return sum + Math.min(
                        Duration.between(start, e.getStartTime().plus(e.getDuration())).toMillis(), 
                        Duration.between(start, end).toMillis()
                    );
                }),
                ((a, b) -> a + b)
            );
    }

    private static void initTestClassStats(HashMap<String, ArrayList<RecordedEvent>> eventData, TestClassData testClass, ArrayList<String> gcEventNames) {
        if (eventData.containsKey("jdk.CPULoad")) {
            double[] cpuAverages = getCpuAverages(eventData.get("jdk.CPULoad"), testClass.getStart(), testClass.getEnd());
            testClass.averageUserCpu = cpuAverages[0];
            testClass.averageSystemCpu = cpuAverages[1];
        }

        if (eventData.containsKey("jdk.GCHeapSummary")) {
            long[] heapAverages = getHeapAverages(eventData.get("jdk.GCHeapSummary"), testClass.getStart(), testClass.getEnd());
            testClass.averageCommittedHeap = heapAverages[0];
            testClass.averageUsedHeap = heapAverages[1];
            testClass.averageUsedHeapRatio = ((double) (heapAverages[1] / 100000) / (heapAverages[0] / 100000));
        }

        if (eventData.containsKey("jdk.ClassLoad"))
            testClass.classesLoaded = countEventsBetween(testClass.getStart(), testClass.getEnd(), eventData.get("jdk.ClassLoad"));
        
        if (eventData.containsKey("jdk.Compilation"))
            testClass.compiledMethods = countEventsBetween(testClass.getStart(), testClass.getEnd(), eventData.get("jdk.Compilation"));

        if (eventData.containsKey("jdk.FileRead"))
            testClass.fileReadDuration = getTotalDurationBetween(testClass.getStart(), testClass.getEnd(), eventData.get("jdk.FileRead"));
        
        if (eventData.containsKey("jdk.FileWrite"))
            testClass.fileWriteDuration = getTotalDurationBetween(testClass.getStart(), testClass.getEnd(), eventData.get("jdk.FileWrite"));
        
        if (eventData.containsKey("jdk.SocketRead"))
            testClass.socketReadDuration = getTotalDurationBetween(testClass.getStart(), testClass.getEnd(), eventData.get("jdk.SocketRead"));

        if (eventData.containsKey("jdk.SocketWrite"))
            testClass.socketWriteDuration = getTotalDurationBetween(testClass.getStart(), testClass.getEnd(), eventData.get("jdk.SocketWrite"));
    
        if (eventData.containsKey("jdk.JavaThreadStatistics")) {
            testClass.totalActiveThreads = getThreadCountAverage(eventData.get("jdk.JavaThreadStatistics"), testClass.getStart(), testClass.getEnd(), "activeCount");
            testClass.activeDaemonThreads = getThreadCountAverage(eventData.get("jdk.JavaThreadStatistics"), testClass.getStart(), testClass.getEnd(), "daemonCount");
        }

        if (eventData.containsKey("jdk.ThreadStart") && eventData.containsKey("jdk.ThreadEnd"))
            testClass.activeThreads = getActiveThreadCount(testClass.getStart(), testClass.getEnd(), eventData);

        if (eventData.containsKey("jdk.ThreadSleep"))
            testClass.totalThreadSleep = getThreadSleepTotal(testClass.getStart(), testClass.getEnd(), eventData.get("jdk.ThreadSleep"));

        if (includeMethodData) {
            if (debug) System.out.println("Begin processing exec samples");
            if (eventData.containsKey("jdk.ExecutionSample")) {
                int curr = 0;
                while (
                    curr < eventData.get("jdk.ExecutionSample").size()
                    && testClass.getEnd().compareTo(eventData.get("jdk.ExecutionSample").get(curr).getInstant("startTime")) >= 0
                ) {
                    if (testClass.getStart().compareTo(eventData.get("jdk.ExecutionSample").get(curr).getInstant("startTime")) <= 0) {
                        RecordedEvent currEvent = eventData.get("jdk.ExecutionSample").get(curr);
                        if (currEvent.getStackTrace() != null && !currEvent.getStackTrace().getFrames().isEmpty()) {
                            testClass.stackTraceCount++;
                            List<RecordedFrame> frames = currEvent.getStackTrace().getFrames();
                            int currFrame = 0;
                            while (currFrame < frames.size()
                                && (frames.get(currFrame).getMethod() == null 
                                    || !frames.get(currFrame).getMethod().getName().toLowerCase().contains("test"))) {
                                if (frames.get(currFrame).getMethod() != null) {
                                    String className = frames.get(currFrame).getMethod().getType().getName();
                                    if (!className.contains("junit")) {
                                        String methodName = frames.get(currFrame).getMethod().getName();
                                        String fullName = className + "." + methodName + " " + frames.get(currFrame).getMethod().getDescriptor();
                                        Integer currVal = testClass.nonTestMethodCounts.putIfAbsent(fullName, 1);
                                        if (currVal != null)
                                            testClass.nonTestMethodCounts.put(fullName, currVal + 1);
                                    }
                                }
                                currFrame++;
                            }
                        }
                    }
                    curr++;
                }
            }
        }
    }

    private static void initTestMethodStats(HashMap<String, ArrayList<RecordedEvent>> eventData, TestClassData testClass, ArrayList<String> gcEventNames) {
        for (TestMethodData testMethod : testClass.getMethods()) {
            if (eventData.containsKey("jdk.CPULoad")) {
                double[] cpuAverages = getCpuAverages(eventData.get("jdk.CPULoad"), testMethod.getStart(), testMethod.getEnd());
                testMethod.averageUserCpu = cpuAverages[0];
                testMethod.averageSystemCpu = cpuAverages[1];
            }

            if (eventData.containsKey("jdk.GCHeapSummary")) {
                long[] heapAverages = getHeapAverages(eventData.get("jdk.GCHeapSummary"), testMethod.getStart(), testMethod.getEnd());
                testMethod.averageCommittedHeap = heapAverages[0];
                testMethod.averageUsedHeap = heapAverages[1];
            }

            if (eventData.containsKey("jdk.ClassLoad"))
                testMethod.classesLoaded = countEventsBetween(testMethod.getStart(), testMethod.getEnd(), eventData.get("jdk.ClassLoad"));
            
            if (eventData.containsKey("jdk.Compilation"))
                testMethod.compiledMethods = countEventsBetween(testMethod.getStart(), testMethod.getEnd(), eventData.get("jdk.Compilation"));

            if (eventData.containsKey("jdk.FileRead"))
                testMethod.fileReadDuration = getTotalDurationBetween(testMethod.getStart(), testMethod.getEnd(), eventData.get("jdk.FileRead"));
            
            if (eventData.containsKey("jdk.FileWrite"))
                testMethod.fileWriteDuration = getTotalDurationBetween(testMethod.getStart(), testMethod.getEnd(), eventData.get("jdk.FileWrite"));
            
            if (eventData.containsKey("jdk.SocketRead"))
                testMethod.socketReadDuration = getTotalDurationBetween(testMethod.getStart(), testMethod.getEnd(), eventData.get("jdk.SocketRead"));

            if (eventData.containsKey("jdk.SocketWrite"))
                testMethod.socketWriteDuration = getTotalDurationBetween(testMethod.getStart(), testMethod.getEnd(), eventData.get("jdk.SocketWrite"));

            if (eventData.containsKey("jdk.JavaThreadStatistics")) {
                testMethod.totalActiveThreads = getThreadCountAverage(eventData.get("jdk.JavaThreadStatistics"), testMethod.getStart(), testMethod.getEnd(), "activeCount");
                testMethod.activeDaemonThreads = getThreadCountAverage(eventData.get("jdk.JavaThreadStatistics"), testMethod.getStart(), testMethod.getEnd(), "daemonCount");
            }
    
            if (eventData.containsKey("jdk.ThreadStart") && eventData.containsKey("jdk.ThreadEnd")) {
                testMethod.activeThreads = getActiveThreadCount(testMethod.getStart(), testMethod.getEnd(), eventData);
            }
    
            if (eventData.containsKey("jdk.ThreadSleep"))
                testMethod.totalThreadSleep = getThreadSleepTotal(testMethod.getStart(), testMethod.getEnd(), eventData.get("jdk.ThreadSleep"));
        
            if (eventData.containsKey("jdk.GarbageCollection")) {
                for (String gcName : gcEventNames) {
                    testMethod.garbageCollections.put(
                        gcName, 
                        (int) eventData.get("jdk.GarbageCollection").stream()
                            .filter(e -> e.getString("name").equals(gcName) && testMethod.getStart().compareTo(e.getInstant("startTime")) <= 0 && testMethod.getEnd().compareTo(e.getInstant("startTime")) >= 0)
                            .count()
                    );
                }
            }
        }
    }

    static private class JFRSummary {
        HashMap<String, ArrayList<RecordedEvent>> events;
        ArrayList<String> gcEventNames;

        JFRSummary() {
            events = new HashMap<>();
            gcEventNames = new ArrayList<>();
        }
    }

    private static JFRSummary getEvents(RecordingFile file) throws IOException {
        JFRSummary summary = new JFRSummary();

        while (file.hasMoreEvents()) {
            RecordedEvent event = file.readEvent();

            String eventName = event.getEventType().getName();
            if (!summary.events.containsKey(eventName))
                summary.events.put(eventName, new ArrayList<RecordedEvent>());
            summary.events.get(eventName).add(event);

            if (eventName.equals("jdk.GarbageCollection") && !summary.gcEventNames.contains(event.getString("name")))
                summary.gcEventNames.add(event.getString("name"));
        }

        if (debug) System.out.println("Begin sorting events");

        if (includeMethodData && summary.events.containsKey("jdk.ExecutionSample"))
            summary.events.put(
                "jdk.ExecutionSample", 
                summary.events.get("jdk.ExecutionSample").stream()
                    .filter((e) -> e.getString("sampledThread.osName").equals("main")).collect(Collectors.toCollection(ArrayList::new))
            );

        String[] eventsToSort = {
            "CPULoad", "ClassLoad", "Compilation", 
            "FileRead", "FileWrite", "SocketRead", "SocketWrite", 
            "GCHeapSummary", "GarbageCollection",
            "ExecutionSample"
        };
        for (String event : eventsToSort) {
            if (summary.events.containsKey("jdk." + event))
                summary.events.put("jdk." + event, getSortedAndFilteredEvents(summary.events, "jdk." + event));
        }

        return summary;
    }
    
    private static JFRSummary writeTestSuiteSummary(RecordingFile file, String gcLogPath, ArrayList<TestClassData> testClasses, String dest) throws IOException {
        JFRSummary summary = getEvents(file);

        if (Files.exists(Path.of(gcLogPath))) getGcData(gcLogPath, testClasses);

        for (TestClassData testClass : testClasses)
            initTestClassStats(summary.events, testClass, summary.gcEventNames);

        if (debug) System.out.println("Writing test classes summary");
    
        // (new File(dest)).mkdirs();
        // try (BufferedWriter writer = new BufferedWriter(new FileWriter(dest + "/test_suite_summary.csv"))) {
        //     String content = "Name,Order,Start time (ms),Duration (ms),Average user CPU %,Average system CPU %,Average committed heap (B),Average used heap (B),Average used/committed heap ratio,Classes loaded,Methods compiled,File read duration (ms),File write duration (ms),Socket read duration (ms),Socket write duration(ms),Active total thread count,Active daemon thread count,Thread sleep total,GC count,GC pause time,GC % of test time\n";
        //     for (int i = 0; i < testClasses.size(); i++) {
        //         content += testClasses.get(i).toCsvString(testClasses.get(0).getStart(), i) + "\n";
        //     }
        //     writer.write(content);
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }

        // HashMap<String, Integer> allNonTestMethods = testClasses.stream()
        //     .reduce(
        //         new HashMap<String, Integer>(), 
        //         ((map, tc) -> {
        //             tc.nonTestMethodCounts.keySet().forEach(
        //                 (k) -> map.put(k, (map.containsKey(k) ? map.get(k) : 0) + tc.nonTestMethodCounts.get(k))
        //             );
        //             return map;
        //         }),
        //         ((m, a) -> m)
        //     );
        // double totalCount = (double) testClasses.stream()
        //     .reduce(
        //         0, 
        //         ((sum, tc) -> sum + tc.stackTraceCount),
        //         ((s, a) -> s + a)
        //     );
        // Utils.writeResults(
        //     dest, 
        //     "hot_methods.csv", 
        //     allNonTestMethods.keySet().stream()
        //         .sorted((k1, k2) -> allNonTestMethods.get(k2).compareTo(allNonTestMethods.get(k1)))
        //         .reduce(
        //             "Name,Appearances in stack traces,Appearance rate\n", 
        //             (csvStr, k) -> {
        //                 double rate = (((double) allNonTestMethods.get(k)) / totalCount * 100);
        //                 return csvStr + k + "," + allNonTestMethods.get(k) + "," + df.format(rate) + "\n";
        //             }
        //         )
        // );

        return summary;
    }

    private static JFRSummary writeTestClassSummary(RecordingFile file, TestClassData testClass, String dest) throws IOException {
        JFRSummary summary = getEvents(file);
        
        initTestClassStats(summary.events, testClass, summary.gcEventNames);
        initTestMethodStats(summary.events, testClass, summary.gcEventNames);

        (new File(dest)).mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dest + "/test_methods_summary.csv"))) {
            String content = "Name,Order,Start time (ms),Duration (ms),Average user CPU %,Average system CPU %,Average committed heap (B),Average used heap (B),Classes loaded,Methods compiled,File read duration (ms),File write duration (ms),Socket read duration (ms),Socket write duration(ms),Active total thread count,Active daemon thread count,Thread sleep total";
            for (String name : summary.gcEventNames) content += "," + name;
            content += "\n";

            for (int i = 0; i < testClass.getMethods().size(); i++) {
                content += testClass.getMethods().get(i).toCsvString(testClass.getStart(), i) + "\n";
            }

            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // try (BufferedWriter writer = new BufferedWriter(new FileWriter(dest + "/hot_methods.csv"))) {
        //     String content = "Name,Appearances in stack traces,Appearance rate\n";

        //     ArrayList<String> sortedKeys = (new ArrayList<String>(testClass.nonTestMethodCounts.keySet()));
        //     sortedKeys.sort(
        //         (k1, k2) -> testClass.nonTestMethodCounts.get(k2).compareTo(testClass.nonTestMethodCounts.get(k1))
        //     );

        //     for (String k : sortedKeys) {
        //         double rate = (((double) testClass.nonTestMethodCounts.get(k)) / ((double) testClass.stackTraceCount) * 100);
        //         content += "" + k + "," + testClass.nonTestMethodCounts.get(k) + "," + df.format(rate) + "\n";
        //     }

        //     writer.write(content);
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }

        return summary;
    }
}
