package parser;

import parser.TestClassData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;


public class StatsProcessor {

    private static class AverageTestClassData {
        String name;

        int[] orders;
        long[] durations;
        double[] userCpus;
        double[] sysCpus;
        long[] committedHeap;
        long[] usedHeap;
        double[] heapRatios;
        int[] classesLoaded;
        int[] compiledMethods;
        long[] fileReads;
        long[] fileWrites;
        long[] socketReads;
        long[] socketWrites;
        HashMap<String, int[]> gcs;

        int[] gcCounts;
        long[] gcPauseTimes;
        double[] gcPercents;

        double[] activeThreads;
        double[] daemons;
        long[] threadSleep;

        AverageTestClassData(String name, int size, String[] gcTypes) {
            this.name = name;

            orders = new int[size];
            durations = new long[size];
            userCpus = new double[size];
            sysCpus = new double[size];
            committedHeap = new long[size];
            usedHeap = new long[size];
            heapRatios = new double[size];
            classesLoaded = new int[size];
            compiledMethods = new int[size];
            fileReads = new long[size];
            fileWrites = new long[size];
            socketReads = new long[size];
            socketWrites = new long[size];
            activeThreads = new double[size];
            daemons = new double[size];
            threadSleep = new long[size];
            gcCounts = new int[size];
            gcPauseTimes = new long[size];
            gcPercents = new double[size];

            gcs = new HashMap<String, int[]>();
            for (String gct : gcTypes) gcs.put(gct, new int[size]);
        }
    }

    public static double[] getMeanAndDeviation(double[] data) {
        double[] stats = {0, 0};

        for (double d : data) stats[0] += d;
        stats[0] /= data.length;

        for (double d : data) stats[1] += Math.pow(d - stats[0], 2);
        stats[1] = Math.sqrt(stats[1] / (data.length - 1));

        return stats;
    }

    public static double[] getMeanAndDeviation(int[] data) {
        double[] converted = new double[data.length];
        for (int i = 0; i < data.length; i++) converted[i] = (double) data[i];
        return getMeanAndDeviation(converted);
    }

    public static long[] getMeanAndDeviation(long[] data) {
        long[] stats = {0, 0};

        for (long d : data) stats[0] += d;
        stats[0] /= data.length;

        for (long d : data) stats[1] += Math.pow(d - stats[0], 2);
        double sqrt = Math.sqrt(stats[1] / (data.length - 1));
        stats[1] = (long) sqrt;

        return stats;
    }

    public static String averageTestClassStats(HashMap<String, HashMap<String, Double>> map, TestClassData[][] data) {
        AverageTestClassData[] averages = new AverageTestClassData[data[0].length];

        final HashMap<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < data[0].length; i++)
            indexMap.put(data[0][i].getName(), i);

        String[] gcTypes = data[0].length > 0
            ? data[0][0].garbageCollections.keySet().stream()
                .sorted()
                .toArray(String[]::new)
            : new String[0];

        for (int t = 0; t < data.length; t++) {
            final TestClassData[] trialData = data[t];

            int[] normalizedOrder = IntStream.range(0, data[t].length)
                .boxed()
                .sorted((i1, i2) -> indexMap.get(trialData[i1].getName()).compareTo(indexMap.get(trialData[i2].getName())))
                .mapToInt(Integer::intValue)
                .toArray();

            for (int i = 0; i < normalizedOrder.length; i++) {
                if (averages[i] == null) averages[i] = new AverageTestClassData(data[t][i].getName(), data.length, gcTypes);

                averages[i].orders[t] = normalizedOrder[i];
                averages[i].durations[t] = data[t][normalizedOrder[i]].getDuration().toMillis();
                averages[i].userCpus[t] = data[t][normalizedOrder[i]].averageUserCpu;
                averages[i].sysCpus[t] = data[t][normalizedOrder[i]].averageSystemCpu;
                averages[i].committedHeap[t] = data[t][normalizedOrder[i]].averageCommittedHeap;
                averages[i].usedHeap[t] = data[t][normalizedOrder[i]].averageUsedHeap;
                averages[i].heapRatios[t] = data[t][normalizedOrder[i]].averageUsedHeapRatio;
                averages[i].classesLoaded[t] = data[t][normalizedOrder[i]].classesLoaded;
                averages[i].compiledMethods[t] = data[t][normalizedOrder[i]].compiledMethods;
                averages[i].fileReads[t] = data[t][normalizedOrder[i]].fileReadDuration;
                averages[i].fileWrites[t] = data[t][normalizedOrder[i]].fileWriteDuration;
                averages[i].socketReads[t] = data[t][normalizedOrder[i]].socketReadDuration;
                averages[i].socketWrites[t] = data[t][normalizedOrder[i]].socketWriteDuration;
                averages[i].activeThreads[t] = data[t][normalizedOrder[i]].totalActiveThreads;
                averages[i].daemons[t] = data[t][normalizedOrder[i]].activeDaemonThreads;
                averages[i].threadSleep[t] = data[t][normalizedOrder[i]].totalThreadSleep;
                averages[i].gcCounts[t] = data[t][normalizedOrder[i]].gcIds.size();
                averages[i].gcPauseTimes[t] = data[t][normalizedOrder[i]].totalGcPauseTime;
                averages[i].gcPercents[t] = data[t][normalizedOrder[i]].gcPercent;

                for (String gct : gcTypes)
                    averages[i].gcs.get(gct)[t] = data[t][normalizedOrder[i]].garbageCollections.get(gct);
            }
        }

