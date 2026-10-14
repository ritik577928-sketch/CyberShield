package com.cybershield.service;

import com.cybershield.execution.RunRecord;
import com.cybershield.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

/** Keeps the execution history in memory and appends it to a JSON-lines file so it survives restarts. */
public final class HistoryService {
    private static final Logger LOG = Logger.getLogger(HistoryService.class.getName());
    private static final int MAX = 2000;
    private final Path file;
    private final Deque<Map<String, Object>> entries = new ArrayDeque<>();

    public HistoryService(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            try {
                for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (l.isBlank()) continue;
                    try { push(Json.asObject(Json.parse(l))); } catch (RuntimeException bad) { LOG.warning("Skipping unreadable history line: " + bad.getMessage()); }
                }
            } catch (IOException e) { LOG.log(Level.WARNING, "Cannot read history file " + file, e); }
        }
    }

    private void push(Map<String, Object> m) {
        entries.addFirst(m);
        while (entries.size() > MAX) entries.removeLast();
    }

    public synchronized void add(RunRecord r) {
        Map<String, Object> m = r.toSummaryMap();
        push(m);
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(m) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { LOG.log(Level.WARNING, "Cannot append to history file", e); }
    }

    public synchronized List<Map<String, Object>> list(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : entries) { if (out.size() >= limit) break; out.add(m); }
        return out;
    }

    public synchronized void clear() {
        entries.clear();
        try { if (file != null) Files.deleteIfExists(file); } catch (IOException e) { LOG.log(Level.WARNING, "Cannot delete history file", e); }
    }
}
