package com.cybershield.execution;

import com.cybershield.algorithm.AlgorithmInfo;
import com.cybershield.algorithm.AlgorithmResult;
import java.util.*;

/** Everything known about one algorithm execution: status, metrics, result and (optionally) the trace. */
public final class RunRecord {
    public long id;
    public long timestamp;
    public AlgorithmInfo info;
    public String datasetId = "", input = "", status = "SUCCESS", error = "";
    public long nanos, comparisons, operations, memoryBytes;
    public AlgorithmResult result;
    public Trace trace = Trace.off();

    public boolean ok() { return "SUCCESS".equals(status); }
    public double millis() { return nanos / 1_000_000.0; }

    /** Compact form without result items or trace (history, comparison tables). */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("timestamp", timestamp);
        m.put("algorithm", info.id());
        m.put("name", info.name());
        m.put("module", info.module());
        m.put("group", info.group());
        m.put("dataset", datasetId);
        m.put("input", input);
        m.put("status", status);
        m.put("error", error);
        m.put("timeMs", Math.round(millis() * 1000) / 1000.0);
        m.put("comparisons", comparisons);
        m.put("operations", operations);
        m.put("memoryBytes", memoryBytes);
        m.put("timeComplexity", info.time());
        m.put("spaceComplexity", info.space());
        m.put("headline", result == null ? error : result.headline);
        return m;
    }

    public Map<String, Object> toFullMap(int maxItems) {
        Map<String, Object> m = toSummaryMap();
        m.put("info", info.toMap());
        if (result != null) {
            m.put("summary", result.summary);
            m.put("itemsTotal", result.itemsTotal);
            m.put("items", result.items.size() > maxItems ? result.items.subList(0, maxItems) : result.items);
        }
        if (trace.enabled()) {
            m.put("trace", trace.events());
            m.put("traceTruncated", trace.truncated());
        }
        return m;
    }
}
