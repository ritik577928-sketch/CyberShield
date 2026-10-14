package com.cybershield.algorithm.string;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Z-function on pattern + separator + text: z[i] = length of the longest common prefix with the whole string. */
public final class ZFunctionSearch implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("z", "Z-Function", "M1 · String Algorithms", "CO1",
            "string-matching", InputKind.TEXT_PATTERNS, "O(n + m)", "O(n + m)",
            "Computes, for every position, how long the substring starting there matches the prefix; a value ≥ |pattern| is a match. Reuses the rightmost Z-box.",
            "Identify repeated attack patterns and periodic payloads.");

    @Override public AlgorithmInfo info() { return INFO; }

    public static int[] zArray(String s, ExecutionContext ctx) {
        int n = s.length();
        int[] z = new int[n];
        int l = 0, r = 0;
        for (int i = 1; i < n; i++) {
            boolean reused = i < r;
            if (reused) z[i] = Math.min(r - i, z[i - l]);
            while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) { z[i]++; ctx.cmp(); }
            ctx.cmp();
            ctx.op();
            if (i + z[i] > r) { l = i; r = i + z[i]; }
            if (ctx.tracing())
                ctx.trace.emit("z", "i=" + i + ": " + (reused ? "start from the Z-box copy" : "no Z-box covers i") + ", extend → z[" + i + "] = " + z[i] + "; Z-box is now [" + l + ", " + r + ").",
                        "i", i, "l", l, "r", r, "zi", z[i], "reused", reused, "z", z.clone());
        }
        if (n > 0) z[0] = n;
        return z;
    }

    public static List<Integer> search(String t, String p, ExecutionContext ctx) {
        List<Integer> pos = new ArrayList<>();
        int m = p.length();
        if (m == 0 || m > t.length()) return pos;
        String s = p + '\u0001' + t;
        if (ctx.tracing()) ctx.trace.emit("start", "Concatenate pattern + separator + text and compute the Z array.", "s", s, "m", m, "pattern", p);
        int[] z = zArray(s, ctx);
        for (int i = m + 1; i < s.length(); i++) {
            if (z[i] >= m) {
                pos.add(i - m - 1);
                if (ctx.tracing())
                    ctx.trace.emit("match", "z[" + i + "] = " + z[i] + " ≥ " + m + " → pattern found at text index " + (i - m - 1) + ".", "pos", i - m - 1, "i", i, "pattern", p);
            }
        }
        return pos;
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        Map<String, Integer> per = new LinkedHashMap<>();
        List<String> repeated = new ArrayList<>();
        long total = 0;
        for (String p : in.patterns) {
            if (p.isEmpty()) continue;
            List<Integer> pos = search(in.text, p, ctx);
            per.merge(p, pos.size(), Integer::sum);
            total += pos.size();
            if (pos.size() >= 3) repeated.add(p);
            for (int s : pos) r.item("pattern", p, "start", s, "end", s + p.length());
        }
        r.headline = total + " matches; " + repeated.size() + " pattern(s) repeat 3+ times";
        r.put("matches", total).put("perPattern", per).put("repeatedPatterns", repeated).put("textLength", in.text.length()).check(total);
        return r;
    }
}
