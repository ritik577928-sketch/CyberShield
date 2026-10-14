package com.cybershield.algorithm.flow;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Dinic: BFS builds a level graph, then blocking flows are found with DFS and current-edge pointers. */
public final class Dinic implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("dinic", "Dinic's algorithm", "M4 · Network Flow", "CO4",
            "network-flow", InputKind.NETWORK, "O(V²·E)", "O(V + E)",
            "Alternates BFS (assign levels) and DFS (push a blocking flow along edges that go exactly one level deeper) until the sink is unreachable.",
            "Max-flow on larger communication networks where Edmonds-Karp becomes slow.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        FlowNetwork g = FlowSupport.build(in);
        FlowSupport.emitGraph(in, g, ctx);
        int n = g.n, s = in.source, t = in.sink, total = 0, aug = 0, phase = 0;
        int[] level = new int[n], it = new int[n], pe = new int[n];
        while (true) {
            Arrays.fill(level, -1);
            level[s] = 0;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(s);
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int e = g.head(u); e != -1; e = g.nxt(e)) {
                    ctx.cmp();
                    if (g.cap(e) > 0 && level[g.to(e)] < 0) { level[g.to(e)] = level[u] + 1; q.add(g.to(e)); ctx.op(); }
                }
            }
            if (level[t] < 0) break;
            phase++;
            if (ctx.tracing())
                ctx.trace.emit("level", "Phase " + phase + ": BFS assigns levels (distance from the source). The sink is at level " + level[t] + ".", "level", level.clone(), "phase", phase);
            for (int i = 0; i < n; i++) it[i] = g.head(i);
            int u = s, depth = 0;
            while (true) {
                if (u == t) {
                    int b = Integer.MAX_VALUE;
                    for (int i = 0; i < depth; i++) b = Math.min(b, g.cap(pe[i]));
                    List<Integer> path = new ArrayList<>();
                    path.add(s);
                    for (int i = 0; i < depth; i++) path.add(g.to(pe[i]));
                    for (int i = 0; i < depth; i++) g.push(pe[i], b);
                    total += b;
                    aug++;
                    ctx.op(depth);
                    if (ctx.tracing())
                        ctx.trace.emit("augment", "Blocking-flow DFS in phase " + phase + " pushes " + b + " Gbps. Total flow = " + total + ".",
                                "path", path, "bottleneck", b, "total", total, "flows", g.flows());
                    u = s;
                    depth = 0;
                    continue;
                }
                boolean advanced = false;
                for (; it[u] != -1; it[u] = g.nxt(it[u])) {
                    int e = it[u];
                    ctx.cmp();
                    if (g.cap(e) > 0 && level[g.to(e)] == level[u] + 1) { pe[depth++] = e; u = g.to(e); advanced = true; break; }
                }
                if (!advanced) {
                    if (u == s) break;
                    level[u] = -1;
                    depth--;
                    u = g.to(pe[depth] ^ 1);
                    it[u] = g.nxt(it[u]);
                }
            }
        }
        AlgorithmResult r = FlowSupport.finish(in, g, total, aug, ctx, "Dinic");
        r.put("phases", phase);
        return r;
    }
}
