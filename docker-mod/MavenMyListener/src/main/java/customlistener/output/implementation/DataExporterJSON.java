package customlistener.output.implementation;

import customlistener.TestInfo;
import customlistener.output.DataExporter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class DataExporterJSON extends DataExporter implements Runnable{

    private boolean headerFlag = true;
    private String logFilePath;
    public DataExporterJSON(String logFilePath,Map<String, TestInfo> testInfoMap) {
        super(testInfoMap);
        this.logFilePath = logFilePath;
    }

    @Override
    protected void writeResultsToFile() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath,true))) {
            File file = new File(logFilePath);
            if(headerFlag && file.length()==0) {
                writer.write("Test Name,Start Time,End Time,Failed," +
                        "Severity,Avg sys CPU %,AVG Committed Heap, AVG Used Heap,Classes Loaded,Classes Unloaded,Compilation Time,GC Count,GC Time,G1Full,G1New,G1Old,Thread Count,Peak Thread Count,Thread CPU Time\n");
                headerFlag = false;
            }
            for (TestInfo testInfo : testInfoMap.values()) {
                writer.write(testInfo.toJson() + "\n");
            }
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
