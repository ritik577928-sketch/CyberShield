package com.cybershield.algorithm.random;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Blelloch work-efficient parallel prefix sum (up-sweep, then down-sweep) over per-minute traffic counts. */
public final class BlellochScan implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("blelloch", "Blelloch Scan (parallel prefix)", "M6 · Randomized & Parallel", "CO6",
            "randomized-parallel", InputKind.SERIES, "work O(n), depth O(log n)", "O(n)",
            "Up-sweep builds partial sums in a binary tree; setting the root to 0 and down-sweeping yields the exclusive prefix sums. Each level can run in parallel.",
            "Large-scale log analytics: cumulative traffic and instant window sums to locate attack bursts.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int len = in.ints.length, n = 1;
        while (n < len) n <<= 1;
        int[] x = Arrays.copyOf(in.ints, n);
        if (ctx.tracing()) ctx.trace.emit("start", "Pad " + len + " per-minute flow counts to " + n + " (power of two).", "arr", x.clone());
        int levels = 0;
        for (int d = 1; d < n; d *= 2) {
            for (int i = 0; i < n; i += 2 * d) { x[i + 2 * d - 1] += x[i + d - 1]; ctx.op(); }
            levels++;
            if (ctx.tracing()) ctx.trace.emit("up", "Up-sweep, stride " + d + ": every right child adds its left sibling (" + (n / (2 * d)) + " additions in parallel).", "d", d, "arr", x.clone());
        }
        long total = x[n - 1];
        x[n - 1] = 0;
        if (ctx.tracing()) ctx.trace.emit("root", "Root holds the total " + total + "; set it to 0 to start the down-sweep.", "arr", x.clone());
        for (int d = n / 2; d >= 1; d /= 2) {
            for (int i = 0; i < n; i += 2 * d) { int t = x[i + d - 1]; x[i + d - 1] = x[i + 2 * d - 1]; x[i + 2 * d - 1] += t; ctx.op(2); }
            levels++;
            if (ctx.tracing()) ctx.trace.emit("down", "Down-sweep, stride " + d + ": swap and add to push prefix sums down.", "d", d, "arr", x.clone());
        }
        long[] ex = new long[len + 1];
        long run = 0;
        boolean ok = true;
        for (int i = 0; i < len; i++) { ex[i] = x[i]; if (x[i] != run) ok = false; run += in.ints[i]; }
        ex[len] = run;
        int w = Math.min(5, len), bestStart = 0;
        long best = -1;
        for (int i = 0; i + w <= len; i++) {
            long sum = ex[i + w] - ex[i];
            ctx.cmp();
            if (sum > best) { best = sum; bestStart = i; }
        }
        AlgorithmResult r = new AlgorithmResult();
        r.item("busiestWindowStartMinute", bestStart, "windowMinutes", w, "flows", best);
        r.headline = "Prefix sums computed in " + levels + " parallel levels; busiest " + w + "-minute window starts at minute " + bestStart + " with " + best + " flows";
        r.put("n", len).put("paddedTo", n).put("levels", levels).put("work", 2L * (n - 1)).put("total", run).put("busiestWindowStart", bestStart)
                .put("busiestWindowFlows", best).put("matchesSequential", ok).put("prefix", ex).check(run);
        return r;
    }
}
