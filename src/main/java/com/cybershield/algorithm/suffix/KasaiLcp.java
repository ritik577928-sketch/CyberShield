package com.cybershield.algorithm.suffix;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Kasai's linear-time LCP array construction on top of a suffix array (built with SA-IS). */
public final class KasaiLcp implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("kasai", "LCP array (Kasai)", "M2 · Suffix Structures", "CO2",
            "suffix-structures", InputKind.TEXT, "O(n)", "O(n)",
            "Walks suffixes in text order and reuses the previous LCP value minus one, so the total character comparisons stay linear.",
            "Detect repeated attack phrases: a large LCP between neighbouring suffixes means a repeated payload.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        ExecutionContext quiet = ctx.quiet();
        int[] sa = SaIs.build(in.text, quiet);
        ctx.absorb(quiet);
        if (ctx.tracing()) ctx.trace.emit("start", "Suffix array ready. Now compute LCP[i] = common prefix of neighbouring suffixes with Kasai.", "s", in.text, "sa", sa.clone());
        int[] lcp = SuffixAnalysis.kasai(in.text, sa, ctx);
        SuffixAnalysis.summarize(in.text, sa, lcp, r, in.intParam("minLen", 8));
        return r;
    }
}
