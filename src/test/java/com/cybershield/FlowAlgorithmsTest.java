package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.algorithm.*;
import com.cybershield.algorithm.flow.*;
import com.cybershield.dataset.Dataset;
import com.cybershield.dataset.Models.Topology;
import java.util.*;
import org.junit.jupiter.api.Test;

class FlowAlgorithmsTest {
    static final int[][] CLRS = {{0, 1, 16}, {0, 2, 13}, {1, 2, 10}, {2, 1, 4}, {1, 3, 12}, {3, 2, 9}, {2, 4, 14}, {4, 3, 7}, {3, 5, 20}, {4, 5, 4}};
    static final List<Algorithm> FLOWS = List.of(new FordFulkerson(), new EdmondsKarp(), new Dinic(), new MinCut());

    @Test void everyMaxFlowAlgorithmFindsTheClrsAnswer() throws Exception {
        for (Algorithm a : FLOWS) {
            AlgorithmResult r = TestSupport.run(a, TestSupport.network(6, 0, 5, CLRS));
            assertEquals(23, r.summary.get("maxFlow"), a.info().id());
            assertEquals(true, r.summary.get("cutEqualsFlow"), a.info().id());
        }
    }

    @Test void algorithmsAgreeAndMinCutEqualsBruteForceOnRandomNetworks() throws Exception {
        Random rnd = new Random(11);
        for (int round = 0; round < 60; round++) {
            int n = 6 + rnd.nextInt(3), m = 8 + rnd.nextInt(10);
            int[][] edges = new int[m][];
            for (int i = 0; i < m; i++) { int u = rnd.nextInt(n), v = rnd.nextInt(n); if (u == v) v = (v + 1) % n; edges[i] = new int[]{u, v, 1 + rnd.nextInt(9)}; }
            Object expect = null;
            for (Algorithm a : FLOWS) {
                AlgorithmResult r = TestSupport.run(a, TestSupport.network(n, 0, n - 1, edges));
                if (expect == null) expect = r.summary.get("maxFlow");
                assertEquals(expect, r.summary.get("maxFlow"), a.info().id() + " round " + round);
                assertEquals(true, r.summary.get("cutEqualsFlow"));
            }
            int best = Integer.MAX_VALUE;
            for (int mask = 0; mask < 1 << n; mask++) {
                if ((mask & 1) == 0 || (mask >> (n - 1) & 1) == 1) continue;
                int cap = 0;
                for (int[] e : edges) if ((mask >> e[0] & 1) == 1 && (mask >> e[1] & 1) == 0) cap += e[2];
                best = Math.min(best, cap);
            }
            assertEquals(best, expect, "max-flow min-cut theorem, round " + round);
        }
    }

    @Test void bundledNetworkTopology() throws Exception {
        Dataset d = TestSupport.app().datasets.get(null);
        Topology t = d.topology();
        Object expect = null;
        for (Algorithm a : FLOWS) {
            AlgorithmInput in = new AlgorithmInput();
            in.edges = t.edges(); in.names = t.names(); in.source = t.source(); in.sink = t.sink();
            AlgorithmResult r = TestSupport.run(a, in);
            if (expect == null) expect = r.summary.get("maxFlow");
            assertEquals(expect, r.summary.get("maxFlow"));
        }
        assertEquals(76, expect);
    }

    @Test void invalidNetworksAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp().run(TestSupport.network(3, 0, 0, new int[][]{{0, 1, 1}}), TestSupport.ctx()));
        assertThrows(IllegalArgumentException.class, () -> new Dinic().run(TestSupport.network(3, 0, 2, new int[0][]), TestSupport.ctx()));
        assertThrows(IllegalArgumentException.class, () -> new Dinic().run(TestSupport.network(3, 0, 2, new int[][]{{0, 5, 1}}), TestSupport.ctx()));
    }

    @Test void flowTraceEndsWithTheReportedMaximumFlow() throws Exception {
        com.cybershield.execution.ExecutionContext ctx = TestSupport.traced();
        AlgorithmResult r = new EdmondsKarp().run(TestSupport.network(6, 0, 5, CLRS), ctx);
        List<Map<String, Object>> ev = ctx.trace.events();
        assertEquals("graph", ev.get(0).get("t"));
        Map<String, Object> cut = ev.get(ev.size() - 1);
        assertEquals("cut", cut.get("t"));
        assertEquals(r.summary.get("maxFlow"), cut.get("total"));
        int[] flows = (int[]) cut.get("flows");
        int outOfSource = 0;
        for (int i = 0; i < CLRS.length; i++) { assertTrue(flows[i] <= CLRS[i][2]); if (CLRS[i][0] == 0) outOfSource += flows[i]; }
        assertEquals(23, outOfSource);
    }

    @Test void bipartiteMatchingMatchesMaxFlowAndBruteForce() throws Exception {
        Random rnd = new Random(12);
        for (int round = 0; round < 80; round++) {
            int L = 2 + rnd.nextInt(4), R = 2 + rnd.nextInt(4);
            List<int[]> es = new ArrayList<>();
            for (int u = 0; u < L; u++) for (int v = 0; v < R; v++) if (rnd.nextInt(3) == 0) es.add(new int[]{u, L + v});
            if (es.isEmpty()) es.add(new int[]{0, L});
            AlgorithmInput in = new AlgorithmInput();
            for (int i = 0; i < L + R; i++) in.names.add("v" + i);
            in.leftCount = L; in.rightCount = R; in.edges = es.toArray(new int[0][]);
            AlgorithmResult r = TestSupport.run(new BipartiteMatching(), in);
            assertEquals(true, r.summary.get("matchesFlow"));
            assertEquals(brute(0, new boolean[R], in.edges, L), r.summary.get("matched"));
            Set<String> seenA = new HashSet<>(), seenB = new HashSet<>();
            for (Map<String, Object> it : r.items) { assertTrue(seenA.add((String) it.get("analyst"))); assertTrue(seenB.add((String) it.get("alert"))); }
        }
    }

    private static int brute(int u, boolean[] used, int[][] edges, int L) {
        if (u == L) return 0;
        int best = brute(u + 1, used, edges, L);
        for (int[] e : edges) if (e[0] == u && !used[e[1] - L]) { used[e[1] - L] = true; best = Math.max(best, 1 + brute(u + 1, used, edges, L)); used[e[1] - L] = false; }
        return best;
    }
}
