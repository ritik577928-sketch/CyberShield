package com.cybershield.algorithm.flow;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Minimum cut via the max-flow min-cut theorem: critical network links whose loss disconnects source from sink. */
public final class MinCut implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("mincut", "Min-Cut (max-flow min-cut)", "M4 · Network Flow", "CO4",
            "network-flow", InputKind.NETWORK, "O(V·E²)", "O(V + E)",
            "After max flow, the nodes still reachable from the source in the residual graph form one side of a minimum cut; saturated links crossing it are the bottleneck.",
            "Identify the critical network connections an attacker (or outage) would target.");

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
                    if (!seen[g.to(e)] && g.cap(e) > 0) { seen[g.to(e)] = true; pe[g.to(e)] = e; q.add(g.to(e)); }
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
                ctx.trace.emit("augment", "Saturate path #" + aug + " (" + b + " Gbps) on the way to the maximum flow.", "path", path, "bottleneck", b, "total", total, "flows", g.flows());
        }
        return FlowSupport.finish(in, g, total, aug, ctx, "Min-Cut");
    }
}
