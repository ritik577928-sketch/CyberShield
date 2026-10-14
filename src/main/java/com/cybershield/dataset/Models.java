package com.cybershield.dataset;

import java.util.*;

/** Small immutable data holders parsed from the dataset files. */
public final class Models {
    private Models() {}

    public record Signature(String category, int severity, String pattern) {}
    public record Flow(long ts, String src, String dst, int sport, int dport, String proto, long bytes, int packets, String flags) {}
    public record Cnf(int vars, int[][] clauses, List<String> names) {}
    public record Topology(List<String> names, int[][] edges, int source, int sink) {}
    public record Assignments(List<String> names, int left, int right, int[][] edges) {}
    public record Graph(List<String> names, int[][] edges) {}
    public record Numbers(List<String> values, List<String> labels) {}
    public record Pipeline(List<String> names, int[] dims) {}
}
