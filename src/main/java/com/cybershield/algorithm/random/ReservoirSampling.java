package com.cybershield.algorithm.random;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Algorithm R: keep a uniform random sample of k items from a stream of unknown length in O(k) memory. */
public final class ReservoirSampling implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("reservoir", "Reservoir Sampling", "M6 · Randomized & Parallel", "CO6",
            "randomized-parallel", InputKind.STREAM, "O(n)", "O(k)",
            "Fill the reservoir with the first k events; the i-th later event replaces a random slot with probability k/i, so every event ends up kept with probability k/n.",
            "Sample a continuous security-event stream without storing it, then estimate the threat rate.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int k = Math.max(1, in.intParam("k", 10));
        long seed = (long) in.param("seed", System.nanoTime() & 0xffffff);
        Random rnd = new Random(seed);
        int[] res = new int[Math.min(k, in.lines.size())];
        int filled = 0;
        for (int i = 0; i < in.lines.size(); i++) {
            int replaced = -1;
            if (i < k) res[filled++] = i;
            else {
                int j = rnd.nextInt(i + 1);
                ctx.cmp();
                if (j < k) { res[j] = i; replaced = j; }
            }
            ctx.op();
            if (ctx.tracing())
                ctx.trace.emit("item", i < k ? "Event " + i + ": reservoir not full yet → keep it." : "Event " + i + ": keep with probability " + k + "/" + (i + 1) + (replaced >= 0 ? " → YES, replaces slot " + replaced + "." : " → no, discard."),
                        "i", i, "replaced", replaced, "reservoir", res.clone(), "filled", filled);
        }
        AlgorithmResult r = new AlgorithmResult();
        int flagged = 0;
        for (int idx : res) {
            boolean hit = isFlagged(in.lines.get(idx), in.patterns);
            if (hit) flagged++;
            r.item("index", idx, "line", in.lines.get(idx), "flagged", hit);
        }
        int popFlagged = 0;
        for (String l : in.lines) if (isFlagged(l, in.patterns)) popFlagged++;
        double est = res.length == 0 ? 0 : 100.0 * flagged / res.length, truth = in.lines.isEmpty() ? 0 : 100.0 * popFlagged / in.lines.size();
        r.headline = "Sampled " + res.length + " of " + in.lines.size() + " events; estimated threat rate " + String.format("%.1f", est) + "% (true " + String.format("%.1f", truth) + "%)";
        r.put("stream", in.lines.size()).put("k", k).put("seed", seed).put("estimatedThreatPercent", est).put("actualThreatPercent", truth).check(res.length);
        return r;
    }

    private static boolean isFlagged(String line, List<String> patterns) {
        for (String p : patterns) if (!p.isEmpty() && line.contains(p)) return true;
        return false;
    }
}
