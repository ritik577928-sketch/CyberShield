package com.cybershield.algorithm.string;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Knuth-Morris-Pratt: an LPS (failure) table lets the text pointer move forward only. */
public final class KmpSearch implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("kmp", "Knuth-Morris-Pratt", "M1 · String Algorithms", "CO1",
            "string-matching", InputKind.TEXT_PATTERNS, "O(n + m)", "O(m)",
            "Builds a longest-proper-prefix-suffix (LPS) table, then scans the text without ever moving the text pointer backwards.",
            "Detect known malware signatures in logs and packet payloads.");

    @Override public AlgorithmInfo info() { return INFO; }

    public static int[] buildLps(String p, ExecutionContext ctx) {
        int m = p.length();
        int[] lps = new int[m];
        int len = 0, i = 1;
        if (ctx.tracing())
            ctx.trace.emit("lps-init", "Build the LPS table: lps[i] = length of the longest proper prefix of the pattern that is also a suffix of p[0..i].",
                    "pattern", p, "i", 0, "len", 0, "lps", lps.clone());
        while (i < m) {
            ctx.cmp();
            ctx.op();
            if (p.charAt(i) == p.charAt(len)) {
                len++;
                lps[i] = len;
                if (ctx.tracing())
                    ctx.trace.emit("lps", "p[" + i + "]='" + p.charAt(i) + "' equals p[" + (len - 1) + "] → lps[" + i + "] = " + len + ".",
                            "i", i, "len", len, "eq", true, "lps", lps.clone());
                i++;
            } else if (len > 0) {
                int to = lps[len - 1];
                if (ctx.tracing())
                    ctx.trace.emit("lps-fallback", "p[" + i + "]='" + p.charAt(i) + "' ≠ p[" + len + "]='" + p.charAt(len) + "' → fall back len " + len + " → " + to + " (no progress on i).",
                            "i", i, "len", len, "to", to, "eq", false, "lps", lps.clone());
                len = to;
            } else {
                lps[i] = 0;
                if (ctx.tracing())
                    ctx.trace.emit("lps", "p[" + i + "]='" + p.charAt(i) + "' ≠ p[0] and len = 0 → lps[" + i + "] = 0.",
                            "i", i, "len", 0, "eq", false, "lps", lps.clone());
                i++;
            }
        }
        return lps;
    }

    public static List<Integer> search(String t, String p, ExecutionContext ctx) {
        List<Integer> pos = new ArrayList<>();
        int n = t.length(), m = p.length();
        if (m == 0 || m > n) return pos;
        if (ctx.tracing()) ctx.trace.emit("pattern", "Searching for \"" + p + "\" in the text.", "pattern", p, "n", n, "m", m);
        int[] lps = buildLps(p, ctx);
        int j = 0, i = 0;
        while (i < n) {
            ctx.cmp();
            ctx.op();
            boolean eq = t.charAt(i) == p.charAt(j);
            if (ctx.tracing())
                ctx.trace.emit("cmp", "Compare text[" + i + "]='" + t.charAt(i) + "' with pattern[" + j + "]='" + p.charAt(j) + "' → " + (eq ? "match" : "mismatch") + ".",
                        "ti", i, "pj", j, "eq", eq);
            if (eq) {
                i++;
                j++;
                if (j == m) {
                    pos.add(i - m);
                    if (ctx.tracing())
                        ctx.trace.emit("match", "Full match of \"" + p + "\" at index " + (i - m) + ".", "pos", i - m, "pattern", p);
                    int to = lps[j - 1];
                    if (ctx.tracing())
                        ctx.trace.emit("shift", "After a match, reuse the border: pattern index " + j + " → " + to + ".", "from", j, "to", to, "ti", i);
                    j = to;
                }
            } else if (j > 0) {
                int to = lps[j - 1];
                if (ctx.tracing())
                    ctx.trace.emit("shift", "Mismatch after " + j + " matched chars: slide the pattern so index " + j + " → lps[" + (j - 1) + "] = " + to + ".",
                            "from", j, "to", to, "ti", i);
                j = to;
            } else {
                i++;
            }
        }
        return pos;
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        Map<String, Integer> per = new LinkedHashMap<>();
        long total = 0;
        for (String p : in.patterns) {
            if (p.isEmpty()) continue;
            List<Integer> pos = search(in.text, p, ctx);
            per.merge(p, pos.size(), Integer::sum);
            total += pos.size();
            for (int s : pos) r.item("pattern", p, "start", s, "end", s + p.length());
        }
        r.headline = total + " signature match" + (total == 1 ? "" : "es") + " found with KMP";
        r.put("matches", total).put("perPattern", per).put("textLength", in.text.length()).put("patterns", in.patterns.size()).check(total);
        return r;
    }
}
