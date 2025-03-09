package customlistener.lightweightProfiler;

import customlistener.TestInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LightweightTestInfos {

    private ConcurrentHashMap<String, Long> testInfos;

    private final long BASE_TIME; //Base time in milliseconds

    private static final int START_OFFSET_BIT_POSITION = 1;
    private static final int END_OFFSET_BIT_POSITION = 29;

    private static final int OFFSET_BITS = 28;

    private static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;

    public LightweightTestInfos() {
        this.testInfos = new ConcurrentHashMap<>();
        this.BASE_TIME = System.currentTimeMillis(); // Capture the current time as the base time
    }

    public void setStartTime(String testIdentifier, long startTime) {
        long offset = (startTime - BASE_TIME) & OFFSET_MASK;
        long encodedData = testInfos.getOrDefault(testIdentifier, 0L);

        encodedData = (encodedData & ~(OFFSET_MASK << START_OFFSET_BIT_POSITION)) | (offset << START_OFFSET_BIT_POSITION);

        testInfos.put(testIdentifier, encodedData);
    }

    public void setEndTime(String testIdentifier, long endTime) {
        long offset = (endTime - BASE_TIME) & OFFSET_MASK;
        long encodedData = testInfos.getOrDefault(testIdentifier, 0L);

        encodedData = (encodedData & ~(OFFSET_MASK << END_OFFSET_BIT_POSITION)) | (offset << END_OFFSET_BIT_POSITION);

        testInfos.put(testIdentifier, encodedData);
    }

    public void setTestStatus(String testIdentifier, boolean failed) {
        long encodedData = testInfos.getOrDefault(testIdentifier, 0L);
        long statusBit = failed ? 1L : 0L;

        encodedData = (encodedData & ~1L) | statusBit;

        testInfos.put(testIdentifier, encodedData);
    }

    public long getStartTime(String testIdentifier) {
        Long encodedData = testInfos.get(testIdentifier);
        if (encodedData == null) return -1;

        long offset = (encodedData >>> START_OFFSET_BIT_POSITION) & OFFSET_MASK;
        return BASE_TIME + offset;
    }

    public long getEndTime(String testIdentifier) {
        Long encodedData = testInfos.get(testIdentifier);
        if (encodedData == null) return -1;

        long offset = (encodedData >>> END_OFFSET_BIT_POSITION) & OFFSET_MASK;
        return BASE_TIME + offset;
    }

    public boolean getFailedStatus(String testIdentifier) {
        Long encodedData = testInfos.get(testIdentifier);
        if (encodedData == null) return false;

        return (encodedData & 1L) == 1;
    }

    public boolean containsTestInfo(String testIdentifier) {
        return testInfos.containsKey(testIdentifier);
    }

    public void removeTestInfo(String testIdentifier) {
        testInfos.remove(testIdentifier);
    }

    public void clearTestInfos() {
        testInfos.clear();
    }

    public LinkedHashMap<String, TestInfo> convertFromLightweightToNormal(){
        List<Map.Entry<String, Long>> entries = new ArrayList<>(testInfos.entrySet());

        entries.sort(Comparator.comparingLong(entry -> getStartTime(entry.getKey())));

        LinkedHashMap<String, TestInfo> convertedMap = new LinkedHashMap<>();

        for(Map.Entry<String, Long> entry : entries){
            String key = entry.getKey();
            convertedMap.put(key, new TestInfo(key, getStartTime(key), getEndTime(key), getFailedStatus(key)));
        }

        return convertedMap;
    }
}
