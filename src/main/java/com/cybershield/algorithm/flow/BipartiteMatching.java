package com.cybershield.algorithm.flow;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Kuhn's augmenting-path bipartite matching: assign security analysts to alerts. */
public final class BipartiteMatching implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("bipartite", "Bipartite Matching (Kuhn)", "M4 · Network Flow", "CO4",
            "network-flow", InputKind.BIPARTITE, "O(V·E)", "O(V + E)",
            "For each analyst try to take a free alert; if it is taken, recursively try to re-route the analyst holding it (augmenting path).",
            "Assign security analysts to alerts so the maximum number of alerts gets an owner.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int L = in.leftCount, R = in.rightCount;
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < L; i++) adj.add(new ArrayList<>());
        for (int[] e : in.edges) adj.get(e[0]).add(e[1] - L);
        int[] matchR = new int[R];
        Arrays.fill(matchR, -1);
        int size = 0;
        for (int u = 0; u < L; u++) {
            if (ctx.tracing()) ctx.trace.emit("start-left", "Find an alert for " + in.names.get(u) + ".", "u", u);
            if (try_(u, adj, matchR, new boolean[R], in, L, ctx)) size++;
        }
        AlgorithmResult r = new AlgorithmResult();
        Set<Integer> matchedAlerts = new HashSet<>();
        for (int v = 0; v < R; v++) {
            if (matchR[v] >= 0) { matchedAlerts.add(v); r.item("analyst", in.names.get(matchR[v]), "alert", in.names.get(L + v)); }
        }
        List<String> unmatched = new ArrayList<>();
        for (int v = 0; v < R; v++) if (!matchedAlerts.contains(v)) unmatched.add(in.names.get(L + v));
        // cross-check with max flow on the equivalent network
        FlowNetwork g = new FlowNetwork(L + R + 2, in.edges.length + L + R);
        for (int u = 0; u < L; u++) g.add(L + R, u, 1);
        for (int[] e : in.edges) g.add(e[0], e[1], 1);
        for (int v = 0; v < R; v++) g.add(L + v, L + R + 1, 1);
        int flow = maxFlow(g, L + R, L + R + 1);
        r.headline = size + " of " + R + " alerts assigned to " + L + " analysts";
        r.put("matched", size).put("analysts", L).put("alerts", R).put("unassignedAlerts", unmatched).put("flowCheck", flow).put("matchesFlow", flow == size).check(size);
        return r;
    }

    private static int maxFlow(FlowNetwork g, int s, int t) {
        int total = 0;
        int[] pe = new int[g.n];
        while (true) {
            Arrays.fill(pe, -1);
            boolean[] seen = new boolean[g.n];
            ArrayDeque<Integer> q = new ArrayDeque<>();
            seen[s] = true;
            q.add(s);
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int e = g.head(u); e != -1; e = g.nxt(e)) if (!seen[g.to(e)] && g.cap(e) > 0) { seen[g.to(e)] = true; pe[g.to(e)] = e; q.add(g.to(e)); }
            }
            if (!seen[t]) return total;
            int b = Integer.MAX_VALUE;
            for (int v = t; v != s; v = g.to(pe[v] ^ 1)) b = Math.min(b, g.cap(pe[v]));
            for (int v = t; v != s; v = g.to(pe[v] ^ 1)) g.push(pe[v], b);
            total += b;
        }
    }

    private boolean try_(int u, List<List<Integer>> adj, int[] matchR, boolean[] used, AlgorithmInput in, int L, ExecutionContext ctx) {
        for (int v : adj.get(u)) {
            ctx.cmp();
            if (used[v]) continue;
            used[v] = true;
            boolean free = matchR[v] < 0;
            if (ctx.tracing())
                ctx.trace.emit("try", in.names.get(u) + " tries " + in.names.get(L + v) + (free ? " (free)." : " (held by " + in.names.get(matchR[v]) + " - try to re-route them)."),
                        "u", u, "v", v, "free", free, "holder", matchR[v]);
            if (free || try_(matchR[v], adj, matchR, used, in, L, ctx)) {
                matchR[v] = u;
                ctx.op();
                if (ctx.tracing()) ctx.trace.emit("assign", in.names.get(L + v) + " is now assigned to " + in.names.get(u) + ".", "u", u, "v", v);
                return true;
            }
        }
        return false;
    }
}
