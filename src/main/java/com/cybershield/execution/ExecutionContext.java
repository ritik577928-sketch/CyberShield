package com.cybershield.execution;

import java.util.*;

/** Per-run counters (comparisons / operations) plus the optional {@link Trace}. Not shared between runs. */
public final class ExecutionContext {
    public final Trace trace;
    private long comparisons;
    private long operations;
    private final Map<String, Long> stats = new LinkedHashMap<>();

    public ExecutionContext(Trace trace) { this.trace = trace; }

    public void cmp() { comparisons++; }
    public void cmp(long n) { comparisons += n; }
    public void op() { operations++; }
    public void op(long n) { operations += n; }
    public void stat(String name, long delta) { stats.merge(name, delta, Long::sum); }
    public long stat(String name) { return stats.getOrDefault(name, 0L); }

    public long comparisons() { return comparisons; }
    public long operations() { return operations; }

    /** True while the trace can still accept events. */
    public boolean tracing() { return trace.room(); }

    /** A context with tracing disabled, for helper phases that should not appear in the animation. */
    public ExecutionContext quiet() { return new ExecutionContext(Trace.off()); }

    public void absorb(ExecutionContext other) {
        comparisons += other.comparisons;
        operations += other.operations;
        other.stats.forEach((k, v) -> stats.merge(k, v, Long::sum));
    }
}
