package com.cybershield.algorithm.string;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Aho-Corasick: trie of all signatures + failure links; finds every signature in ONE pass over the text. */
public final class AhoCorasickSearch implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("ahocorasick", "Aho-Corasick", "M1 · String Algorithms", "CO1",
            "string-matching", InputKind.TEXT_PATTERNS, "O(n + m + z)", "O(m·σ)",
            "Builds a trie of all patterns, adds failure links with BFS, then walks the text once; output links report every pattern ending at a position.",
            "Detect many threat signatures simultaneously in a single pass over a security log.");

    @FunctionalInterface public interface MatchSink { void match(String pattern, int start); }

    @Override public AlgorithmInfo info() { return INFO; }

    /** Searches {@code text} for all {@code patterns}; returns the number of trie nodes. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int search(String text, List<String> patterns, ExecutionContext ctx, MatchSink sink) {
        int cap = 1;
        for (String p : patterns) cap += p.length();
        int[] fail = new int[cap], dict = new int[cap], parent = new int[cap], depth = new int[cap];
        char[] ch = new char[cap];
        String[] out = new String[cap];
        HashMap<Character, Integer>[] next = new HashMap[cap];
        Arrays.fill(dict, -1);
        next[0] = new HashMap<>();
        int size = 1;

        // 1) trie construction
        for (String p : patterns) {
            if (p.isEmpty()) continue;
            int cur = 0;
            for (int k = 0; k < p.length(); k++) {
                char c = p.charAt(k);
                ctx.op();
                Integer nx = next[cur].get(c);
                boolean created = false;
                if (nx == null) {
                    nx = size++;
                    next[nx] = new HashMap<>();
                    parent[nx] = cur;
                    ch[nx] = c;
                    depth[nx] = depth[cur] + 1;
                    next[cur].put(c, nx);
                    created = true;
                }
                if (ctx.tracing())
                    ctx.trace.emit("insert", (created ? "Create node " + nx : "Reuse node " + nx) + " for '" + c + "' while inserting \"" + p + "\".",
                            "pattern", p, "k", k, "node", nx, "parent", cur, "ch", String.valueOf(c), "created", created, "end", k == p.length() - 1);
                cur = nx;
            }
            out[cur] = p;
        }

        // 2) failure links by BFS
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int v : next[0].values()) {
            fail[v] = 0;
            q.add(v);
            if (ctx.tracing()) ctx.trace.emit("fail", "Depth-1 node " + v + " fails to the root.", "node", v, "to", 0);
        }
        while (!q.isEmpty()) {
            int u = q.poll();
            for (Map.Entry<Character, Integer> en : next[u].entrySet()) {
                char c = en.getKey();
                int v = en.getValue(), f = fail[u];
                ctx.op();
                while (f != 0 && !next[f].containsKey(c)) { f = fail[f]; ctx.cmp(); }
                Integer g = next[f].get(c);
                fail[v] = (g != null && g != v) ? g : 0;
                dict[v] = out[fail[v]] != null ? fail[v] : dict[fail[v]];
                if (ctx.tracing())
                    ctx.trace.emit("fail", "Failure link of node " + v + " ('" + c + "') → node " + fail[v] + " (longest proper suffix that is also a trie prefix).",
                            "node", v, "to", fail[v]);
                q.add(v);
            }
        }

        // 3) single pass over the text
        if (ctx.tracing()) ctx.trace.emit("search-start", "Trie ready (" + size + " nodes). Scan the text once, following goto edges and failure links.", "nodes", size);
        int s = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (s != 0 && !next[s].containsKey(c)) {
                int to = fail[s];
                ctx.cmp();
                if (ctx.tracing())
                    ctx.trace.emit("fallback", "No edge '" + c + "' from node " + s + " → follow failure link to node " + to + ".", "i", i, "node", s, "to", to, "ch", String.valueOf(c));
                s = to;
            }
            ctx.cmp();
            ctx.op();
            Integer nx = next[s].get(c);
            int from = s;
            s = nx != null ? nx : 0;
            if (ctx.tracing())
                ctx.trace.emit("goto", "Read text[" + i + "]='" + c + "': node " + from + (nx != null ? " → " + s : " stays at root") + ".",
                        "i", i, "ch", String.valueOf(c), "from", from, "to", s, "moved", nx != null);
            for (int o = out[s] != null ? s : dict[s]; o != -1; o = dict[o]) {
                String p = out[o];
                int start = i - p.length() + 1;
                sink.match(p, start);
                if (ctx.tracing())
                    ctx.trace.emit("match", "Output: \"" + p + "\" ends at index " + i + " (starts at " + start + ").", "i", i, "pattern", p, "start", start, "node", o);
            }
        }
        return size;
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        Map<String, Integer> per = new LinkedHashMap<>();
        long[] total = {0};
        int nodes = search(in.text, in.patterns, ctx, (p, st) -> {
            per.merge(p, 1, Integer::sum);
            total[0]++;
            r.item("pattern", p, "start", st, "end", st + p.length());
        });
        r.headline = total[0] + " matches for " + in.patterns.size() + " signatures in a single pass";
        r.put("matches", total[0]).put("perPattern", per).put("trieNodes", nodes).put("textLength", in.text.length()).check(total[0]);
        return r;
    }
}
