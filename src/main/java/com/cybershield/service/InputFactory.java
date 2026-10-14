package com.cybershield.service;

import com.cybershield.algorithm.*;
import com.cybershield.algorithm.dp.EditDistance;
import com.cybershield.dataset.*;
import com.cybershield.dataset.Models.*;
import java.util.*;

/** Turns real dataset content (or an explicit snippet from the dashboard) into the input an algorithm needs. */
public final class InputFactory {
    private final DetectionService detection;

    public InputFactory(DetectionService detection) { this.detection = detection; }

    @SuppressWarnings("unchecked")
    public AlgorithmInput build(AlgorithmInfo info, Dataset d, Map<String, Object> req, boolean forTrace) {
        AlgorithmInput in = new AlgorithmInput();
        Object params = req.get("params");
        if (params instanceof Map<?, ?> pm) for (Map.Entry<?, ?> e : pm.entrySet()) if (e.getValue() instanceof Number n) in.params.put(String.valueOf(e.getKey()), n.doubleValue());
        switch (info.kind()) {
            case TEXT_PATTERNS -> stringInput(in, d, req, forTrace);
            case TEXT -> textInput(in, info, d, req, forTrace);
            case TOKENS_NAMES -> tokenInput(in, d, req, forTrace);
            case RULES -> { in.rules = d.rules(); in.names = d.categories(); }
            case DIMS -> { Pipeline p = d.pipeline(); in.ints = p.dims(); in.names = p.names(); }
            case NETWORK -> { Topology t = d.topology(); in.edges = t.edges(); in.names = t.names(); in.source = t.source(); in.sink = t.sink(); }
            case BIPARTITE -> { Assignments a = d.assignments(); in.names = a.names(); in.leftCount = a.left(); in.rightCount = a.right(); in.edges = a.edges(); }
            case CNF -> { Cnf c = d.cnf(); in.cnf = c.clauses(); in.numVars = c.vars(); in.names = c.names(); }
            case GRAPH -> { Graph g = d.dependencies(); in.names = g.names(); in.edges = g.edges(); }
            case NUMBERS -> { Numbers n = d.numbers(); in.tokens = n.values(); in.names = n.labels(); }
            case SEVERITIES -> severities(in, d, forTrace);
            case STREAM -> stream(in, d, forTrace);
            case SERIES -> series(in, d, forTrace);
        }
        return in;
    }

    // ------------------------------------------------------------------ text based inputs
    private void stringInput(AlgorithmInput in, Dataset d, Map<String, Object> req, boolean forTrace) {
        List<String> all = distinctPatterns(d);
        String explicit = str(req.get("text"));
        Integer lineIdx = intOf(req.get("line"));
        String line = null;
        if (!explicit.isBlank()) line = Preprocessor.normalize(explicit);
        else if (lineIdx != null) line = d.corpus().lines.get(lineIdx).norm();
        else if (forTrace) line = firstAttackNorm(d);
        List<String> given = strList(req.get("patterns"));
        if (line == null) {
            in.text = d.corpus().text;
            in.patterns = given.isEmpty() ? all : lower(given);
            return;
        }
        List<String> hits = new ArrayList<>(), misses = new ArrayList<>();
        for (String p : (given.isEmpty() ? all : lower(given))) (line.contains(p) ? hits : misses).add(p);
        if (forTrace && explicit.isBlank()) {
            int first = hits.isEmpty() ? 0 : line.indexOf(hits.get(0));
            int start = Math.max(0, first - 30);
            line = line.substring(start, Math.min(line.length(), start + 120));
            List<String> kept = new ArrayList<>();
            for (String p : hits) if (line.contains(p) && kept.size() < 2) kept.add(p);
            if (!misses.isEmpty()) kept.add(misses.get(0));
            in.patterns = kept;
        } else {
            in.patterns = new ArrayList<>(hits);
            if (forTrace && !misses.isEmpty()) in.patterns.add(misses.get(0));
            if (in.patterns.isEmpty()) in.patterns = given.isEmpty() ? all : lower(given);
        }
        in.text = line;
    }

    private void textInput(AlgorithmInput in, AlgorithmInfo info, Dataset d, Map<String, Object> req, boolean forTrace) {
        String explicit = str(req.get("text"));
        Integer lineIdx = intOf(req.get("line"));
        in.patterns = distinctPatterns(d);
        if (!explicit.isBlank()) { in.text = Preprocessor.normalize(explicit); if (forTrace && in.text.length() > 120) in.text = in.text.substring(0, 120); return; }
        if (forTrace || lineIdx != null) {
            String line = lineIdx != null ? d.corpus().lines.get(lineIdx).norm() : firstAttackNorm(d);
            int first = 0;
            for (String p : in.patterns) { int i = line.indexOf(p); if (i >= 0) { first = i; break; } }
            int start = Math.max(0, first - 6);
            in.text = line.substring(start, Math.min(line.length(), start + 26));
            in.patterns = new ArrayList<>(in.patterns.stream().filter(in.text::contains).limit(3).toList());
            return;
        }
        int cap = (int) in.param("maxChars", info.id().equals("suffixautomaton") ? 30_000 : 60_000);
        String t = d.corpus().text;
        in.text = t.length() > cap ? t.substring(0, cap) : t;
    }

