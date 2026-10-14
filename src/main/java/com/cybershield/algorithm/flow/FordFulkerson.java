package com.cybershield.algorithm.flow;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Ford-Fulkerson with depth-first augmenting paths (runtime depends on the flow value). */
public final class FordFulkerson implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("fordfulkerson", "Ford-Fulkerson (DFS)", "M4 · Network Flow", "CO4",
            "network-flow", InputKind.NETWORK, "O(E · f)", "O(V + E)",
            "Finds any augmenting path with DFS in the residual graph and pushes the bottleneck capacity; stops when none exists.",
            "Baseline maximum-flow method for network capacity analysis.");

    @Override public AlgorithmInfo info() { return INFO; }

    private static int dfs(FlowNetwork g, int u, int t, int f, boolean[] seen, int[] pe, ExecutionContext ctx) {
        if (u == t) return f;
        seen[u] = true;
        for (int e = g.head(u); e != -1; e = g.nxt(e)) {
            ctx.cmp();
            int v = g.to(e);
            if (!seen[v] && g.cap(e) > 0) {
                int got = dfs(g, v, t, Math.min(f, g.cap(e)), seen, pe, ctx);
                if (got > 0) { pe[v] = e; return got; }
            }
        }
        return 0;
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        FlowNetwork g = FlowSupport.build(in);
        FlowSupport.emitGraph(in, g, ctx);
        int s = in.source, t = in.sink, total = 0, aug = 0;
        int[] pe = new int[g.n];
        while (true) {
            Arrays.fill(pe, -1);
            int f = dfs(g, s, t, Integer.MAX_VALUE, new boolean[g.n], pe, ctx);
            if (f == 0) break;
            List<Integer> path = FlowSupport.pathOf(g, pe, s, t);
            for (int v = t; v != s; v = g.to(pe[v] ^ 1)) g.push(pe[v], f);
            total += f;
            aug++;
            ctx.op(path.size());
            if (ctx.tracing())
                ctx.trace.emit("augment", "DFS found an augmenting path (not necessarily shortest) pushing " + f + " Gbps. Total flow = " + total + ".",
                        "path", path, "bottleneck", f, "total", total, "flows", g.flows());
        }
        return FlowSupport.finish(in, g, total, aug, ctx, "Ford-Fulkerson");
    }
}
