package com.cybershield.algorithm.np;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** DPLL SAT solver (unit propagation + branching) for security-policy constraints in CNF. */
public final class DpllSat implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("sat", "SAT / 3-SAT (DPLL)", "M5 · NP-Completeness", "CO5",
            "np-completeness", InputKind.CNF, "O(2^n) worst case", "O(n + m)",
            "Repeats unit propagation (a clause with one open literal forces it) and, when stuck, branches on a variable; backtracks on conflict.",
            "Decide whether a set of security-policy constraints (MFA, VPN, segmentation...) can all be satisfied together.");

    @Override public AlgorithmInfo info() { return INFO; }

    public static boolean solve(int[][] cnf, int[] assign, int depth, ExecutionContext ctx) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int ci = 0; ci < cnf.length; ci++) {
                int[] c = cnf[ci];
                int open = 0, last = 0;
                boolean sat = false;
                for (int lit : c) {
                    ctx.cmp();
                    int a = assign[Math.abs(lit)];
                    if (a == 0) { open++; last = lit; }
                    else if ((a > 0) == (lit > 0)) { sat = true; break; }
                }
                if (sat) continue;
                if (open == 0) {
                    ctx.stat("conflicts", 1);
                    if (ctx.tracing()) ctx.trace.emit("conflict", "Clause " + (ci + 1) + " has every literal false → conflict at depth " + depth + ", backtrack.", "clause", ci, "depth", depth);
                    return false;
                }
                if (open == 1) {
                    assign[Math.abs(last)] = last > 0 ? 1 : -1;
                    ctx.stat("propagations", 1);
                    ctx.op();
                    changed = true;
                    if (ctx.tracing())
                        ctx.trace.emit("unit", "Clause " + (ci + 1) + " has a single open literal → force x" + Math.abs(last) + " = " + (last > 0), "var", Math.abs(last), "value", last > 0, "depth", depth, "assign", assign.clone());
                }
            }
        }
        int pick = 0;
        for (int[] c : cnf) {
            boolean sat = false;
            int cand = 0;
            for (int lit : c) {
                int a = assign[Math.abs(lit)];
                if (a == 0) { if (cand == 0) cand = Math.abs(lit); }
                else if ((a > 0) == (lit > 0)) { sat = true; break; }
            }
            if (!sat && cand != 0) { pick = cand; break; }
        }
        if (pick == 0) return true;
        for (int val : new int[]{1, -1}) {
            int[] copy = assign.clone();
            copy[pick] = val;
            ctx.stat("decisions", 1);
            if (ctx.tracing()) ctx.trace.emit("decide", "Decide x" + pick + " = " + (val > 0) + " (depth " + (depth + 1) + ").", "var", pick, "value", val > 0, "depth", depth + 1, "assign", copy.clone());
            if (solve(cnf, copy, depth + 1, ctx)) { System.arraycopy(copy, 0, assign, 0, copy.length); return true; }
            if (ctx.tracing()) ctx.trace.emit("backtrack", "x" + pick + " = " + (val > 0) + " fails → undo.", "var", pick, "depth", depth + 1);
        }
        return false;
    }

    public static boolean satisfies(int[][] cnf, int[] assign) {
        for (int[] c : cnf) {
            boolean ok = false;
            for (int lit : c) { int a = assign[Math.abs(lit)]; if (a != 0 && (a > 0) == (lit > 0)) { ok = true; break; } }
            if (!ok) return false;
        }
        return true;
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        if (in.cnf.length == 0) throw new IllegalArgumentException("The policy file contains no clauses");
        int[] assign = new int[in.numVars + 1];
        boolean sat = solve(in.cnf, assign, 0, ctx);
        AlgorithmResult r = new AlgorithmResult();
        Map<String, Boolean> model = new LinkedHashMap<>();
        if (sat) {
            for (int v = 1; v <= in.numVars; v++) {
                String name = v - 1 < in.names.size() ? in.names.get(v - 1) : "x" + v;
                boolean val = assign[v] >= 0;
                model.put(name, val);
                r.item("variable", name, "value", val);
            }
        }
        boolean verified = !sat || satisfies(in.cnf, fillUnset(assign));
        r.headline = sat ? "Policies are satisfiable - a consistent configuration exists" : "Policies are UNSATISFIABLE - the constraints contradict each other";
        r.put("satisfiable", sat).put("variables", in.numVars).put("clauses", in.cnf.length).put("decisions", ctx.stat("decisions"))
                .put("propagations", ctx.stat("propagations")).put("conflicts", ctx.stat("conflicts")).put("verified", verified).put("model", model).check(sat);
        return r;
    }

    private static int[] fillUnset(int[] a) {
        int[] c = a.clone();
        for (int i = 1; i < c.length; i++) if (c[i] == 0) c[i] = 1;
        return c;
    }
}
