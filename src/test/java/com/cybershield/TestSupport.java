package com.cybershield;

import com.cybershield.algorithm.*;
import com.cybershield.execution.*;
import com.cybershield.service.AppContext;
import java.util.*;

/** Shared helpers for the test-suite. */
final class TestSupport {
    private static AppContext app;
    private TestSupport() {}

    static synchronized AppContext app() {
        if (app == null) app = new AppContext(null);
        return app;
    }

    static ExecutionContext ctx() { return new ExecutionContext(Trace.off()); }
    static ExecutionContext traced() { return new ExecutionContext(Trace.on(100_000)); }

    static List<Integer> naiveFind(String text, String p) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i + p.length() <= text.length(); i++) if (text.startsWith(p, i)) r.add(i);
        return r;
    }

    static String randomString(Random r, int len, String alphabet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    static AlgorithmInput network(int n, int source, int sink, int[][] edges) {
        AlgorithmInput in = new AlgorithmInput();
        for (int i = 0; i < n; i++) in.names.add("n" + i);
        in.edges = edges;
        in.source = source;
        in.sink = sink;
        return in;
    }

    static AlgorithmResult run(Algorithm a, AlgorithmInput in) throws Exception { return a.run(in, ctx()); }
}
