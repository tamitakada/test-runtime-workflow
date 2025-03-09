package customlistener.output.implementation;

import customlistener.TestInfo;
import customlistener.monitoring.data.MethodData;
import customlistener.monitoring.data.MethodProfiler;
import customlistener.output.DataExporter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

public class MethodDataExporter extends DataExporter implements Runnable {

    private String methodDataFilePath;

    public MethodDataExporter(String methodDataFilePath) {
        super(null);
        this.methodDataFilePath = methodDataFilePath;
    }
    @Override
    protected void writeResultsToFile() throws IOException {
        File file = new File(methodDataFilePath);
        System.out.println("WRITING METHOD DATA RESULTS TO FILE");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(methodDataFilePath, true))) {
            if (file.length() == 0) {
                writer.write("Method name,Call count,Average runtime (s),Total runtime (s),Percentage of total runtime\n");
            }

            long totalRuntimeNanos = MethodProfiler.methodDataMap.entrySet()
                    .stream()
                    .mapToLong(entry -> entry.getValue().getCallCount().get() * entry.getValue().getAverageDuration().get())
                    .sum();  // if runtime is above 2^63-1 nanoseconds (~2 hrs I believe) , this will overflow

            MethodProfiler.methodDataMap.entrySet()
                    .stream()
                    .sorted(Comparator.<Map.Entry<String, MethodData>>comparingLong(
                                    entry -> entry.getValue().getCallCount().get() * entry.getValue().getAverageDuration().get())
                            .reversed())
                    .forEach(entry -> {
                        try {
                            long callCount = entry.getValue().getCallCount().get();
                            long avgDurationNanos = entry.getValue().getAverageDuration().get();
                            long totalMethodRuntimeNanos = callCount * avgDurationNanos;
                            double avgDurationSeconds = avgDurationNanos / 1_000_000_000.0;
                            double totalRuntimeSeconds = totalMethodRuntimeNanos / 1_000_000_000.0;
                            double percentageOfTotal = (totalMethodRuntimeNanos / (double) totalRuntimeNanos) * 100;

                            writer.write(entry.getKey() + "," + callCount + "," + avgDurationSeconds + "," + totalRuntimeSeconds + "," + percentageOfTotal + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }


    @Override
    public void run() {
        try {
            writeResultsToFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}