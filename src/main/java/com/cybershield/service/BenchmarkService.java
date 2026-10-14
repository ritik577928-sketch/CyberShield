package com.cybershield.service;

import com.cybershield.algorithm.*;
import com.cybershield.dataset.Dataset;
import com.cybershield.execution.*;
import java.util.*;

/** Measures algorithms of one group on growing slices of the real corpus (median of several repetitions). */
public final class BenchmarkService {
    private final AlgorithmRegistry registry;
    private final ExecutionEngine engine;
    private final InputFactory inputs;

    public BenchmarkService(AlgorithmRegistry registry, ExecutionEngine engine, InputFactory inputs) {
        this.registry = registry; this.engine = engine; this.inputs = inputs;
    }

    public Map<String, Object> run(String group, List<Integer> sizes, int repeats, Dataset d) {
        List<String> ids = switch (group) {
            case "string-matching" -> List.of("kmp", "z", "rabinkarp", "ahocorasick");
            case "suffix-structures" -> List.of("suffixarray", "sais", "suffixautomaton");
            default -> throw new IllegalArgumentException("Benchmarks are available for: string-matching, suffix-structures");
        };
        repeats = Math.max(1, Math.min(repeats, 9));
        String full = d.corpus().text;
        Map<String, List<Map<String, Object>>> series = new LinkedHashMap<>();
        List<Integer> used = new ArrayList<>();
        for (int size : sizes) {
            int n = Math.min(size, full.length());
            if (n < 100 || used.contains(n)) continue;
            used.add(n);
            for (String id : ids) {
                Algorithm a = registry.get(id);
                AlgorithmInput in = inputs.build(a.info(), d, Map.of(), false);
                in.text = full.substring(0, n);
                List<RunRecord> rs = new ArrayList<>();
                engine.measure(a, in, d.id(), false, 0); // warm-up
                for (int i = 0; i < repeats; i++) rs.add(engine.measure(a, in, d.id(), false, 0));
                rs.sort(Comparator.comparingLong(x -> x.nanos));
                RunRecord med = rs.get(rs.size() / 2);
                med.input = n + " chars (median of " + repeats + ")";
                med.datasetId = d.id() + " · benchmark";
                engine.record(med); // one history row per (algorithm, size)
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("size", n);
                p.put("timeMs", Math.round(med.millis() * 1000) / 1000.0);
                p.put("comparisons", med.comparisons);
                p.put("operations", med.operations);
                p.put("memoryBytes", med.memoryBytes);
                p.put("ok", med.ok());
                series.computeIfAbsent(id, k -> new ArrayList<>()).add(p);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("group", group);
        out.put("sizes", used);
        out.put("repeats", repeats);
        out.put("series", series);
        return out;
    }
}
