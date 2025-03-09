package customlistener.output;

import customlistener.TestInfo;
import customlistener.monitoring.data.MethodData;
import customlistener.output.implementation.DataExporterCSV;
import customlistener.output.implementation.DataExporterJSON;
import customlistener.output.implementation.MethodDataExporter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class DataExporter {

    protected Map<String, TestInfo> testInfoMap;

    public DataExporter(Map<String,TestInfo> testInfoMap) {
        this.testInfoMap = testInfoMap;
    }
    public static void generateReport(String logFilePath, String csvFilePath, Map<String, TestInfo> testInfoMap) {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        DataExporter csvExporter = new DataExporterCSV(csvFilePath, testInfoMap);
        DataExporter jsonExporter = new DataExporterJSON(logFilePath, testInfoMap);

        executor.submit((Runnable) csvExporter);
        executor.submit((Runnable) jsonExporter);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate");
                    executor.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

    }
    public static void exportMethodData(String methodFilePath){
        System.out.println("IN EXPORT METHOD DATA");

        try {
            new Thread(() -> new MethodDataExporter(methodFilePath).run()).start();
        }catch (Exception e){
            System.out.println("IN CATCH");
            e.printStackTrace();
        }
    }

    protected abstract void writeResultsToFile() throws IOException;

}