        String csvString = "Name,Order mean,Order deviation,Duration mean (ms),Duration deviation,User CPU % mean,User CPU % deviation,System CPU % mean,System CPU % deviation,Committed heap (B) mean,Committed heap deviation,Used heap (B) mean,Used heap deviation,Used heap ratio mean,Used heap ratio deviation,Classes loaded mean,Classes loaded deviation,Methods compiled mean,Methods compiled deviation,File read duration (ms) mean,File read deviation,File write duration (ms) mean,File write deviation,Socket read duration (ms) mean,Socket read deviation,Socket write duration(ms) mean,Socket write deviation,Avg active thread mean,Avg active count deviation,Avg active daemon mean,Avg active daemon deviation,Thread sleep mean,Thread sleep deviation,GC count,GC count deviation,GC pause time,GC pause time deviation,GC % test time,GC % deviation";
        for (String gct : gcTypes) csvString += "," + gct + " mean," + gct + " deviation";
        csvString += "\n";

        Arrays.sort(
            averages, 
            ((tc1, tc2) -> Long.compare(getMeanAndDeviation(tc1.durations)[0], getMeanAndDeviation(tc2.durations)[0]))
        );

        for (AverageTestClassData tc : averages) {
            long[] durationStats = getMeanAndDeviation(tc.durations);
            double[] orderStats = getMeanAndDeviation(tc.orders);
            double[] userCpuStats = getMeanAndDeviation(tc.userCpus);
            double[] sysCpuStats = getMeanAndDeviation(tc.sysCpus);
            long[] committedStats = getMeanAndDeviation(tc.committedHeap);
            long[] usedStats = getMeanAndDeviation(tc.usedHeap);
            double[] ratioStats = getMeanAndDeviation(tc.heapRatios);
            double[] classesLoaded = getMeanAndDeviation(tc.classesLoaded);
            double[] compiledMethods = getMeanAndDeviation(tc.compiledMethods);
            long[] fileReads = getMeanAndDeviation(tc.fileReads);
            long[] fileWrites = getMeanAndDeviation(tc.fileWrites);
            long[] socketReads = getMeanAndDeviation(tc.socketReads);
            long[] socketWrites = getMeanAndDeviation(tc.socketWrites);
            double[] activeThreads = getMeanAndDeviation(tc.activeThreads);
            double[] daemons = getMeanAndDeviation(tc.daemons);
            long[] threadSleeps = getMeanAndDeviation(tc.threadSleep);
            double[] gcCounts = getMeanAndDeviation(tc.gcCounts);
            long[] gcPauses = getMeanAndDeviation(tc.gcPauseTimes);
            double[] gcPercents = getMeanAndDeviation(tc.gcPercents);

            if (map != null) {
                if (!map.containsKey(tc.name)) map.put(tc.name, new HashMap<>());

                map.get(tc.name).put("durationMean", (double) durationStats[0] / 1000);
                map.get(tc.name).put("durationDev", (double) durationStats[1] / 1000);

                map.get(tc.name).put("userCpuMean", userCpuStats[0]);
                map.get(tc.name).put("userCpuDev", userCpuStats[1]);
                map.get(tc.name).put("sysCpuMean", sysCpuStats[0]);
                map.get(tc.name).put("sysCpuDev", sysCpuStats[1]);

                map.get(tc.name).put("committedHeapMean", (double) (committedStats[0] / 1000000));
                map.get(tc.name).put("committedHeapDev", (double) (committedStats[1] / 1000000));
                map.get(tc.name).put("usedHeapMean", (double) (usedStats[0] / 1000000));
                map.get(tc.name).put("usedHeapDev", (double) (usedStats[1] / 1000000));
                map.get(tc.name).put("usedHeapRatioMean", ratioStats[0]);
                map.get(tc.name).put("usedHeapRatioDev", ratioStats[1]);

                map.get(tc.name).put("classesLoadedMean", classesLoaded[0]);
                map.get(tc.name).put("classesLoadedDev", classesLoaded[1]);
                map.get(tc.name).put("compiledMethodsMean", compiledMethods[0]);
                map.get(tc.name).put("compiledMethodsDev", compiledMethods[1]);

                map.get(tc.name).put("fileReadMean", (double) fileReads[0] / 1000);
                map.get(tc.name).put("fileReadDev", (double) fileReads[1] / 1000);
                map.get(tc.name).put("fileWriteMean", (double) fileWrites[0] / 1000);
                map.get(tc.name).put("fileWriteDev", (double) fileWrites[1] / 1000);
                
                map.get(tc.name).put("socketReadMean", (double) socketReads[0] / 1000);
                map.get(tc.name).put("socketReadDev", (double) socketReads[1] / 1000);
                map.get(tc.name).put("socketWriteMean", (double) socketWrites[0] / 1000);
                map.get(tc.name).put("socketWriteDev", (double) socketWrites[1] / 1000);

                map.get(tc.name).put("activeThreadMean", activeThreads[0]);
                map.get(tc.name).put("activeThreadDev", activeThreads[1]);
                map.get(tc.name).put("activeDaemonMean", daemons[0]);
                map.get(tc.name).put("activeDaemonDev", daemons[1]);
                map.get(tc.name).put("threadSleepMean", (double) threadSleeps[0] / 1000);
                map.get(tc.name).put("threadSleepDev", (double) threadSleeps[1] / 1000);

                map.get(tc.name).put("gcCountsMean", gcCounts[0]);
                map.get(tc.name).put("gcCountsDev", gcCounts[1]);
                map.get(tc.name).put("gcPauseTimeMean", (double) gcPauses[0] / 1000);
                map.get(tc.name).put("gcPauseTimeDev", (double) gcPauses[1] / 1000);
                map.get(tc.name).put("gcPausePercentMean", gcPercents[0]);
                map.get(tc.name).put("gcPausePercentDev", gcPercents[1]);
            }

            HashMap<String, double[]> gcStats = new HashMap<>();
            for (String gct : gcTypes) {
                gcStats.put(gct, getMeanAndDeviation(tc.gcs.get(gct)));
            }
            
            csvString += "" + tc.name + "," + orderStats[0] + "," + orderStats[1] + ","
                + durationStats[0] + "," + durationStats[1] + ","
                + userCpuStats[0] + "," + userCpuStats[1] + ","
                + sysCpuStats[0] + "," + sysCpuStats[1]
                + "," + committedStats[0] + "," + committedStats[1] + "," + usedStats[0] + "," + usedStats[1] + ","
                + ratioStats[0] + "," + ratioStats[1] + ","
                + classesLoaded[0] + "," + classesLoaded[1] + "," + compiledMethods[0] + "," + compiledMethods[1] + ","
                + fileReads[0] + "," + fileReads[1] + "," + fileWrites[0] + "," + fileWrites[1] + ","
                + socketReads[0] + "," + socketReads[1] + "," + socketWrites[0] + "," + socketWrites[1]
                + "," + activeThreads[0] + "," + activeThreads[1] + "," + daemons[0] + "," + daemons[1] + ","
                + threadSleeps[0] + "," + threadSleeps[1]
                + "," + gcCounts[0] + "," + gcCounts[1] + "," + gcPauses[0] + "," + gcPauses[1]
                + "," + gcPercents[0] + "," + gcPercents[1];
            
            // for (String gct : gcTypes)
            //     csvString += "," + gcStats.get(gct)[0] + "," + gcStats.get(gct)[1];
            
            csvString += "\n";
        }
        
        return csvString;
    }
}