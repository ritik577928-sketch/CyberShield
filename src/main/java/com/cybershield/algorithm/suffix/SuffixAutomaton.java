package com.cybershield.algorithm.suffix;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Suffix automaton (DAWG): the minimal DFA of all suffixes; supports exact occurrence counts for any substring. */
public final class SuffixAutomaton implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("suffixautomaton", "Suffix Automaton", "M2 · Suffix Structures", "CO2",
            "suffix-structures", InputKind.TEXT, "O(n)", "O(n·σ)",
            "Online construction with suffix links and cloned states; every state represents a class of substrings with identical end positions.",
            "Substring-based threat analysis: count how often each signature occurs in O(|pattern|).");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        String t = in.text;
        int n = t.length(), cap = 2 * n + 2;
        int[] len = new int[cap], link = new int[cap], first = new int[cap];
        long[] cnt = new long[cap];
        HashMap<Character, Integer>[] next = new HashMap[cap];
        next[0] = new HashMap<>();
        link[0] = -1;
        int size = 1, last = 0;
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            int cur = size++;
            next[cur] = new HashMap<>();
            len[cur] = len[last] + 1;
            first[cur] = i;
            cnt[cur] = 1;
            int p = last, clone = -1;
            while (p != -1 && !next[p].containsKey(c)) { next[p].put(c, cur); p = link[p]; ctx.cmp(); ctx.op(); }
            if (p == -1) link[cur] = 0;
            else {
                int q = next[p].get(c);
                if (len[p] + 1 == len[q]) link[cur] = q;
                else {
                    clone = size++;
                    len[clone] = len[p] + 1;
                    next[clone] = new HashMap<>(next[q]);
                    link[clone] = link[q];
                    first[clone] = first[q];
                    while (p != -1 && next[p].get(c) == q) { next[p].put(c, clone); p = link[p]; ctx.cmp(); ctx.op(); }
                    link[q] = link[cur] = clone;
                }
            }
            last = cur;
            if (ctx.tracing())
                ctx.trace.emit("extend", "Append '" + c + "' (index " + i + "): new state " + cur + (clone >= 0 ? ", cloned state " + clone + " to keep the automaton minimal" : "") + ". Total states: " + size + ".",
                        "i", i, "ch", String.valueOf(c), "cur", cur, "clone", clone, "states", size, "link", link[cur]);
        }
        // occurrence counts: propagate along suffix links in decreasing length order
        int[] bucket = new int[n + 2];
        for (int v = 0; v < size; v++) bucket[len[v]]++;
        for (int i = 1; i <= n; i++) bucket[i] += bucket[i - 1];
        int[] order = new int[size];
        for (int v = size - 1; v >= 0; v--) order[--bucket[len[v]]] = v;
        for (int i = size - 1; i > 0; i--) { int v = order[i]; if (link[v] >= 0) cnt[link[v]] += cnt[v]; }
        long distinct = 0;
        int best = 0, bestState = 0;
        for (int v = 1; v < size; v++) {
            distinct += len[v] - len[link[v]];
            if (cnt[v] >= 2 && len[v] > best) { best = len[v]; bestState = v; }
        }
        AlgorithmResult r = new AlgorithmResult();
        String longest = best > 0 ? t.substring(first[bestState] - best + 1, first[bestState] + 1) : "";
        Map<String, Long> occ = new LinkedHashMap<>();
        for (String p : in.patterns) {
            if (p.isEmpty()) continue;
            int s = 0;
            boolean ok = true;
            for (int i = 0; i < p.length() && ok; i++) {
                Integer nx = next[s].get(p.charAt(i));
                ctx.cmp();
                if (nx == null) ok = false; else s = nx;
            }
            long c = ok ? cnt[s] : 0;
            occ.put(p, c);
            r.item("pattern", p, "occurrences", c);
        }
        long totalOcc = occ.values().stream().mapToLong(Long::longValue).sum();
        r.headline = size + " states, " + distinct + " distinct substrings; longest repeat " + best + " chars";
        r.put("length", n).put("states", size).put("distinctSubstrings", distinct).put("longestRepeatedLength", best)
                .put("longestRepeated", SuffixAnalysis.display(longest)).put("occurrences", occ).put("totalOccurrences", totalOcc).check(best);
        return r;
    }
}
