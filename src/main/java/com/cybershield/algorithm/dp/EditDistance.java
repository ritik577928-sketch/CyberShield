package com.cybershield.algorithm.dp;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/**
 * Weighted Damerau-Levenshtein distance. Costs are stored x10 so they stay integers:
 * insert/delete/substitute = 10, look-alike substitution (o↔0, l↔1, s↔5 ...) = 3, adjacent swap = 7.
 */
public final class EditDistance implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("editdistance", "Weighted Damerau-Levenshtein", "M3 · Dynamic Programming", "CO3",
            "dynamic-programming", InputKind.TOKENS_NAMES, "O(n·m) per pair", "O(n·m)",
            "Dynamic programme over a (|a|+1)×(|b|+1) table; each cell is the cheapest of insert, delete, substitute or swap. Different error types carry different costs.",
            "Compare noisy / obfuscated process names against known malware and system-process signatures (typosquatting, homoglyphs).");

    public static final int INS = 10, DEL = 10, SUB = 10, HOMO = 3, SWAP = 7;
    private static final String[] LOOKALIKES = {"o0", "l1", "i1", "li", "s5", "e3", "a@", "a4", "b8", "g9", "t7", "z2"};

    @Override public AlgorithmInfo info() { return INFO; }

    static boolean confusable(char a, char b) {
        for (String p : LOOKALIKES) if ((p.charAt(0) == a && p.charAt(1) == b) || (p.charAt(0) == b && p.charAt(1) == a)) return true;
        return false;
    }

    /** Full DP table (costs x10). */
    public static int[][] table(String a, String b, boolean damerau, ExecutionContext ctx) {
        int m = a.length(), n = b.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) d[i][0] = i * DEL;
        for (int j = 0; j <= n; j++) d[0][j] = j * INS;
        boolean tr = ctx.tracing();
        if (tr) ctx.trace.emit("pair", "Compare \"" + a + "\" → \"" + b + "\". Row 0 and column 0 are pure deletions / insertions.", "a", a, "b", b, "rows", m + 1, "cols", n + 1);
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                ctx.cmp();
                ctx.op(3);
                char x = a.charAt(i - 1), y = b.charAt(j - 1);
                int sub = x == y ? 0 : (confusable(x, y) ? HOMO : SUB);
                int best = d[i - 1][j - 1] + sub;
                String op = x == y ? "match" : (sub == HOMO ? "look-alike" : "substitute");
                if (d[i - 1][j] + DEL < best) { best = d[i - 1][j] + DEL; op = "delete"; }
                if (d[i][j - 1] + INS < best) { best = d[i][j - 1] + INS; op = "insert"; }
                if (damerau && i > 1 && j > 1 && x == b.charAt(j - 2) && a.charAt(i - 2) == y && d[i - 2][j - 2] + SWAP < best) {
                    best = d[i - 2][j - 2] + SWAP;
                    op = "swap";
                }
                d[i][j] = best;
                if (ctx.tracing())
                    ctx.trace.emit("cell", "d[" + i + "][" + j + "] for '" + x + "' vs '" + y + "': best operation is " + op + " → cost " + best / 10.0 + ".",
                            "i", i, "j", j, "v", best / 10.0, "op", op);
            }
        }
        if (ctx.tracing()) {
            List<int[]> path = new ArrayList<>();
            int i = m, j = n;
            path.add(new int[]{i, j});
            while (i > 0 || j > 0) {
                if (i > 0 && j > 0) {
                    char x = a.charAt(i - 1), y = b.charAt(j - 1);
                    int sub = x == y ? 0 : (confusable(x, y) ? HOMO : SUB);
                    if (d[i][j] == d[i - 1][j - 1] + sub) { i--; j--; path.add(new int[]{i, j}); continue; }
                }
                if (i > 0 && d[i][j] == d[i - 1][j] + DEL) { i--; path.add(new int[]{i, j}); continue; }
                if (j > 0 && d[i][j] == d[i][j - 1] + INS) { j--; path.add(new int[]{i, j}); continue; }
                if (damerau && i > 1 && j > 1 && d[i][j] == d[i - 2][j - 2] + SWAP) { i -= 2; j -= 2; path.add(new int[]{i, j}); continue; }
                break;
            }
            ctx.trace.emit("path", "Distance = " + d[m][n] / 10.0 + ". Trace back from the corner to see which edits produced it.", "path", path, "dist", d[m][n] / 10.0);
        }
        return d;
    }

    public static double distance(String a, String b, boolean damerau) {
        return table(a, b, damerau, new ExecutionContext(com.cybershield.execution.Trace.off()))[a.length()][b.length()] / 10.0;
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        double threshold = in.param("threshold", 0.2);
        boolean damerau = in.param("damerau", 1) != 0;
        int malwareLike = 0, masquerade = 0, known = 0;
        for (String token : in.tokens) {
            double bestD = Double.MAX_VALUE;
            int bestI = -1;
            for (int i = 0; i < in.patterns.size(); i++) {
                int[][] d = table(token, in.patterns.get(i), damerau, ctx);
                double dist = d[token.length()][in.patterns.get(i).length()] / 10.0;
                if (dist < bestD) { bestD = dist; bestI = i; }
            }
            if (bestI < 0) continue;
            String nearest = in.patterns.get(bestI), cat = bestI < in.names.size() ? in.names.get(bestI) : "UNKNOWN";
            double norm = bestD / Math.max(1, Math.max(token.length(), nearest.length()));
            String verdict = null;
            if (bestD == 0) { if (cat.equals("MALWARE")) { verdict = "KNOWN_MALWARE"; known++; } }
            else if (norm <= threshold) {
                if (cat.equals("MALWARE")) { verdict = "MALWARE_LOOKALIKE"; malwareLike++; }
                else { verdict = "MASQUERADING"; masquerade++; }
            }
            if (verdict != null) r.item("token", token, "nearest", nearest, "distance", bestD, "similarity", Math.round((1 - norm) * 100), "verdict", verdict);
        }
        r.headline = (known + malwareLike + masquerade) + " suspicious process name(s): " + known + " known, " + malwareLike + " look-alike, " + masquerade + " masquerading";
        r.put("tokens", in.tokens.size()).put("knownNames", in.patterns.size()).put("knownMalware", known).put("malwareLookalikes", malwareLike)
                .put("masquerading", masquerade).put("threshold", threshold).check(known + malwareLike + masquerade);
        return r;
    }
}
