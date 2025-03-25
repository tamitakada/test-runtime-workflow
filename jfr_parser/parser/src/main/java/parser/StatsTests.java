package parser;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.*;
import java.util.HashMap;
import java.time.Duration;

import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;


public class StatsTests {

    private static KendallsCorrelation kc = new KendallsCorrelation();
    private static MannWhitneyUTest ut = new MannWhitneyUTest();

    public static Map<String, Double[]> getKTValsAcrossTestClasses(Map<String, ArrayList<TestClassData>> testClasses) {
        return testClasses.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey, 
                k -> {
                    ArrayList<TestClassData> tcs = testClasses.get(k.getKey());
                    double[] durationInSecs = tcs.stream().mapToDouble((tc) -> (double) tc.getDuration().toMillis() / 1000).toArray();
                    return new Double[]{
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.averageUserCpu).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.averageSystemCpu).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.averageCommittedHeap / 1000000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.averageUsedHeap / 1000000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.averageUsedHeapRatio).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.classesLoaded).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.compiledMethods).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> (double) tc.fileReadDuration / 1000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> (double) tc.fileWriteDuration / 1000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> (double) tc.socketReadDuration / 1000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> (double) tc.socketWriteDuration / 1000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.activeThreads).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.activeDaemonThreads).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> (double) tc.totalThreadSleep / 1000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.gcIds.size()).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> (double) tc.totalGcPauseTime / 1000).toArray()),
                        kc.correlation(durationInSecs, tcs.stream().mapToDouble((tc) -> tc.gcPercent).toArray())
                    };
                }
            ));
    }

    public static double[] getKTVals(TestClassData[] testClasses) {
        double[] durationInSecs = Arrays.stream(testClasses).mapToDouble((tc) -> (double) tc.getDuration().toMillis() / 1000).toArray();
        return new double[]{
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.averageUserCpu).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.averageSystemCpu).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.averageCommittedHeap / 1000000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.averageUsedHeap / 1000000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.averageUsedHeapRatio).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.classesLoaded).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.compiledMethods).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> (double) tc.fileReadDuration / 1000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> (double) tc.fileWriteDuration / 1000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> (double) tc.socketReadDuration / 1000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> (double) tc.socketWriteDuration / 1000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.activeThreads).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.activeDaemonThreads).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> (double) tc.totalThreadSleep / 1000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.gcIds.size()).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> (double) tc.totalGcPauseTime / 1000).toArray()),
            kc.correlation(durationInSecs, Arrays.stream(testClasses).mapToDouble((tc) -> tc.gcPercent).toArray())
        };
    }

    public static ArrayList<Double> uTestAllOrderPairs(TestClassData[][][] orders) {
        ArrayList<Double> pValues = new ArrayList<>();
        for (int i = 0; i < orders.length; i++) {
            for (int j = i+1; j < orders.length; j++) {
                pValues.add(uTestOrderPair(orders[i], orders[j]));
            }
        }
        return pValues;
    }

    public static Duration testSuiteDuration(TestClassData[] testSuite) {
        return Duration.between(
            Arrays.stream(testSuite).min((tc1, tc2) -> tc1.getStart().compareTo(tc2.getStart())).get().getStart(),
            Arrays.stream(testSuite).max((tc1, tc2) -> tc1.getEnd().compareTo(tc2.getEnd())).get().getEnd()
        );
    }

    public static double uTestOrderPair(TestClassData[][] o1, TestClassData[][] o2) {
        double[] o1Durations = Arrays.stream(o1)
            .mapToDouble((trial) -> (double) testSuiteDuration(trial).toSeconds() / 60)
            .toArray();
        double[] o2Durations = Arrays.stream(o2)
            .mapToDouble((trial) -> (double) testSuiteDuration(trial).toSeconds() / 60)
            .toArray();
        return ut.mannWhitneyUTest(o1Durations, o2Durations);
    }

    private static double[] getUTestStats(double[] s1, double[] s2) {
        int s2j = 0;
        int u1 = 0;
        for (int s1j = 0; s1j < s1.length; s1j++) {
            while (s2j < s2.length && s1[s1j] > s2[s2j]) s2j++;
            u1 += s2j;
        }
        double r = 2* ((double) u1) / (s1.length * s2.length) - 1;
        return new double[]{ut.mannWhitneyUTest(s1, s2), r};
    }

    public static double[][][] uTestOrderPairTestClasses(TestClassData[][] o1, TestClassData[][] o2) {
        final HashMap<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < o1[0].length; i++)
            indexMap.put(o1[0][i].getName(), i);

        int[] normalizedOrder = IntStream.range(0, o1[0].length)
            .boxed()
            .sorted((i1, i2) -> {
                if (!indexMap.containsKey(o2[0][i1].getName()) && !indexMap.containsKey(o2[0][i2].getName()))
                    return 0;
                else if (!indexMap.containsKey(o2[0][i1].getName())) return 1;
                else if (!indexMap.containsKey(o2[0][i2].getName())) return -1;
                return indexMap.get(o2[0][i1].getName()).compareTo(indexMap.get(o2[0][i2].getName()));
            })
            .mapToInt(Integer::intValue)
            .toArray();

        double[][][] pValues = new double[normalizedOrder.length][17][2];

        try {
            for (int i = 0; i < o1[0].length; i++) {
                final int index = i;
                pValues[i] = new double[][]{
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> (double) tcs[index].getDuration().toMillis() / 1000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> (double) tcs[normalizedOrder[index]].getDuration().toMillis() / 1000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].averageUserCpu)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].averageUserCpu)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].averageSystemCpu)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].averageSystemCpu)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].averageCommittedHeap / 1000000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].averageCommittedHeap / 1000000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].averageUsedHeap / 1000000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].averageUsedHeap / 1000000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].averageUsedHeapRatio)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].averageUsedHeapRatio)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].classesLoaded)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].classesLoaded)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].compiledMethods)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].compiledMethods)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> (double) tcs[index].fileReadDuration / 1000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> (double) tcs[normalizedOrder[index]].fileReadDuration / 1000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> (double) tcs[index].fileWriteDuration / 1000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> (double) tcs[normalizedOrder[index]].fileWriteDuration / 1000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> (double) tcs[index].socketReadDuration / 1000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> (double) tcs[normalizedOrder[index]].socketReadDuration / 1000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> (double) tcs[index].socketWriteDuration / 1000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> (double) tcs[normalizedOrder[index]].socketWriteDuration / 1000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].activeThreads)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].activeThreads)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].activeDaemonThreads)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].activeDaemonThreads)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> (double) tcs[index].totalThreadSleep / 1000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].totalThreadSleep / 1000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].gcIds.size())
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].gcIds.size())
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> (double) tcs[index].totalGcPauseTime / 1000)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> (double) tcs[normalizedOrder[index]].totalGcPauseTime / 1000)
                            .sorted()
                            .toArray()
                    ),
                    getUTestStats(
                        Arrays.stream(o1)
                            .mapToDouble((tcs) -> tcs[index].gcPercent)
                            .sorted()
                            .toArray(),
                        Arrays.stream(o2)
                            .mapToDouble((tcs) -> tcs[normalizedOrder[index]].gcPercent)
                            .sorted()
                            .toArray()
                    )
                };
            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
        

        return pValues;
    }

}