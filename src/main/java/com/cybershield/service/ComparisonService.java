package com.cybershield.service;

import com.cybershield.algorithm.*;
import com.cybershield.dataset.Dataset;
import com.cybershield.execution.*;
import java.util.*;

/** Runs several algorithms on the same dataset and checks that they agree (algorithm-to-algorithm comparison). */
public final class ComparisonService {
    private final AlgorithmRegistry registry;
    private final ExecutionEngine engine;
    private final InputFactory inputs;

    public ComparisonService(AlgorithmRegistry registry, ExecutionEngine engine, InputFactory inputs) {
        this.registry = registry; this.engine = engine; this.inputs = inputs;
    }

    public Map<String, Object> compare(List<String> ids, Dataset d, Map<String, Object> req) {
        if (ids.size() < 2) throw new IllegalArgumentException("Select at least two algorithms to compare");
        List<Map<String, Object>> runs = new ArrayList<>();
        Set<Object> checks = new LinkedHashSet<>();
        Set<String> groups = new LinkedHashSet<>();
        Map<String, Object> checkByAlgo = new LinkedHashMap<>();
        for (String id : ids) {
            Algorithm a = registry.get(id);
            groups.add(a.info().group());
            AlgorithmInput in = inputs.build(a.info(), d, req, false);
            RunRecord r = engine.execute(a, in, d.id(), false, 0);
            Map<String, Object> m = r.toSummaryMap();
            if (r.ok() && r.result.summary.containsKey("checkValue")) {
                Object v = r.result.summary.get("checkValue");
                m.put("checkValue", v);
                checks.add(String.valueOf(v));
                checkByAlgo.put(id, v);
            }
            runs.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataset", d.id());
        out.put("runs", runs);
        out.put("sameFamily", groups.size() == 1);
        out.put("consistent", checks.size() <= 1);
        out.put("checkValues", checkByAlgo);
        return out;
    }
}
