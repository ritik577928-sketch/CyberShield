package com.cybershield.algorithm.np;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** 3-SAT → CLIQUE → INDEPENDENT-SET → VERTEX-COVER, executed and verified end to end. */
public final class ReductionChain implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("reduction", "3-SAT → CLIQUE → IS → Vertex-Cover", "M5 · NP-Completeness", "CO5",
            "np-completeness", InputKind.CNF, "O(3^m) search, poly-time reductions", "O(m²)",
            "Builds the clique graph (one vertex per literal occurrence, edges between compatible literals of different clauses), then complements it for Independent-Set and takes the complement of that set as a Vertex-Cover.",
            "Threat-conflict analysis: shows policy conflicts can be encoded as graph problems and the problems are mutually reducible.");

    @Override public AlgorithmInfo info() { return INFO; }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        int m = in.cnf.length;
        if (m == 0) throw new IllegalArgumentException("The policy file contains no clauses");
        for (int[] c : in.cnf) if (c.length != 3) throw new IllegalArgumentException("Reduction needs exact 3-SAT: found a clause with " + c.length + " literals");
        int n = 3 * m;
        int[] clauseOf = new int[n], lit = new int[n];
        for (int i = 0; i < m; i++) for (int j = 0; j < 3; j++) { clauseOf[3 * i + j] = i; lit[3 * i + j] = in.cnf[i][j]; }
        boolean[][] adj = new boolean[n][n];
        int edges = 0;
        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++) {
                ctx.cmp();
                if (clauseOf[a] != clauseOf[b] && lit[a] != -lit[b]) { adj[a][b] = adj[b][a] = true; edges++; }
            }
        if (ctx.tracing())
            ctx.trace.emit("reduce", "3-SAT → CLIQUE: " + m + " clauses become " + n + " vertices and " + edges + " edges. The formula is satisfiable iff the graph has a clique of size " + m + ".",
                    "vertices", n, "edges", edges, "k", m);
        int[] chosen = new int[m];
        boolean found = clique(0, m, n, clauseOf, adj, chosen, ctx);
        AlgorithmResult r = new AlgorithmResult();
        ExecutionContext q = ctx.quiet();
        int[] assign = new int[in.numVars + 1];
        boolean dpll = DpllSat.solve(in.cnf, assign, 0, q);
        r.put("clausesK", m).put("cliqueVertices", n).put("cliqueEdges", edges).put("cliqueFound", found).put("dpllAgrees", dpll == found);
        if (found) {
            // clique -> independent set in the complement graph
            long compEdges = (long) n * (n - 1) / 2 - edges;
            boolean isIndependent = true;
            for (int i = 0; i < m; i++) for (int j = i + 1; j < m; j++) if (!adj[chosen[i]][chosen[j]]) isIndependent = false;
            if (ctx.tracing())
                ctx.trace.emit("independent-set", "CLIQUE → INDEPENDENT-SET: the same " + m + " vertices are pairwise NON-adjacent in the complement graph (" + compEdges + " edges) → independent set of size " + m + ".",
                        "set", chosen.clone(), "complementEdges", compEdges);
            // independent set -> vertex cover = V \ IS
            boolean[] inIs = new boolean[n];
            for (int v : chosen) inIs[v] = true;
            int cover = 0;
            boolean coversAll = true;
            for (int a = 0; a < n; a++) {
                if (!inIs[a]) cover++;
                for (int b = a + 1; b < n; b++) if (!adj[a][b] && inIs[a] && inIs[b]) coversAll = false;
            }
            if (ctx.tracing())
                ctx.trace.emit("vertex-cover", "INDEPENDENT-SET → VERTEX-COVER: V minus the independent set is a vertex cover of size " + cover + " (= " + n + " - " + m + ").", "coverSize", cover);
            boolean[] truth = new boolean[in.numVars + 1];
            int[] model = new int[in.numVars + 1];
            for (int v : chosen) { int l = lit[v]; model[Math.abs(l)] = l > 0 ? 1 : -1; }
            for (int v = 1; v <= in.numVars; v++) if (model[v] == 0) model[v] = 1;
            r.put("independentSetSize", m).put("independentSetValid", isIndependent).put("vertexCoverSize", cover).put("vertexCoverValid", coversAll)
                    .put("assignmentFromCliqueSatisfies", DpllSat.satisfies(in.cnf, model));
            r.headline = "Satisfiable: clique of size " + m + " → independent set " + m + " → vertex cover " + cover;
            for (int v : chosen) r.item("clause", clauseOf[v] + 1, "literal", lit[v]);
        } else {
            r.headline = "No clique of size " + m + " → the formula is unsatisfiable (and the reductions confirm it)";
        }
        r.check(found);
        return r;
    }

    private boolean clique(int depth, int m, int n, int[] clauseOf, boolean[][] adj, int[] chosen, ExecutionContext ctx) {
        if (depth == m) return true;
        for (int v = 3 * depth; v < 3 * depth + 3; v++) {
            boolean ok = true;
            for (int i = 0; i < depth; i++) { ctx.cmp(); if (!adj[chosen[i]][v]) { ok = false; break; } }
            if (ctx.tracing())
                ctx.trace.emit("clique-try", "Clause " + (depth + 1) + ": try literal vertex " + v + " → " + (ok ? "adjacent to all chosen vertices." : "conflicts with an earlier choice."),
                        "depth", depth, "vertex", v, "ok", ok);
            if (!ok) continue;
            chosen[depth] = v;
            ctx.op();
            if (clique(depth + 1, m, n, clauseOf, adj, chosen, ctx)) return true;
        }
        return false;
    }
}
