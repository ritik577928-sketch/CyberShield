package com.cybershield.dataset;

import com.cybershield.dataset.Models.Cnf;
import java.util.*;

/** Parser for the standard DIMACS CNF format (plus "c var N = name" comments used to label variables). */
public final class DimacsParser {
    private DimacsParser() {}

    public static Cnf parse(String text) {
        int vars = -1, declared = -1;
        Map<Integer, String> names = new TreeMap<>();
        List<int[]> clauses = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("c")) {
                String[] p = line.split("\\s+");
                if (p.length >= 5 && p[1].equals("var") && p[3].equals("=")) names.put(Integer.parseInt(p[2]), p[4]);
                continue;
            }
            if (line.startsWith("p")) {
                String[] p = line.split("\\s+");
                if (p.length != 4 || !p[1].equals("cnf")) throw new IllegalArgumentException("Bad DIMACS header: " + line);
                vars = Integer.parseInt(p[2]);
                declared = Integer.parseInt(p[3]);
                continue;
            }
            if (vars < 0) throw new IllegalArgumentException("DIMACS clause found before the 'p cnf' header");
            for (String tok : line.split("\\s+")) {
                int lit = Integer.parseInt(tok);
                if (lit == 0) { clauses.add(cur.stream().mapToInt(Integer::intValue).toArray()); cur.clear(); }
                else {
                    if (Math.abs(lit) > vars) throw new IllegalArgumentException("Literal " + lit + " exceeds declared variable count " + vars);
                    cur.add(lit);
                }
            }
        }
        if (!cur.isEmpty()) throw new IllegalArgumentException("Last DIMACS clause is not terminated by 0");
        if (declared >= 0 && declared != clauses.size())
            throw new IllegalArgumentException("DIMACS header declares " + declared + " clauses but " + clauses.size() + " were found");
        List<String> nameList = new ArrayList<>();
        for (int v = 1; v <= Math.max(vars, 0); v++) nameList.add(names.getOrDefault(v, "x" + v));
        return new Cnf(Math.max(vars, 0), clauses.toArray(new int[0][]), nameList);
    }
}
