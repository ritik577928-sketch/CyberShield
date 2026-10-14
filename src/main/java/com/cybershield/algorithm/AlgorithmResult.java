package com.cybershield.algorithm;

import java.util.*;

/** Output of an algorithm: a headline, a key/value summary and a list of items (matches, picks, pairs...). */
public final class AlgorithmResult {
    public static final int ITEM_CAP = 200_000;

    public String headline = "";
    public final Map<String, Object> summary = new LinkedHashMap<>();
    public final List<Map<String, Object>> items = new ArrayList<>();
    public long itemsTotal;

    public AlgorithmResult put(String key, Object value) { summary.put(key, value); return this; }

    /** Value used by the comparison service to check that different algorithms agree. */
    public AlgorithmResult check(Object value) { summary.put("checkValue", value); return this; }

    public void item(Object... kv) {
        itemsTotal++;
        if (items.size() >= ITEM_CAP) return;
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        items.add(m);
    }
}
