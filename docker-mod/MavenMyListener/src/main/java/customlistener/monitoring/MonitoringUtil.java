package customlistener.monitoring;

import java.lang.management.*;
import java.util.List;

public class MonitoringUtil {
    private final MemoryMXBean memoryMXBean;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    private final com.sun.management.OperatingSystemMXBean osMXBean;
    private final ClassLoadingMXBean classLoadingMXBean;
    private final ThreadMXBean threadMXBean;
    private final CompilationMXBean compilationMXBean;
    private final int availableProcessors;

    public MonitoringUtil() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.osMXBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.compilationMXBean = ManagementFactory.getCompilationMXBean();
        this.availableProcessors = osMXBean.getAvailableProcessors();
    }

    public MemoryMXBean getMemoryMXBean() {
        return memoryMXBean;
    }

    public List<GarbageCollectorMXBean> getGcMXBeans() {
        return gcMXBeans;
    }

    public com.sun.management.OperatingSystemMXBean getOsMXBean() {
        return osMXBean;
    }

    public ClassLoadingMXBean getClassLoadingMXBean() {
        return classLoadingMXBean;
    }

    public ThreadMXBean getThreadMXBean() {
        return threadMXBean;
    }

    public CompilationMXBean getCompilationMXBean() {
        return compilationMXBean;
    }
    public int getAvailableProcessors() {
        return availableProcessors;
    }
}