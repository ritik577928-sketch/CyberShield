package com.cybershield.algorithm.random;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;
import java.util.stream.IntStream;

/** Tree-shaped parallel reduction (sum, sum of squares, max) with Brent's theorem work/depth accounting. */
public final class ParallelReduce implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("parallelreduce", "Parallel Reduce + Brent's theorem", "M6 · Randomized & Parallel", "CO6",
            "randomized-parallel", InputKind.SERIES, "work O(n), depth O(log n)", "O(n)",
            "Combines pairs level by level (each level runs on the parallel stream). Brent's theorem bounds the time on p processors by W/p + D.",
            "Parallel security-data processing: mean / deviation / peak of traffic to flag anomalous minutes.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int n = in.ints.length;
        if (n == 0) throw new IllegalArgumentException("Series is empty");
        long[][] cur = new long[n][];
        for (int i = 0; i < n; i++) cur[i] = new long[]{in.ints[i], (long) in.ints[i] * in.ints[i], in.ints[i]};
        long work = 0;
        int depth = 0, len = n;
        while (len > 1) {
            int half = (len + 1) / 2, l = len;
            long[][] c = cur, nxt = new long[half][];
            IntStream.range(0, half).parallel().forEach(i -> {
                int a = 2 * i, b = a + 1;
                nxt[i] = b < l ? new long[]{c[a][0] + c[b][0], c[a][1] + c[b][1], Math.max(c[a][2], c[b][2])} : c[a];
            });
            work += l / 2;
            depth++;
            ctx.op(l / 2);
            ctx.cmp(l / 2);
            cur = nxt;
            len = half;
            if (ctx.tracing()) {
                long[] sums = new long[len];
                for (int i = 0; i < len; i++) sums[i] = cur[i][0];
                ctx.trace.emit("level", "Level " + depth + ": " + (l / 2) + " pair(s) combined in parallel → " + len + " partial result(s) left.", "level", depth, "width", l / 2, "arr", sums);
            }
        }
        long sum = cur[0][0], sq = cur[0][1], max = cur[0][2];
        double mean = (double) sum / n, sd = Math.sqrt(Math.max(0, (double) sq / n - mean * mean));
        int p = Runtime.getRuntime().availableProcessors();
        double brent = (double) work / p + depth;
        List<Integer> spikes = new ArrayList<>();
        for (int i = 0; i < n; i++) if (sd > 0 && (in.ints[i] - mean) / sd > in.param("zThreshold", 3.0)) spikes.add(i);
        AlgorithmResult r = new AlgorithmResult();
        for (int s : spikes) r.item("minute", s, "flows", in.ints[s], "zScore", Math.round((in.ints[s] - mean) / sd * 100) / 100.0);
        r.headline = "Total " + sum + ", peak " + max + "/min, " + spikes.size() + " anomalous minute(s); Brent bound T_" + p + " ≤ " + String.format("%.1f", brent) + " steps";
        r.put("n", n).put("sum", sum).put("max", max).put("mean", mean).put("stdDev", sd).put("work", work).put("depth", depth).put("processors", p)
                .put("brentBound", brent).put("spikeMinutes", spikes).put("workIsNMinus1", work == n - 1).check(sum);
        return r;
    }
}
