package com.cybershield.algorithm;

import java.util.LinkedHashMap;
import java.util.Map;

/** Static description of an algorithm: where it fits in the syllabus, complexity and cybersecurity use. */
public record AlgorithmInfo(String id, String name, String module, String co, String group, InputKind kind,
                            String time, String space, String description, String useCase) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("module", module); m.put("co", co); m.put("group", group);
        m.put("kind", kind.name()); m.put("time", time); m.put("space", space);
        m.put("description", description); m.put("useCase", useCase);
        return m;
    }
}
