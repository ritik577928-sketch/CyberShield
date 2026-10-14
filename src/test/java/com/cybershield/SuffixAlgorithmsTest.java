package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.algorithm.*;
import com.cybershield.algorithm.suffix.*;
import com.cybershield.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SuffixAlgorithmsTest {
    static int[] naiveSa(String s) {
        Integer[] idx = new Integer[s.length()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparing(s::substring));
        return Arrays.stream(idx).mapToInt(Integer::intValue).toArray();
    }

    static int[] naiveLcp(String s, int[] sa) {
        int[] l = new int[sa.length];
        for (int i = 1; i < sa.length; i++) {
            int a = sa[i - 1], b = sa[i], h = 0;
            while (a + h < s.length() && b + h < s.length() && s.charAt(a + h) == s.charAt(b + h)) h++;
            l[i] = h;
        }
        return l;
    }

    @Test void banana() {
        assertArrayEquals(new int[]{5, 3, 1, 0, 4, 2}, SuffixArrayDoubling.build("banana", TestSupport.ctx()));
        assertArrayEquals(new int[]{5, 3, 1, 0, 4, 2}, SaIs.build("banana", TestSupport.ctx()));
    }

    @Test void edgeCases() {
        assertEquals(0, SuffixArrayDoubling.build("", TestSupport.ctx()).length);
        assertEquals(0, SaIs.build("", TestSupport.ctx()).length);
        assertArrayEquals(new int[]{0}, SaIs.build("x", TestSupport.ctx()));
        assertArrayEquals(naiveSa("aaaa"), SaIs.build("aaaa", TestSupport.ctx()));
        assertArrayEquals(naiveSa("aaaa"), SuffixArrayDoubling.build("aaaa", TestSupport.ctx()));
    }

    @Test void bothConstructionsMatchNaiveSortingOnRandomStrings() {
        Random rnd = new Random(7);
        for (int round = 0; round < 400; round++) {
            String s = TestSupport.randomString(rnd, rnd.nextInt(60), round % 4 == 0 ? "ab" : round % 4 == 1 ? "abc" : round % 4 == 2 ? "a" : "abcdefgh ");
            int[] expect = naiveSa(s);
            assertArrayEquals(expect, SuffixArrayDoubling.build(s, TestSupport.ctx()), "doubling: " + s);
            assertArrayEquals(expect, SaIs.build(s, TestSupport.ctx()), "SA-IS: " + s);
        }
    }

    @Test void kasaiLcpMatchesNaiveLcp() {
        Random rnd = new Random(8);
        for (int round = 0; round < 200; round++) {
            String s = TestSupport.randomString(rnd, 1 + rnd.nextInt(50), "abc");
            int[] sa = SaIs.build(s, TestSupport.ctx());
            assertArrayEquals(naiveLcp(s, sa), SuffixAnalysis.kasai(s, sa, TestSupport.ctx()), s);
        }
    }

    @Test void suffixAutomatonCountsDistinctSubstringsAndOccurrences() throws Exception {
        Random rnd = new Random(9);
        for (int round = 0; round < 100; round++) {
            String s = TestSupport.randomString(rnd, 1 + rnd.nextInt(40), "ab" + (round % 2 == 0 ? "c" : ""));
            Set<String> subs = new HashSet<>();
            for (int i = 0; i < s.length(); i++) for (int j = i + 1; j <= s.length(); j++) subs.add(s.substring(i, j));
            AlgorithmInput in = AlgorithmInput.ofText(s, "a", "ab", "ba", "abc", "zz");
            AlgorithmResult r = TestSupport.run(new SuffixAutomaton(), in);
            assertEquals((long) subs.size(), ((Number) r.summary.get("distinctSubstrings")).longValue(), s);
            @SuppressWarnings("unchecked") Map<String, Long> occ = (Map<String, Long>) r.summary.get("occurrences");
            for (String p : in.patterns) assertEquals(Long.valueOf(TestSupport.naiveFind(s, p).size()), occ.get(p), s + " / " + p);
        }
    }

    @Test void allSuffixStructuresAgreeOnTheLongestRepeat() throws Exception {
        String text = "GET /a?x=1 union select password-- HTTP\nGET /b union select password-- HTTP\nPOST /c ok";
        AlgorithmInput in = AlgorithmInput.ofText(text);
        int expected = -1;
        for (Algorithm a : List.of(new SuffixArrayDoubling(), new SaIs(), new KasaiLcp(), new SuffixAutomaton())) {
            AlgorithmResult r = TestSupport.run(a, in);
            int len = ((Number) r.summary.get("longestRepeatedLength")).intValue();
            if (expected < 0) expected = len;
            assertEquals(expected, len, a.info().id());
        }
        assertTrue(expected >= 30, "the repeated attack phrase should be found, got " + expected);
    }

    @Test void repeatedPhrasesAreReallyRepeated() throws Exception {
        String text = "xx attack-payload-123 yy attack-payload-123 zz attack-payload-123 ww";
        AlgorithmInput in = AlgorithmInput.ofText(text);
        in.params.put("minLen", 10.0);
        AlgorithmResult r = TestSupport.run(new SaIs(), in);
        assertFalse(r.items.isEmpty());
        for (Map<String, Object> it : r.items) {
            String phrase = (String) it.get("phrase");
            assertEquals(((Number) it.get("count")).intValue(), TestSupport.naiveFind(text, phrase).size(), phrase);
        }
    }

    @Test void prefixDoublingTraceEndsWithThePublishedSuffixArray() {
        String s = "mississippi";
        ExecutionContext ctx = TestSupport.traced();
        int[] sa = SuffixArrayDoubling.build(s, ctx);
        List<Map<String, Object>> rounds = ctx.trace.events().stream().filter(e -> "round".equals(e.get("t"))).toList();
        assertTrue(rounds.size() >= 3);
        Map<String, Object> last = rounds.get(rounds.size() - 1);
        int[] order = (int[]) last.get("order");
        assertArrayEquals(sa, Arrays.copyOfRange(order, 1, order.length));
        assertEquals(s.length() + 1, last.get("classes"));
        int prev = 0;
        for (Map<String, Object> e : rounds) { int c = (Integer) e.get("classes"); assertTrue(c >= prev); prev = c; }
    }

    @Test void saIsTraceShowsInducedSorting() {
        ExecutionContext ctx = TestSupport.traced();
        int[] sa = SaIs.build("abracadabra", ctx);
        List<Map<String, Object>> ev = ctx.trace.events();
        assertEquals("classify", ev.get(0).get("t"));
        Map<String, Object> last = ev.get(ev.size() - 1);
        assertEquals("induce", last.get("t"));
        assertArrayEquals(sa, (int[]) last.get("sa"));
    }
}
