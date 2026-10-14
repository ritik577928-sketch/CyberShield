package com.cybershield.algorithm.random;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Randomized QuickSort (Las Vegas): always correct, running time depends on random pivots. Sorts descending. */
public final class RandomizedQuickSort implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("quicksort", "Randomized QuickSort", "M6 · Randomized & Parallel", "CO6",
            "randomized-parallel", InputKind.SEVERITIES, "O(n log n) expected", "O(log n)",
            "Picks a random pivot and three-way partitions (greater | equal | smaller). Random pivots make the O(n²) worst case astronomically unlikely.",
            "Rank detected security events by severity.");

    @Override public AlgorithmInfo info() { return INFO; }

    static void sort(int[] a, int lo, int hi, Random rnd, ExecutionContext ctx) {
        if (lo >= hi) return;
        int pv = a[lo + rnd.nextInt(hi - lo + 1)];
        int lt = lo, gt = hi, i = lo;
        while (i <= gt) {
            ctx.cmp();
            if (a[i] > pv) { swap(a, lt++, i++); }
            else if (a[i] < pv) { swap(a, i, gt--); }
            else i++;
            ctx.op();
        }
        if (ctx.tracing() && a.length <= 64)
            ctx.trace.emit("partition", "Random pivot " + pv + " on [" + lo + ".." + hi + "]: larger values go left, equal stay in the middle [" + lt + ".." + gt + "], smaller go right.",
                    "lo", lo, "hi", hi, "pivot", pv, "lt", lt, "gt", gt, "arr", a.clone());
        sort(a, lo, lt - 1, rnd, ctx);
        sort(a, gt + 1, hi, rnd, ctx);
    }

    private static void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int[] a = in.ints.clone();
        long seed = (long) in.param("seed", System.nanoTime() & 0xffffff);
        if (ctx.tracing()) ctx.trace.emit("start", "Sort " + a.length + " event keys (severity-major) in descending order.", "arr", a.clone(), "seed", seed);
        sort(a, 0, a.length - 1, new Random(seed), ctx);
        int[] ref = in.ints.clone();
        Arrays.sort(ref);
        boolean ok = true;
        for (int i = 0; i < a.length; i++) if (a[i] != ref[a.length - 1 - i]) ok = false;
        AlgorithmResult r = new AlgorithmResult();
        int top = Math.min(10, a.length);
        for (int i = 0; i < top; i++) {
            int ord = 999_999 - a[i] % 1_000_000;
            r.item("rank", i + 1, "severity", a[i] / 1_000_000, "event", ord >= 0 && ord < in.names.size() ? in.names.get(ord) : "#" + ord);
        }
        double expected = a.length < 2 ? 0 : 1.39 * a.length * Math.log(a.length) / Math.log(2);
        r.headline = "Ranked " + a.length + " events; " + ctx.comparisons() + " comparisons (expected ≈ " + Math.round(expected) + ")";
        r.put("n", a.length).put("seed", seed).put("sortedCorrectly", ok).put("expectedComparisons", Math.round(expected)).check(a.length);
        return r;
    }
}
