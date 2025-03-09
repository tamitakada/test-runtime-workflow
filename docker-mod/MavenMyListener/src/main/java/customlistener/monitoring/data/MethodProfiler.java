package customlistener.monitoring.data;

//import customlistener.monitoring.loopDetection.LoopEntryFlag;
import net.bytebuddy.asm.Advice;

import java.util.concurrent.ConcurrentHashMap;

public class MethodProfiler {

    public static final ConcurrentHashMap<String, MethodData> methodDataMap = new ConcurrentHashMap<>();

    public static MethodData getOrCreateMethodData(String method) {
        return methodDataMap.computeIfAbsent(method, k -> new MethodData());
    }

    @Advice.OnMethodEnter
    public static long onMethodEnter(@Advice.Origin("#t.#m") String methodNative) {
        long startTime = System.nanoTime();

        //System.out.println("DOING THIS METHOD : " + methodNative);

        //MethodData methodData = getOrCreateMethodData(methodNative);
//
//        if (LoopEntryFlag.isInLoop()) {
//            methodData.incrementLoopCallCount();
//        }

        return startTime;
    }

    @Advice.OnMethodExit
    public static void onMethodExit(@Advice.Origin("#t.#m") String methodNative, @Advice.Enter long startTime) {
        long endTime = System.nanoTime();

        MethodData methodData = getOrCreateMethodData(methodNative);
        if (methodData != null) {
            methodData.updateAverageDuration(endTime - startTime);
        }
    }
}