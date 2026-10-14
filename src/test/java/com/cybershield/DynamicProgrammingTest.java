package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.algorithm.*;
import com.cybershield.algorithm.dp.*;
import com.cybershield.dataset.Dataset;
import java.util.*;
import org.junit.jupiter.api.Test;

class DynamicProgrammingTest {
    @Test void classicEditDistances() {
        assertEquals(3.0, EditDistance.distance("kitten", "sitting", false), 1e-9);
        assertEquals(0.0, EditDistance.distance("same", "same", true), 1e-9);
        assertEquals(1.0, EditDistance.distance("svhost.exe", "svchost.exe", true), 1e-9);
    }

    @Test void damerauSwapIsCheaperThanTwoSubstitutions() {
        assertEquals(0.7, EditDistance.distance("scvhost", "svchost", true), 1e-9);
        assertEquals(2.0, EditDistance.distance("scvhost", "svchost", false), 1e-9);
    }

    @Test void lookAlikeCharactersCostLess() {
        assertEquals(0.6, EditDistance.distance("m1m1katz", "mimikatz", true), 1e-9);
        assertEquals(0.3, EditDistance.distance("expl0rer", "explorer", true), 1e-9);
        assertEquals(1.0, EditDistance.distance("explxrer", "explorer", true), 1e-9);
    }

    @Test void distanceIsAMetricOnRandomStrings() {
        Random rnd = new Random(3);
        for (int i = 0; i < 300; i++) {
            String a = TestSupport.randomString(rnd, rnd.nextInt(8), "abl1o0"), b = TestSupport.randomString(rnd, rnd.nextInt(8), "abl1o0"), c = TestSupport.randomString(rnd, rnd.nextInt(8), "abl1o0");
            assertEquals(0.0, EditDistance.distance(a, a, false), 1e-9);
            assertEquals(EditDistance.distance(a, b, false), EditDistance.distance(b, a, false), 1e-9, a + "/" + b);
            assertTrue(EditDistance.distance(a, c, false) <= EditDistance.distance(a, b, false) + EditDistance.distance(b, c, false) + 1e-9, a + "/" + b + "/" + c);
        }
    }

    @Test void flagsLookalikeProcessesButNotLegitimateOnes() throws Exception {
        AlgorithmInput in = new AlgorithmInput();
        in.patterns = List.of("mimikatz.exe", "svchost.exe");
        in.names = List.of("MALWARE", "SYSTEM");
        in.tokens = List.of("mimikatz.exe", "m1m1katz.exe", "svch0st.exe", "svchost.exe", "chrome.exe");
        AlgorithmResult r = TestSupport.run(new EditDistance(), in);
        Map<String, String> verdict = new HashMap<>();
        for (Map<String, Object> it : r.items) verdict.put((String) it.get("token"), (String) it.get("verdict"));
        assertEquals("KNOWN_MALWARE", verdict.get("mimikatz.exe"));
        assertEquals("MALWARE_LOOKALIKE", verdict.get("m1m1katz.exe"));
        assertEquals("MASQUERADING", verdict.get("svch0st.exe"));
        assertFalse(verdict.containsKey("svchost.exe"));
        assertFalse(verdict.containsKey("chrome.exe"));
    }

    @Test void bitmaskDpFindsTheCheapestCoverAndMatchesBruteForce() throws Exception {
        Dataset d = TestSupport.app().datasets.get(null);
        AlgorithmInput in = new AlgorithmInput();
        in.rules = d.rules();
        in.names = d.categories();
        AlgorithmResult r = TestSupport.run(new BitmaskRuleCover(), in);
        int best = Integer.MAX_VALUE, full = (1 << in.names.size()) - 1;
        for (int sub = 0; sub < 1 << in.rules.size(); sub++) {
            int mask = 0, cost = 0;
            for (int i = 0; i < in.rules.size(); i++) if ((sub >> i & 1) == 1) { mask |= in.rules.get(i).mask(); cost += in.rules.get(i).cost(); }
            if (mask == full) best = Math.min(best, cost);
        }
        assertEquals(best, r.summary.get("optimalCost"));
        assertTrue((Integer) r.summary.get("greedyCost") >= best);
    }

    @Test void bitmaskDpReportsUncoverableCategories() throws Exception {
        AlgorithmInput in = new AlgorithmInput();
        in.names = List.of("A", "B");
        in.rules = List.of(new AlgorithmInput.Rule("only-A", 5, 1));
        AlgorithmResult r = TestSupport.run(new BitmaskRuleCover(), in);
        assertEquals(false, r.summary.get("feasible"));
        assertEquals(List.of("B"), r.summary.get("uncoverable"));
    }

    @Test void matrixChainReproducesTheClrsExample() throws Exception {
        AlgorithmInput in = new AlgorithmInput();
        in.ints = new int[]{30, 35, 15, 5, 10, 20, 25};
        AlgorithmResult r = TestSupport.run(new MatrixChain(), in);
        assertEquals(15125L, r.summary.get("optimalCost"));
        assertEquals("((A1 × (A2 × A3)) × ((A4 × A5) × A6))", r.summary.get("parenthesization"));
        assertEquals(40500L, r.summary.get("naiveCost"));
    }

    @Test void matrixChainOnTheBundledPipelineIsNeverWorseThanLeftToRight() throws Exception {
        Dataset d = TestSupport.app().datasets.get(null);
        AlgorithmInput in = new AlgorithmInput();
        in.ints = d.pipeline().dims();
        in.names = d.pipeline().names();
        AlgorithmResult r = TestSupport.run(new MatrixChain(), in);
        assertTrue((Long) r.summary.get("optimalCost") <= (Long) r.summary.get("naiveCost"));
    }
}
