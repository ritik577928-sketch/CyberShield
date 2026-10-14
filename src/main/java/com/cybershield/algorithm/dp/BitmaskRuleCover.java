package com.cybershield.algorithm.dp;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Bitmask DP over the set of threat categories: cheapest set of security rules that covers every category. */
public final class BitmaskRuleCover implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("bitmaskdp", "Bitmask DP - rule combination", "M3 · Dynamic Programming", "CO3",
            "dynamic-programming", InputKind.RULES, "O(2^k · r)", "O(2^k)",
            "dp[mask] = cheapest cost of rules whose union of covered categories is exactly mask. Transition: add one rule. Answer = dp[all categories].",
            "Choose the cheapest combination of security rules / controls that covers every threat category.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int k = in.names.size();
        if (k == 0 || k > 22) throw new IllegalArgumentException("Bitmask DP needs between 1 and 22 categories, got " + k);
        int full = (1 << k) - 1, size = 1 << k, INF = Integer.MAX_VALUE / 2;
        int[] dp = new int[size], prev = new int[size], via = new int[size];
        Arrays.fill(dp, INF);
        Arrays.fill(prev, -1);
        dp[0] = 0;
        for (int mask = 0; mask < size; mask++) {
            if (dp[mask] >= INF) continue;
            for (int ri = 0; ri < in.rules.size(); ri++) {
                AlgorithmInput.Rule rule = in.rules.get(ri);
                int nm = mask | rule.mask();
                ctx.cmp();
                ctx.op();
                if (nm == mask) continue;
                if (dp[mask] + rule.cost() < dp[nm]) {
                    dp[nm] = dp[mask] + rule.cost();
                    prev[nm] = mask;
                    via[nm] = ri;
                    if (ctx.tracing())
                        ctx.trace.emit("dp", "mask " + Integer.toBinaryString(mask) + " + rule \"" + rule.name() + "\" → mask " + Integer.toBinaryString(nm) + " costs " + dp[nm] + " (better).",
                                "mask", mask, "to", nm, "rule", rule.name(), "cost", dp[nm], "covered", Integer.bitCount(nm), "of", k);
                }
            }
        }
        AlgorithmResult r = new AlgorithmResult();
        if (dp[full] >= INF) {
            int covered = 0;
            for (AlgorithmInput.Rule rule : in.rules) covered |= rule.mask();
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < k; i++) if ((covered >> i & 1) == 0) missing.add(in.names.get(i));
            r.headline = "No rule combination covers: " + missing;
            r.put("feasible", false).put("uncoverable", missing).check(-1);
            return r;
        }
        List<String> chosen = new ArrayList<>();
        for (int m = full; m != 0; m = prev[m]) {
            AlgorithmInput.Rule rule = in.rules.get(via[m]);
            chosen.add(rule.name());
            r.item("rule", rule.name(), "cost", rule.cost());
        }
        // greedy baseline: best cost per newly covered category
        int gm = 0, gcost = 0;
        while (gm != full) {
            double bestRatio = Double.MAX_VALUE;
            AlgorithmInput.Rule pick = null;
            for (AlgorithmInput.Rule rule : in.rules) {
                int add = Integer.bitCount(rule.mask() & ~gm);
                if (add > 0 && (double) rule.cost() / add < bestRatio) { bestRatio = (double) rule.cost() / add; pick = rule; }
            }
            if (pick == null) break;
            gm |= pick.mask();
            gcost += pick.cost();
        }
        r.headline = "Cheapest full coverage costs " + dp[full] + " using " + chosen.size() + " rule(s)";
        r.put("feasible", true).put("optimalCost", dp[full]).put("rules", chosen).put("categories", k).put("states", size).put("greedyCost", gcost).check(dp[full]);
        return r;
    }
}
