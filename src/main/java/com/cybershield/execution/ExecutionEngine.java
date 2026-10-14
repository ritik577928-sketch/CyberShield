package com.cybershield.execution;

import com.cybershield.algorithm.*;
import com.cybershield.metrics.MemoryProbe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.logging.*;

/** Runs algorithms, measuring time / comparisons / operations / allocated memory, and never lets an algorithm failure escape. */
public final class ExecutionEngine {
    private static final Logger LOG = Logger.getLogger(ExecutionEngine.class.getName());
    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger active = new AtomicInteger();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, Object>> lastRun = new ConcurrentHashMap<>();
    private volatile Consumer<RunRecord> listener = r -> {};

    public void setListener(Consumer<RunRecord> l) { listener = l; }
    public int activeRuns() { return active.get(); }
    public Set<String> runningAlgorithms() { return running; }
    public Map<String, Map<String, Object>> lastRuns() { return lastRun; }

    /** Execute and record the run in history. */
    public RunRecord execute(Algorithm algo, AlgorithmInput in, String datasetId, boolean trace, int traceLimit) {
        RunRecord r = measure(algo, in, datasetId, trace, traceLimit);
        record(r);
        return r;
    }

    /** Publish an already measured run to the history listener and the "last run" table. */
    public void record(RunRecord r) {
        lastRun.put(r.info.id(), r.toSummaryMap());
        try { listener.accept(r); } catch (RuntimeException e) { LOG.log(Level.WARNING, "Run listener failed", e); }
    }

    /** Execute without touching history (used for warm-up / repeated benchmark runs). */
    public RunRecord measure(Algorithm algo, AlgorithmInput in, String datasetId, boolean trace, int traceLimit) {
        RunRecord r = new RunRecord();
        r.id = ids.incrementAndGet();
        r.timestamp = System.currentTimeMillis();
        r.info = algo.info();
        r.datasetId = datasetId;
        r.input = in.describe();
        ExecutionContext ctx = new ExecutionContext(trace ? Trace.on(traceLimit) : Trace.off());
        r.trace = ctx.trace;
        active.incrementAndGet();
        running.add(r.info.id());
        long mem0 = MemoryProbe.allocatedNow();
        long t0 = System.nanoTime();
        try {
            r.result = algo.run(in, ctx);
        } catch (Exception | StackOverflowError e) {
            r.status = "FAILED";
            r.error = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "no message" : e.getMessage());
            LOG.log(Level.WARNING, "Algorithm " + r.info.id() + " failed: " + r.error);
        } finally {
            r.nanos = System.nanoTime() - t0;
            r.memoryBytes = Math.max(0, MemoryProbe.allocatedNow() - mem0);
            r.comparisons = ctx.comparisons();
            r.operations = ctx.operations();
            running.remove(r.info.id());
            active.decrementAndGet();
        }
        if (r.ok()) LOG.fine(() -> r.info.id() + " finished in " + r.millis() + " ms");
        return r;
    }
}
