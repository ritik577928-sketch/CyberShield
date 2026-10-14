package com.cybershield.algorithm.dp;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Matrix-chain multiplication DP: best parenthesisation of a chain of data-processing stages. */
public final class MatrixChain implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("matrixchain", "Matrix-Chain Multiplication", "M3 · Dynamic Programming", "CO3",
            "dynamic-programming", InputKind.DIMS, "O(n³)", "O(n²)",
            "m[i][j] = min over split k of m[i][k] + m[k+1][j] + d[i-1]·d[k]·d[j]. Chains are filled by increasing length.",
            "Optimise the order in which security-data transformation stages (feature matrices) are multiplied.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int[] d = in.ints;
        int n = d.length - 1;
        if (n < 1) throw new IllegalArgumentException("Matrix chain needs at least two dimensions");
        long[][] m = new long[n + 1][n + 1];
        int[][] s = new int[n + 1][n + 1];
        for (int len = 2; len <= n; len++) {
            for (int i = 1; i + len - 1 <= n; i++) {
                int j = i + len - 1;
                m[i][j] = Long.MAX_VALUE;
                for (int k = i; k < j; k++) {
                    ctx.cmp();
                    ctx.op(3);
                    long cost = m[i][k] + m[k + 1][j] + (long) d[i - 1] * d[k] * d[j];
                    if (cost < m[i][j]) { m[i][j] = cost; s[i][j] = k; }
                }
                if (ctx.tracing())
                    ctx.trace.emit("cell", "Chain A" + i + "..A" + j + ": best split after A" + s[i][j] + " → " + m[i][j] + " scalar multiplications.",
                            "i", i, "j", j, "k", s[i][j], "cost", m[i][j], "table", copy(m, n));
            }
        }
        // naive left-to-right cost for comparison
        long naive = 0;
        int rows = d[0];
        for (int i = 1; i < n; i++) naive += (long) rows * d[i] * d[i + 1];
        String paren = paren(s, 1, n, in.names);
        AlgorithmResult r = new AlgorithmResult();
        r.headline = "Optimal order " + paren + " costs " + m[1][n] + " multiplications (left-to-right: " + naive + ")";
        r.put("stages", n).put("optimalCost", m[1][n]).put("naiveCost", naive).put("parenthesization", paren)
                .put("savingsPercent", naive == 0 ? 0 : Math.round(100.0 * (naive - m[1][n]) / naive)).check(m[1][n]);
        return r;
    }

    private static long[][] copy(long[][] m, int n) {
        long[][] c = new long[n][n];
        for (int i = 1; i <= n; i++) for (int j = 1; j <= n; j++) c[i - 1][j - 1] = m[i][j] == Long.MAX_VALUE ? 0 : m[i][j];
        return c;
    }

    static String paren(int[][] s, int i, int j, List<String> names) {
        if (i == j) return i - 1 < names.size() ? names.get(i - 1) : "A" + i;
        return "(" + paren(s, i, s[i][j], names) + " × " + paren(s, s[i][j] + 1, j, names) + ")";
    }
}
