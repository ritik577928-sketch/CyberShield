package com.cybershield.execution;

import java.util.*;

/**
 * Execution trace recorded by an algorithm WHILE it runs. Every animation in the dashboard is
 * replayed from these events - nothing is scripted on the front end.
 */
public final class Trace {
    private final boolean enabled;
    private final int limit;
    private final List<Map<String, Object>> events = new ArrayList<>();
    private boolean truncated;

    private Trace(boolean enabled, int limit) { this.enabled = enabled; this.limit = limit; }

    public static Trace off() { return new Trace(false, 0); }
    public static Trace on(int limit) { return new Trace(true, Math.max(1, limit)); }

    public boolean enabled() { return enabled; }

    /** Cheap guard: true while more events can be stored (call before building expensive payloads). */
    public boolean room() {
        if (!enabled) return false;
        if (events.size() >= limit) { truncated = true; return false; }
        return true;
    }

    /** Record an event: type, human explanation, then key/value pairs of state. */
    public void emit(String type, String message, Object... kv) {
        if (!room()) return;
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("t", type);
        e.put("msg", message);
        for (int i = 0; i + 1 < kv.length; i += 2) e.put(String.valueOf(kv[i]), kv[i + 1]);
        events.add(e);
    }

    public List<Map<String, Object>> events() { return events; }
    public boolean truncated() { return truncated; }
}
