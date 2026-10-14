package com.cybershield.algorithm.flow;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Shared helpers for the flow algorithms: network construction, graph trace event, result assembly. */
final class FlowSupport {
    private FlowSupport() {}

    static FlowNetwork build(AlgorithmInput in) {
        if (in.edges.length == 0) throw new IllegalArgumentException("The network has no edges");
        int n = in.names.size();
        if (in.source < 0 || in.source >= n || in.sink < 0 || in.sink >= n || in.source == in.sink)
            throw new IllegalArgumentException("Invalid source/sink for a network of " + n + " nodes");
        FlowNetwork g = new FlowNetwork(n, in.edges.length);
        for (int[] e : in.edges) g.add(e[0], e[1], e[2]);
        return g;
    }

    static void emitGraph(AlgorithmInput in, FlowNetwork g, ExecutionContext ctx) {
        if (!ctx.tracing()) return;
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < g.edgeCount(); i++) edges.add(new int[]{g.from(i), g.target(i), g.capacity(i)});
        ctx.trace.emit("graph", "Network: " + in.names.size() + " nodes, " + g.edgeCount() + " links. Source = " + in.names.get(in.source) + ", sink = " + in.names.get(in.sink) + ".",
                "nodes", in.names, "edges", edges, "source", in.source, "sink", in.sink);
    }

    static AlgorithmResult finish(AlgorithmInput in, FlowNetwork g, int total, int augmentations, ExecutionContext ctx, String algo) {
        List<Integer> cut = g.cutEdges(in.source);
        long cutCap = 0;
        List<String> cutNames = new ArrayList<>();
        AlgorithmResult r = new AlgorithmResult();
        for (int i : cut) {
            cutCap += g.capacity(i);
            String label = in.names.get(g.from(i)) + " → " + in.names.get(g.target(i));
            cutNames.add(label);
            r.item("link", label, "capacity", g.capacity(i), "role", "critical (min-cut)");
        }
        boolean[] side = g.reachable(in.source);
        if (ctx.tracing())
            ctx.trace.emit("cut", "No augmenting path left: max flow = " + total + ". The saturated links separating the reachable side from the sink form the minimum cut (capacity " + cutCap + ").",
                    "flows", g.flows(), "cut", cut, "side", side, "total", total);
        r.headline = "Max flow " + total + " Gbps via " + augmentations + " augmenting path(s); min-cut capacity " + cutCap;
        r.put("maxFlow", total).put("augmentations", augmentations).put("minCutCapacity", cutCap).put("criticalLinks", cutNames)
                .put("cutEqualsFlow", cutCap == total).put("algorithm", algo).check(total);
        return r;
    }

    static List<Integer> pathOf(FlowNetwork g, int[] parentEdge, int s, int t) {
        LinkedList<Integer> p = new LinkedList<>();
        for (int v = t; v != s; v = g.to(parentEdge[v] ^ 1)) p.addFirst(v);
        p.addFirst(s);
        return p;
    }
}
