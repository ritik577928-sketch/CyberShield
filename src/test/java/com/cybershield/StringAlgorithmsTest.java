package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.algorithm.*;
import com.cybershield.algorithm.string.*;
import com.cybershield.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StringAlgorithmsTest {
    @Test void kmpKnownCases() {
        assertEquals(List.of(0, 1, 2, 3), KmpSearch.search("aaaaa", "aa", TestSupport.ctx()));
        assertEquals(List.of(6), KmpSearch.search("abxabcabcaby", "abcaby", TestSupport.ctx()));
        assertTrue(KmpSearch.search("abc", "abcd", TestSupport.ctx()).isEmpty());
        assertTrue(KmpSearch.search("abc", "", TestSupport.ctx()).isEmpty());
    }

    @Test void lpsTableMatchesTheTextbook() {
        assertArrayEquals(new int[]{0, 0, 0, 0, 1, 2, 3, 1}, KmpSearch.buildLps("abcdabca", TestSupport.ctx()));
        assertArrayEquals(new int[]{0, 1, 0, 1, 2, 2, 3}, KmpSearch.buildLps("aabaaab", TestSupport.ctx()));
        assertArrayEquals(new int[]{0, 0, 1, 2, 3, 4, 0, 1}, KmpSearch.buildLps("abababca", TestSupport.ctx()));
        assertArrayEquals(new int[]{0, 1, 2, 3}, KmpSearch.buildLps("aaaa", TestSupport.ctx()));
    }

    @Test void allFourAlgorithmsAgreeWithNaiveSearch() {
        Random rnd = new Random(1);
        for (int round = 0; round < 300; round++) {
            String text = TestSupport.randomString(rnd, 1 + rnd.nextInt(80), "ab" + (round % 3 == 0 ? "c" : ""));
            String p = TestSupport.randomString(rnd, 1 + rnd.nextInt(5), "ab");
            List<Integer> expect = TestSupport.naiveFind(text, p);
            assertEquals(expect, KmpSearch.search(text, p, TestSupport.ctx()), "KMP " + text + " / " + p);
            assertEquals(expect, ZFunctionSearch.search(text, p, TestSupport.ctx()), "Z " + text + " / " + p);
            assertEquals(expect, RabinKarpSearch.search(text, p, TestSupport.ctx()), "RK " + text + " / " + p);
            List<Integer> ac = new ArrayList<>();
            AhoCorasickSearch.search(text, List.of(p), TestSupport.ctx(), (pat, st) -> ac.add(st));
            assertEquals(expect, ac, "AC " + text + " / " + p);
        }
    }

    @Test void ahoCorasickFindsOverlappingAndNestedPatternsInOnePass() {
        List<String> found = new ArrayList<>();
        AhoCorasickSearch.search("ushers", List.of("he", "she", "his", "hers"), TestSupport.ctx(), (p, s) -> found.add(p + "@" + s));
        Collections.sort(found);
        assertEquals(List.of("he@2", "hers@2", "she@1"), found);
    }

    @Test void ahoCorasickMatchesManyPatternsLikeNaiveSearch() {
        Random rnd = new Random(2);
        for (int round = 0; round < 100; round++) {
            String text = TestSupport.randomString(rnd, 120, "abc");
            List<String> pats = new ArrayList<>();
            for (int i = 0; i < 6; i++) pats.add(TestSupport.randomString(rnd, 1 + rnd.nextInt(4), "abc"));
            Map<String, Integer> got = new TreeMap<>(), want = new TreeMap<>();
            AhoCorasickSearch.search(text, pats, TestSupport.ctx(), (p, s) -> got.merge(p, 1, Integer::sum));
            for (String p : new LinkedHashSet<>(pats)) { int n = TestSupport.naiveFind(text, p).size(); if (n > 0) want.put(p, n); }
            assertEquals(want, got);
        }
    }

    @Test void rabinKarpVerifiesHashHits() throws Exception {
        AlgorithmResult r = TestSupport.run(new RabinKarpSearch(), AlgorithmInput.ofText("abracadabra", "abra", "cad", "zzz"));
        assertEquals(3L, r.summary.get("matches"));
        assertEquals(0L, r.summary.get("hashCollisions"));
    }

    @Test void zFunctionArrayIsCorrect() {
        assertArrayEquals(new int[]{11, 1, 0, 0, 4, 1, 0, 0, 0, 2, 1}, ZFunctionSearch.zArray("aabxaabxcaa", TestSupport.ctx()));
    }

    @Test void zFunctionFlagsRepeatedPatterns() throws Exception {
        AlgorithmResult r = TestSupport.run(new ZFunctionSearch(), AlgorithmInput.ofText("xxabxxabxxabxx", "xxab", "abx", "q"));
        assertEquals(List.of("xxab", "abx"), r.summary.get("repeatedPatterns"));
    }

    @Test void traceDrivesTheAnimationWithTheSameMatchesAsTheResult() throws Exception {
        String text = "the quick union select attack then another union select here";
        for (Algorithm a : List.of(new KmpSearch(), new ZFunctionSearch(), new RabinKarpSearch(), new AhoCorasickSearch())) {
            ExecutionContext ctx = TestSupport.traced();
            AlgorithmResult r = a.run(AlgorithmInput.ofText(text, "union select", "the", "zzz"), ctx);
            assertFalse(ctx.trace.events().isEmpty(), a.info().id() + " produced no trace");
            // Rabin-Karp reports verified matches as "window" events with real=true; the others emit "match" events
            boolean rk = a instanceof RabinKarpSearch;
            long traced = ctx.trace.events().stream().filter(e -> rk ? Boolean.TRUE.equals(e.get("real")) : "match".equals(e.get("t"))).count();
            assertEquals(r.itemsTotal, traced, a.info().id() + ": trace match events must equal the reported matches");
        }
    }

    @Test void kmpNeverMovesTheTextPointerBackwards() {
        ExecutionContext ctx = TestSupport.traced();
        KmpSearch.search("abababababcabababab", "ababc", ctx);
        int last = -1;
        for (Map<String, Object> e : ctx.trace.events()) {
            if (!"cmp".equals(e.get("t"))) continue;
            int ti = (Integer) e.get("ti");
            assertTrue(ti >= last, "text index went backwards");
            last = ti;
        }
        assertTrue(ctx.comparisons() <= 2 * 19 + 10, "KMP must do O(n) comparisons");
    }

    @Test void comparisonCountersAreRecorded() {
        ExecutionContext ctx = TestSupport.ctx();
        KmpSearch.search("aaaaaaaaaa", "aab", ctx);
        assertTrue(ctx.comparisons() > 0 && ctx.operations() > 0);
    }
}
