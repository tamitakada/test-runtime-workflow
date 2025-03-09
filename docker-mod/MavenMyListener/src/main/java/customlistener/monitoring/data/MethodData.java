package customlistener.monitoring.data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MethodData {
    private final AtomicInteger callCount;
    private final AtomicLong averageDuration;
    private final AtomicInteger loopCallCount;
    private final AtomicInteger callsLeadingToLoops;

    public MethodData() {
        this.callCount = new AtomicInteger(0);
        this.averageDuration = new AtomicLong(0);
        this.loopCallCount = new AtomicInteger(0);
        this.callsLeadingToLoops = new AtomicInteger(0);
    }

    public AtomicInteger getCallCount() {
        return callCount;
    }

    public AtomicLong getAverageDuration() {
        return averageDuration;
    }

    public AtomicInteger getNumberOfIterationsInsideOfLoop() {
        return loopCallCount;
    }

    public AtomicInteger getCallsLeadingToLoops() {
        return callsLeadingToLoops;
    }

    public void updateAverageDuration(long duration) {
        int count = callCount.incrementAndGet();
        long currentAvg;

        do {
            currentAvg = averageDuration.get();
        } while (!averageDuration.compareAndSet(currentAvg, currentAvg + (duration - currentAvg) / count));
    }

    public void incrementLoopCallCount() {
        this.loopCallCount.incrementAndGet();
    }

    public void incrementCallsLeadingToLoops() {
        this.callsLeadingToLoops.incrementAndGet();
    }
}
