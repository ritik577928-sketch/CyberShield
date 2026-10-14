package com.cybershield.algorithm.suffix;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Prefix-doubling suffix array: sort suffixes by 1, 2, 4, 8... characters using counting sort on equivalence classes. */
public final class SuffixArrayDoubling implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("suffixarray", "Suffix Array (prefix doubling)", "M2 · Suffix Structures", "CO2",
            "suffix-structures", InputKind.TEXT, "O(n log n)", "O(n)",
            "Sorts all suffixes by their first 2^k characters, doubling k each round with radix/counting sort until every suffix has a unique rank.",
            "Index large security logs so any substring query becomes a binary search.");

    @Override public AlgorithmInfo info() { return INFO; }

    /** Returns the suffix array of s0 (length n). */
    public static int[] build(String s0, ExecutionContext ctx) {
        String s = s0 + '\0';
        int n = s.length();
        int[] p = new int[n], c = new int[n], pn = new int[n], cn = new int[n];
        int[] cnt = new int[Math.max(65536, n)];
        for (int i = 0; i < n; i++) cnt[s.charAt(i)]++;
        for (int i = 1; i < 65536; i++) cnt[i] += cnt[i - 1];
        for (int i = n - 1; i >= 0; i--) p[--cnt[s.charAt(i)]] = i;
        c[p[0]] = 0;
        int classes = 1;
        for (int i = 1; i < n; i++) {
            if (s.charAt(p[i]) != s.charAt(p[i - 1])) classes++;
            c[p[i]] = classes - 1;
        }
        ctx.op(2L * n);
        if (ctx.tracing())
            ctx.trace.emit("start", "Append a sentinel '$' smaller than every character, so all suffixes are distinct.", "s", s0, "n", n);
        if (ctx.tracing())
            ctx.trace.emit("round", "Round 0: suffixes sorted by their first character → " + classes + " distinct class(es).", "k", 1, "order", p.clone(), "cls", c.clone(), "classes", classes);
        for (int h = 0; (1 << h) < n && classes < n; h++) {
            int k = 1 << h;
            for (int i = 0; i < n; i++) {
                pn[i] = p[i] - k;
                if (pn[i] < 0) pn[i] += n;
            }
            Arrays.fill(cnt, 0, classes, 0);
            for (int i = 0; i < n; i++) cnt[c[pn[i]]]++;
            for (int i = 1; i < classes; i++) cnt[i] += cnt[i - 1];
            for (int i = n - 1; i >= 0; i--) p[--cnt[c[pn[i]]]] = pn[i];
            cn[p[0]] = 0;
            classes = 1;
            for (int i = 1; i < n; i++) {
                ctx.cmp(2);
                if (c[p[i]] != c[p[i - 1]] || c[(p[i] + k) % n] != c[(p[i - 1] + k) % n]) classes++;
                cn[p[i]] = classes - 1;
            }
            int[] t = c; c = cn; cn = t;
            ctx.op(4L * n);
            if (ctx.tracing())
                ctx.trace.emit("round", "Round " + (h + 1) + ": sort by the first " + (2 * k) + " characters using (rank of first half, rank of second half) → " + classes + " class(es)" + (classes == n ? " - all suffixes are now unique." : "."),
                        "k", 2 * k, "order", p.clone(), "cls", c.clone(), "classes", classes);
            ctx.stat("rounds", 1);
        }
        return Arrays.copyOfRange(p, 1, n);
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        String text = in.text.replace("\0", "");
        int[] sa = build(text, ctx);
        ExecutionContext quiet = ctx.quiet();
        int[] lcp = SuffixAnalysis.kasai(text, sa, quiet);
        ctx.absorb(quiet);
        SuffixAnalysis.summarize(text, sa, lcp, r, in.intParam("minLen", 8));
        r.put("rounds", ctx.stat("rounds")).put("saPreview", Arrays.copyOf(sa, Math.min(16, sa.length)));
        return r;
    }
}
