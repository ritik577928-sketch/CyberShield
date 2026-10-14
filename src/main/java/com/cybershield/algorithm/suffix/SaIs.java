package com.cybershield.algorithm.suffix;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** SA-IS: linear-time suffix array by induced sorting of LMS substrings (L/S types, recursion on the reduced string). */
public final class SaIs implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("sais", "SA-IS (induced sorting)", "M2 · Suffix Structures", "CO2",
            "suffix-structures", InputKind.TEXT, "O(n)", "O(n)",
            "Classifies suffixes as S/L type, sorts the LMS substrings, recurses on the reduced string and induces the final order in linear time.",
            "Index very large logs in linear time where prefix doubling would be too slow.");

    @Override public AlgorithmInfo info() { return INFO; }

    public static int[] build(String text, ExecutionContext ctx) {
        int n = text.length(), upper = 0;
        int[] s = new int[n];
        for (int i = 0; i < n; i++) { s[i] = text.charAt(i); upper = Math.max(upper, s[i]); }
        return sais(s, upper, ctx, 0);
    }

    private static int[] sais(int[] s, int upper, ExecutionContext ctx, int level) {
        int n = s.length;
        if (n == 0) return new int[0];
        if (n == 1) return new int[]{0};
        if (n == 2) { ctx.cmp(); return s[0] < s[1] ? new int[]{0, 1} : new int[]{1, 0}; }
        int[] sa = new int[n];
        boolean[] ls = new boolean[n];
        for (int i = n - 2; i >= 0; i--) { ls[i] = (s[i] == s[i + 1]) ? ls[i + 1] : (s[i] < s[i + 1]); ctx.op(); }
        int[] sumL = new int[upper + 1], sumS = new int[upper + 1];
        for (int i = 0; i < n; i++) { if (!ls[i]) sumS[s[i]]++; else sumL[s[i] + 1]++; }
        for (int i = 0; i <= upper; i++) { sumS[i] += sumL[i]; if (i < upper) sumL[i + 1] += sumS[i]; }
        int[] lmsMap = new int[n + 1];
        Arrays.fill(lmsMap, -1);
        int m = 0;
        for (int i = 1; i < n; i++) if (!ls[i - 1] && ls[i]) lmsMap[i] = m++;
        int[] lms = new int[m];
        int k0 = 0;
        for (int i = 1; i < n; i++) if (!ls[i - 1] && ls[i]) lms[k0++] = i;
        boolean show = level == 0 && n <= 160 && ctx.tracing();
        if (show) {
            StringBuilder types = new StringBuilder();
            for (int i = 0; i < n; i++) types.append(ls[i] ? 'S' : 'L');
            ctx.trace.emit("classify", "Classify every suffix: S-type if it is smaller than the next suffix, L-type otherwise. LMS = an S right after an L (" + m + " found).",
                    "types", types.toString(), "lms", lms.clone(), "n", n);
        }
        induce(lms, s, upper, sa, ls, sumL, sumS, ctx);
        if (show) ctx.trace.emit("induce", "Induced sort #1: place LMS suffixes at bucket ends, induce L then S types → LMS substrings are now sorted.", "sa", sa.clone(), "stage", 1);
        if (m > 0) {
            int[] sorted = new int[m];
            int k = 0;
            for (int v : sa) if (lmsMap[v] != -1) sorted[k++] = v;
            int[] rec = new int[m];
            int recUpper = 0;
            rec[lmsMap[sorted[0]]] = 0;
            for (int i = 1; i < m; i++) {
                int l = sorted[i - 1], r = sorted[i];
                int endL = (lmsMap[l] + 1 < m) ? lms[lmsMap[l] + 1] : n;
                int endR = (lmsMap[r] + 1 < m) ? lms[lmsMap[r] + 1] : n;
                boolean same = true;
                if (endL - l != endR - r) same = false;
                else {
                    while (l < endL) { ctx.cmp(); if (s[l] != s[r]) break; l++; r++; }
                    if (l == n || r == n || s[l] != s[r]) same = false;
                }
                if (!same) recUpper++;
                rec[lmsMap[sorted[i]]] = recUpper;
            }
            if (show) ctx.trace.emit("rename", "Name each LMS substring by its rank; the reduced string has " + m + " characters and " + (recUpper + 1) + " distinct names" + (recUpper + 1 == m ? " (all unique - no recursion needed)." : " - recurse."),
                    "reduced", rec.clone(), "names", recUpper + 1);
            int[] recSa = sais(rec, recUpper, ctx, level + 1);
            for (int i = 0; i < m; i++) sorted[i] = lms[recSa[i]];
            induce(sorted, s, upper, sa, ls, sumL, sumS, ctx);
            if (show) ctx.trace.emit("induce", "Induced sort #2: with LMS suffixes in their true order, induce L and S suffixes → final suffix array.", "sa", sa.clone(), "stage", 2);
        }
        return sa;
    }

    private static void induce(int[] lms, int[] s, int upper, int[] sa, boolean[] ls, int[] sumL, int[] sumS, ExecutionContext ctx) {
        int n = s.length;
        Arrays.fill(sa, -1);
        int[] buf = new int[upper + 1];
        System.arraycopy(sumS, 0, buf, 0, upper + 1);
        for (int d : lms) { if (d == n) continue; sa[buf[s[d]]++] = d; }
        System.arraycopy(sumL, 0, buf, 0, upper + 1);
        sa[buf[s[n - 1]]++] = n - 1;
        for (int i = 0; i < n; i++) { int v = sa[i]; if (v >= 1 && !ls[v - 1]) sa[buf[s[v - 1]]++] = v - 1; }
        System.arraycopy(sumL, 0, buf, 0, upper + 1);
        for (int i = n - 1; i >= 0; i--) { int v = sa[i]; if (v >= 1 && ls[v - 1]) sa[--buf[s[v - 1] + 1]] = v - 1; }
        ctx.op(3L * n);
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        String text = in.text;
        int[] sa = build(text, ctx);
        ExecutionContext quiet = ctx.quiet();
        int[] lcp = SuffixAnalysis.kasai(text, sa, quiet);
        ctx.absorb(quiet);
        SuffixAnalysis.summarize(text, sa, lcp, r, in.intParam("minLen", 8));
        r.put("saPreview", Arrays.copyOf(sa, Math.min(16, sa.length)));
        return r;
    }
}
