package com.cybershield.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/** Measures bytes allocated by the current thread (exact on HotSpot); falls back to heap-used deltas. */
public final class MemoryProbe {
    private static final com.sun.management.ThreadMXBean SUN;

    static {
        com.sun.management.ThreadMXBean b = null;
        try {
            ThreadMXBean t = ManagementFactory.getThreadMXBean();
            if (t instanceof com.sun.management.ThreadMXBean s && s.isThreadAllocatedMemorySupported()) {
                s.setThreadAllocatedMemoryEnabled(true);
                b = s;
            }
        } catch (Throwable ignored) { /* use fallback */ }
        SUN = b;
    }

    private MemoryProbe() {}

    public static boolean exact() { return SUN != null; }

    public static long allocatedNow() {
        if (SUN != null) return SUN.getThreadAllocatedBytes(Thread.currentThread().threadId());
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    public static long heapUsed() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }
}
