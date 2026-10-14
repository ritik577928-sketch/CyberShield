package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.algorithm.*;
import com.cybershield.algorithm.np.*;
import com.cybershield.dataset.*;
import com.cybershield.dataset.Models.*;
import com.cybershield.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NpAlgorithmsTest {
    static AlgorithmInput cnf(int vars, int[][] clauses) {
        AlgorithmInput in = new AlgorithmInput();
        in.numVars = vars;
        in.cnf = clauses;
        for (int i = 1; i <= vars; i++) in.names.add("x" + i);
        return in;
    }

    static boolean bruteSat(int vars, int[][] clauses) {
        for (int m = 0; m < 1 << vars; m++) {
            boolean all = true;
            for (int[] c : clauses) {
                boolean ok = false;
                for (int l : c) if (((m >> (Math.abs(l) - 1) & 1) == 1) == (l > 0)) ok = true;
                if (!ok) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }

    @Test void dpllSolvesTheBundledPolicies() throws Exception {
        Cnf c = TestSupport.app().datasets.get(null).cnf();
        AlgorithmInput in = cnf(c.vars(), c.clauses());
        in.names = c.names();
        AlgorithmResult r = TestSupport.run(new DpllSat(), in);
        assertEquals(true, r.summary.get("satisfiable"));
        assertEquals(true, r.summary.get("verified"));
        assertEquals(c.vars(), r.items.size());
    }

    @Test void dpllDetectsAnUnsatisfiableContradiction() throws Exception {
        List<int[]> all = new ArrayList<>();
        for (int m = 0; m < 8; m++) all.add(new int[]{(m & 1) == 0 ? 1 : -1, (m & 2) == 0 ? 2 : -2, (m & 4) == 0 ? 3 : -3});
        AlgorithmResult r = TestSupport.run(new DpllSat(), cnf(3, all.toArray(new int[0][])));
        assertEquals(false, r.summary.get("satisfiable"));
    }

    @Test void dpllMatchesBruteForceOnRandom3Sat() throws Exception {
        Random rnd = new Random(21);
        for (int round = 0; round < 300; round++) {
            int vars = 3 + rnd.nextInt(3), m = 1 + rnd.nextInt(14);
            int[][] cl = randomClauses(rnd, vars, m);
            AlgorithmResult r = TestSupport.run(new DpllSat(), cnf(vars, cl));
            assertEquals(bruteSat(vars, cl), r.summary.get("satisfiable"), "round " + round);
            assertEquals(true, r.summary.get("verified"));
        }
    }

    static int[][] randomClauses(Random rnd, int vars, int m) {
        int[][] cl = new int[m][3];
        for (int i = 0; i < m; i++) for (int j = 0; j < 3; j++) cl[i][j] = (1 + rnd.nextInt(vars)) * (rnd.nextBoolean() ? 1 : -1);
        return cl;
    }

    @Test void threeSatToCliqueToVertexCoverAgreesWithDpll() throws Exception {
        Random rnd = new Random(22);
        int sat = 0, unsat = 0;
        for (int round = 0; round < 200; round++) {
            int vars = 3 + (round % 5 == 0 ? 1 : 0), m = 4 + rnd.nextInt(11);
            int[][] cl = randomClauses(rnd, vars, m);
            AlgorithmResult r = TestSupport.run(new ReductionChain(), cnf(vars, cl));
            boolean expect = bruteSat(vars, cl);
            assertEquals(expect, r.summary.get("cliqueFound"), "round " + round);
            assertEquals(true, r.summary.get("dpllAgrees"));
            if (expect) {
                sat++;
                assertEquals(true, r.summary.get("independentSetValid"));
                assertEquals(true, r.summary.get("vertexCoverValid"));
                assertEquals(3 * m - m, r.summary.get("vertexCoverSize"));
                assertEquals(true, r.summary.get("assignmentFromCliqueSatisfies"));
            } else unsat++;
        }
        assertTrue(sat > 20 && unsat >= 5, "test must exercise both outcomes: sat=" + sat + " unsat=" + unsat);
    }

    @Test void reductionRejectsNon3SatInputThroughTheEngine() {
        AlgorithmInput in = cnf(3, new int[][]{{1, 2}, {-1, 3, 2}});
        RunRecord r = TestSupport.app().engine.measure(new ReductionChain(), in, "test", false, 0);
        assertEquals("FAILED", r.status);
        assertTrue(r.error.contains("3-SAT"), r.error);
    }

    @Test void vertexCoverIsValidAndWithinTwiceTheOptimum() throws Exception {
        Random rnd = new Random(23);
        for (int round = 0; round < 150; round++) {
            int n = 4 + rnd.nextInt(7);
            List<int[]> es = new ArrayList<>();
            for (int u = 0; u < n; u++) for (int v = u + 1; v < n; v++) if (rnd.nextInt(3) == 0) es.add(new int[]{u, v});
            if (es.isEmpty()) es.add(new int[]{0, 1});
            AlgorithmInput in = new AlgorithmInput();
            for (int i = 0; i < n; i++) in.names.add("s" + i);
            in.edges = es.toArray(new int[0][]);
            AlgorithmResult r = TestSupport.run(new VertexCover2Approx(), in);
            assertEquals(true, r.summary.get("validCover"));
            assertEquals(true, r.summary.get("withinTwoApprox"));
            assertEquals(VertexCover2Approx.exact(n, in.edges, TestSupport.ctx()), r.summary.get("optimum"));
        }
    }

    @Test void dimacsParserValidatesInput() {
        Cnf c = DimacsParser.parse("c var 1 = mfa\np cnf 2 2\n1 -2 0\n2\n0\n");
        assertEquals(2, c.clauses().length);
        assertEquals("mfa", c.names().get(0));
        assertThrows(IllegalArgumentException.class, () -> DimacsParser.parse("p cnf 2 1\n1 3 0\n"));
        assertThrows(IllegalArgumentException.class, () -> DimacsParser.parse("p cnf 2 2\n1 2 0\n"));
        assertThrows(IllegalArgumentException.class, () -> DimacsParser.parse("p cnf 2 1\n1 2\n"));
        assertThrows(IllegalArgumentException.class, () -> DimacsParser.parse("1 2 0\n"));
    }
}
