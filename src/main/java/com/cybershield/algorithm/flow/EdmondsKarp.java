package com.cybershield.algorithm.flow;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Edmonds-Karp: Ford-Fulkerson that always augments along a BFS shortest path. */
public final class EdmondsKarp implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("edmondskarp", "Edmonds-Karp", "M4 · Network Flow", "CO4",
            "network-flow", InputKind.NETWORK, "O(V·E²)", "O(V + E)",
            "Repeatedly finds the shortest augmenting path with BFS in the residual graph and pushes the bottleneck capacity along it.",
            "Maximum communication flow / capacity analysis between the Internet and the SIEM.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        FlowNetwork g = FlowSupport.build(in);
        FlowSupport.emitGraph(in, g, ctx);
        int s = in.source, t = in.sink, total = 0, aug = 0;
        int[] pe = new int[g.n];
        while (true) {
            Arrays.fill(pe, -1);
            boolean[] seen = new boolean[g.n];
            ArrayDeque<Integer> q = new ArrayDeque<>();
            seen[s] = true;
            q.add(s);
            while (!q.isEmpty() && !seen[t]) {
                int u = q.poll();
                for (int e = g.head(u); e != -1; e = g.nxt(e)) {
                    ctx.cmp();
                    int v = g.to(e);
                    if (!seen[v] && g.cap(e) > 0) { seen[v] = true; pe[v] = e; q.add(v); ctx.op(); }
                }
            }
            if (!seen[t]) break;
            int b = Integer.MAX_VALUE;
            for (int v = t; v != s; v = g.to(pe[v] ^ 1)) b = Math.min(b, g.cap(pe[v]));
            List<Integer> path = FlowSupport.pathOf(g, pe, s, t);
            for (int v = t; v != s; v = g.to(pe[v] ^ 1)) g.push(pe[v], b);
            total += b;
            aug++;
            ctx.op(path.size());
            if (ctx.tracing())
                ctx.trace.emit("augment", "BFS shortest path #" + aug + " pushes " + b + " Gbps (bottleneck). Total flow = " + total + ".",
                        "path", path, "bottleneck", b, "total", total, "flows", g.flows());
        }
        return FlowSupport.finish(in, g, total, aug, ctx, "Edmonds-Karp");
    }
}