    private void tokenInput(AlgorithmInput in, Dataset d, Map<String, Object> req, boolean forTrace) {
        List<String> names = new ArrayList<>(), cats = new ArrayList<>();
        for (String[] p : d.processNames()) { cats.add(p[0]); names.add(p[1].toLowerCase(Locale.ROOT)); }
        String explicit = str(req.get("text"));
        if (!explicit.isBlank()) {
            in.tokens = new ArrayList<>(List.of(explicit.toLowerCase(Locale.ROOT).trim()));
            in.patterns = names; in.names = cats;
            return;
        }
        List<String> tokens = d.processEventTokens();
        if (!forTrace) { in.tokens = tokens; in.patterns = names; in.names = cats; return; }
        List<String> chosen = new ArrayList<>(), pats = new ArrayList<>(), pcat = new ArrayList<>();
        for (String t : tokens) {
            int bi = -1; double bd = 1e9;
            for (int i = 0; i < names.size(); i++) { double x = EditDistance.distance(t, names.get(i), true); if (x < bd) { bd = x; bi = i; } }
            if (bd > 0 && bd / Math.max(t.length(), names.get(bi).length()) <= 0.2) {
                chosen.add(t);
                if (!pats.contains(names.get(bi))) { pats.add(names.get(bi)); pcat.add(cats.get(bi)); }
                if (chosen.size() >= 2) break;
            }
        }
        for (int i = 0; i < names.size() && pats.size() < 3; i++) if (!pats.contains(names.get(i))) { pats.add(names.get(i)); pcat.add(cats.get(i)); }
        in.tokens = chosen; in.patterns = pats; in.names = pcat;
    }

    // ------------------------------------------------------------------ analytics inputs
    private void severities(AlgorithmInput in, Dataset d, boolean forTrace) {
        DetectionService.Scan s = detection.scan(d);
        int n = forTrace ? Math.min(16, s.hits.size()) : s.hits.size();
        int start = forTrace ? Math.max(0, s.hits.size() / 2 - n / 2) : 0;
        in.ints = new int[n];
        for (int i = 0; i < n; i++) {
            DetectionService.Hit h = s.hits.get(start + i);
            in.ints[i] = h.severity * 1_000_000 + (999_999 - i);
            Corpus.LogLine l = s.corpus.lines.get(h.line);
            in.names.add(String.join("+", h.categories) + " · " + l.source() + ":" + l.number());
        }
    }

    private void stream(AlgorithmInput in, Dataset d, boolean forTrace) {
        Corpus c = d.corpus();
        in.patterns = distinctPatterns(d);
        if (!forTrace) { for (Corpus.LogLine l : c.lines) in.lines.add(l.norm()); in.params.putIfAbsent("k", 200.0); return; }
        DetectionService.Scan s = detection.scan(d);
        int first = s.hits.isEmpty() ? 0 : s.hits.get(s.hits.size() / 3).line;
        int start = Math.max(0, Math.min(first - 15, c.lines.size() - 40));
        for (int i = start; i < Math.min(c.lines.size(), start + 40); i++) in.lines.add(c.lines.get(i).norm());
        in.params.put("k", 5.0);
    }

    private void series(AlgorithmInput in, Dataset d, boolean forTrace) {
        int[] all = d.flowsPerMinute();
        if (all.length == 0) throw new IllegalArgumentException("This dataset has no NetFlow data");
        if (!forTrace || all.length <= 16) { in.ints = all; return; }
        int peak = 0;
        for (int i = 0; i < all.length; i++) if (all[i] > all[peak]) peak = i;
        int start = Math.max(0, Math.min(peak - 8, all.length - 16));
        in.ints = Arrays.copyOfRange(all, start, start + 16);
    }

    // ------------------------------------------------------------------ helpers
    private String firstAttackNorm(Dataset d) {
        List<Map<String, Object>> samples = detection.attackSamples(d, 1);
        if (samples.isEmpty()) return d.corpus().lines.isEmpty() ? "" : d.corpus().lines.get(0).norm();
        return (String) samples.get(0).get("norm");
    }

    private List<String> distinctPatterns(Dataset d) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (Signature sig : d.signatures()) s.add(sig.pattern());
        return new ArrayList<>(s);
    }

    private static List<String> lower(List<String> l) { return l.stream().map(x -> x.toLowerCase(Locale.ROOT)).toList(); }
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static Integer intOf(Object o) { return o instanceof Number n ? n.intValue() : null; }
    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x != null && !String.valueOf(x).isEmpty()) out.add(String.valueOf(x));
        return out;
    }
}
