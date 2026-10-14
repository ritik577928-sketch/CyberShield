package com.cybershield.algorithm;

import java.util.*;

/** Generic input bag. The service layer fills the fields an algorithm's {@link InputKind} needs. */
public final class AlgorithmInput {
    public record Rule(String name, int cost, int mask) {}

    public String text = "";
    public List<String> patterns = new ArrayList<>();
    public List<String> tokens = new ArrayList<>();
    public List<String> names = new ArrayList<>();
    public int[] ints = new int[0];
    public int[][] edges = new int[0][];
    public int source, sink, leftCount, rightCount;
    public int[][] cnf = new int[0][];
    public int numVars;
    public List<Rule> rules = new ArrayList<>();
    public List<String> lines = new ArrayList<>();
    public final Map<String, Double> params = new HashMap<>();

    public double param(String key, double def) { return params.getOrDefault(key, def); }
    public int intParam(String key, int def) { return (int) Math.round(params.getOrDefault(key, (double) def)); }

    public static AlgorithmInput ofText(String text, String... patterns) {
        AlgorithmInput in = new AlgorithmInput();
        in.text = text;
        in.patterns = new ArrayList<>(Arrays.asList(patterns));
        return in;
    }

    /** Short human description of the input size, stored in the run history. */
    public String describe() {
        if (!text.isEmpty()) return text.length() + " chars" + (patterns.isEmpty() ? "" : ", " + patterns.size() + " patterns");
        if (edges.length > 0) return edges.length + " edges";
        if (cnf.length > 0) return cnf.length + " clauses";
        if (ints.length > 0) return ints.length + " values";
        if (!tokens.isEmpty()) return tokens.size() + " tokens";
        if (!lines.isEmpty()) return lines.size() + " lines";
        if (!rules.isEmpty()) return rules.size() + " rules";
        return "empty";
    }
}
