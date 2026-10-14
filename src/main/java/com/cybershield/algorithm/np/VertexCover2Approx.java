package com.cybershield.algorithm.np;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** 2-approximation for Vertex Cover via a maximal matching, compared with the exact optimum. */
public final class VertexCover2Approx implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("vertexcover", "Vertex-Cover 2-Approximation", "M5 · NP-Completeness", "CO5",
            "np-completeness", InputKind.GRAPH, "O(V + E)", "O(V)",
            "Picks any uncovered edge and adds BOTH endpoints to the cover. The picked edges form a matching, so the cover is at most twice the optimum.",
            "Choose a small set of services/hosts to monitor so that every security dependency link is covered.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int n = in.names.size();
        boolean[] cov = new boolean[n];
        int pairs = 0;
        AlgorithmResult r = new AlgorithmResult();
        if (ctx.tracing()) ctx.trace.emit("graph", "Dependency graph: " + n + " services, " + in.edges.length + " links.", "nodes", in.names, "edges", in.edges);
        for (int[] e : in.edges) {
            ctx.cmp();
            if (!cov[e[0]] && !cov[e[1]]) {
                cov[e[0]] = cov[e[1]] = true;
                pairs++;
                ctx.op(2);
                if (ctx.tracing()) {
                    List<Integer> cur = new ArrayList<>();
                    for (int i = 0; i < n; i++) if (cov[i]) cur.add(i);
                    ctx.trace.emit("pick-edge", "Edge " + in.names.get(e[0]) + " – " + in.names.get(e[1]) + " is uncovered → add BOTH endpoints.", "u", e[0], "v", e[1], "cover", cur);
                }
            }
        }
        List<String> cover = new ArrayList<>();
        for (int i = 0; i < n; i++) if (cov[i]) { cover.add(in.names.get(i)); r.item("service", in.names.get(i)); }
        boolean valid = true;
        for (int[] e : in.edges) if (!cov[e[0]] && !cov[e[1]]) valid = false;
        int opt = n <= 22 ? exact(n, in.edges, ctx) : -1;
        r.headline = "Cover of " + cover.size() + " services" + (opt > 0 ? " (optimum " + opt + ", ratio " + String.format("%.2f", (double) cover.size() / opt) + ")" : "");
        r.put("coverSize", cover.size()).put("cover", cover).put("matchingSize", pairs).put("optimum", opt)
                .put("ratio", opt > 0 ? (double) cover.size() / opt : -1).put("validCover", valid).put("withinTwoApprox", opt < 0 || cover.size() <= 2 * opt).check(cover.size());
        return r;
    }

    public static int exact(int n, int[][] edges, ExecutionContext ctx) {
        int best = n;
        for (int mask = 0; mask < (1 << n); mask++) {
            int c = Integer.bitCount(mask);
            if (c >= best) continue;
            boolean ok = true;
            for (int[] e : edges) { ctx.cmp(); if ((mask >> e[0] & 1) == 0 && (mask >> e[1] & 1) == 0) { ok = false; break; } }
            if (ok) best = c;
        }
        return best;
    }
}
