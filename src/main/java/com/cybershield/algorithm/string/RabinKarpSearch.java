package com.cybershield.algorithm.string;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Rabin-Karp: rolling polynomial hash; characters are compared only when hashes are equal. */
public final class RabinKarpSearch implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("rabinkarp", "Rabin-Karp", "M1 · String Algorithms", "CO1",
            "string-matching", InputKind.TEXT_PATTERNS, "O(n + m) avg, O(nm) worst", "O(1)",
            "Slides a window over the text updating a rolling hash in O(1); a full comparison happens only on hash equality (collisions are counted).",
            "Hash-based signature search; easy to extend to many equal-length signatures.");

    static final long MOD = 1_000_000_007L, BASE = 131;

    @Override public AlgorithmInfo info() { return INFO; }

    public static List<Integer> search(String t, String p, ExecutionContext ctx) {
        List<Integer> pos = new ArrayList<>();
        int n = t.length(), m = p.length();
        if (m == 0 || m > n) return pos;
        long ph = 0, wh = 0, pow = 1;
        for (int i = 0; i < m; i++) {
            ph = (ph * BASE + p.charAt(i)) % MOD;
            wh = (wh * BASE + t.charAt(i)) % MOD;
            if (i > 0) pow = pow * BASE % MOD;
        }
        if (ctx.tracing()) ctx.trace.emit("pattern", "Pattern hash = " + ph + ". Slide a window of " + m + " characters over the text.", "pattern", p, "m", m, "ph", ph);
        for (int i = 0; i + m <= n; i++) {
            ctx.cmp();
            ctx.op();
            boolean hashEq = ph == wh, real = false;
            if (hashEq) {
                real = true;
                for (int k = 0; k < m; k++) {
                    ctx.cmp();
                    if (t.charAt(i + k) != p.charAt(k)) { real = false; break; }
                }
                if (!real) ctx.stat("collisions", 1);
            }
            if (ctx.tracing())
                ctx.trace.emit("window", "Window [" + i + "," + (i + m) + ") hash " + wh + (hashEq ? " = " : " ≠ ") + "pattern hash" + (hashEq ? (real ? " → verified match." : " → spurious hash collision, characters differ.") : "."),
                        "i", i, "wh", wh, "ph", ph, "eq", hashEq, "real", real, "collision", hashEq && !real);
            if (real) pos.add(i);
            if (i + m < n) {
                wh = (wh - t.charAt(i) * pow % MOD + MOD) % MOD;
                wh = (wh * BASE + t.charAt(i + m)) % MOD;
                ctx.op(2);
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
        r.headline = total + " matches; " + ctx.stat("collisions") + " hash collision(s) filtered out";
        r.put("matches", total).put("perPattern", per).put("hashCollisions", ctx.stat("collisions")).put("textLength", in.text.length()).check(total);
        return r;
    }
}
