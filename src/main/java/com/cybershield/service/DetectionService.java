package com.cybershield.service;

import com.cybershield.algorithm.AlgorithmInput;
import com.cybershield.algorithm.dp.EditDistance;
import com.cybershield.algorithm.random.ParallelReduce;
import com.cybershield.algorithm.string.*;
import com.cybershield.dataset.*;
import com.cybershield.dataset.Corpus.LogLine;
import com.cybershield.dataset.Models.*;
import com.cybershield.execution.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * The detection pipeline: Input → Preprocessing → Aho-Corasick signature detection → KMP cross-check →
 * evaluation against ground-truth labels → network and process analytics.
 */
public final class DetectionService {
    private static final Logger LOG = Logger.getLogger(DetectionService.class.getName());
    private static final Pattern IP = Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{1,3}){3})\\b");

    public static final class Hit {
        public final int line;
        public final Set<String> categories = new LinkedHashSet<>();
        public int severity;
        public final List<String> patterns = new ArrayList<>();
        Hit(int line) { this.line = line; }
    }

    public static final class Scan {
        public Dataset dataset;
        public Corpus corpus;
        public List<Hit> hits = new ArrayList<>();
        public Map<String, Object> json = new LinkedHashMap<>();
        public List<Map<String, Object>> detections = new ArrayList<>();
        public Map<String, Signature> signatureByPattern = new LinkedHashMap<>();
    }

    private final ExecutionEngine engine;
    private final Map<String, Scan> cache = new ConcurrentHashMap<>();

    public DetectionService(ExecutionEngine engine) { this.engine = engine; }

    public void invalidate(String datasetId) { cache.remove(datasetId); }

    public Scan scan(Dataset d) {
        Scan s = cache.get(d.id());
        if (s != null) return s;
        synchronized (this) {
            s = cache.get(d.id());
            if (s == null) { s = doScan(d); cache.put(d.id(), s); }
            return s;
        }
    }

    private Scan doScan(Dataset d) {
        Scan s = new Scan();
        s.dataset = d;
        s.corpus = d.corpus();
        Corpus corpus = s.corpus;
        for (Signature sig : d.signatures()) {
            Signature old = s.signatureByPattern.get(sig.pattern());
            if (old == null || sig.severity() > old.severity()) s.signatureByPattern.put(sig.pattern(), sig);
        }
        List<String> patterns = new ArrayList<>(s.signatureByPattern.keySet());
        AlgorithmInput in = AlgorithmInput.ofText(corpus.text);
        in.patterns = patterns;

        RunRecord ac = engine.execute(new AhoCorasickSearch(), in, d.id(), false, 0);
        RunRecord kmp = engine.execute(new KmpSearch(), in, d.id(), false, 0);
        if (!ac.ok()) throw new IllegalStateException("Aho-Corasick scan failed: " + ac.error);

        Map<Integer, Hit> byLine = new TreeMap<>();
        for (Map<String, Object> m : ac.result.items) {
            int line = corpus.lineOf((Integer) m.get("start"));
            String pat = (String) m.get("pattern");
            Signature sig = s.signatureByPattern.get(pat);
            Hit h = byLine.computeIfAbsent(line, Hit::new);
            h.categories.add(sig.category());
            h.severity = Math.max(h.severity, sig.severity());
            if (!h.patterns.contains(pat)) h.patterns.add(pat);
        }
        s.hits = new ArrayList<>(byLine.values());

        // ---------------------------------------------------------------- detections list + aggregates
        Map<String, Integer> byCategory = new LinkedHashMap<>(), bySource = new LinkedHashMap<>(), ips = new HashMap<>();
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        for (String k : new String[]{"critical", "high", "medium", "low"}) bySeverity.put(k, 0);
        Map<String, String> labels = d.labels();
        for (Hit h : s.hits) {
            LogLine l = corpus.lines.get(h.line);
            for (String c : h.categories) byCategory.merge(c, 1, Integer::sum);
            bySource.merge(l.source(), 1, Integer::sum);
            bySeverity.merge(h.severity >= 10 ? "critical" : h.severity >= 8 ? "high" : h.severity >= 5 ? "medium" : "low", 1, Integer::sum);
            Matcher m = IP.matcher(l.raw());
            if (m.find()) ips.merge(m.group(1), 1, Integer::sum);
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("id", h.line);
            det.put("source", l.source());
            det.put("line", l.number());
            det.put("severity", h.severity);
            det.put("categories", h.categories);
            det.put("patterns", h.patterns);
            det.put("text", l.raw().length() > 360 ? l.raw().substring(0, 360) + "…" : l.raw());
            det.put("label", labels.getOrDefault(l.source() + ":" + l.number(), ""));
            s.detections.add(det);
        }
        s.detections.sort((a, b) -> {
            int c = Integer.compare((Integer) b.get("severity"), (Integer) a.get("severity"));
            return c != 0 ? c : Integer.compare((Integer) a.get("id"), (Integer) b.get("id"));
        });
        List<Map<String, Object>> topIps = new ArrayList<>();
        ips.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8).forEach(e -> topIps.add(Map.of("ip", e.getKey(), "count", e.getValue())));

        // ---------------------------------------------------------------- evaluation against ground truth
        Map<String, Object> eval = null;
        if (!labels.isEmpty()) {
            Set<String> logSources = new HashSet<>();
            for (LogLine l : corpus.lines) logSources.add(l.source());
            Set<String> truth = new HashSet<>(), predicted = new HashSet<>();
            Map<String, String> truthLabel = new HashMap<>();
            for (Map.Entry<String, String> e : labels.entrySet())
                if (logSources.contains(e.getKey().substring(0, e.getKey().indexOf(':')))) { truth.add(e.getKey()); truthLabel.put(e.getKey(), e.getValue()); }
            for (Hit h : s.hits) predicted.add(corpus.lines.get(h.line).source() + ":" + corpus.lines.get(h.line).number());
            int tp = 0, fp = 0, fn = 0;
            Map<String, Integer> missed = new TreeMap<>();
            for (String k : predicted) if (truth.contains(k)) tp++; else fp++;
            for (String k : truth) if (!predicted.contains(k)) { fn++; missed.merge(truthLabel.get(k), 1, Integer::sum); }
            eval = new LinkedHashMap<>();
            eval.put("labelledAttackLines", truth.size());
            eval.put("truePositives", tp); eval.put("falsePositives", fp); eval.put("falseNegatives", fn);
            eval.put("precision", tp + fp == 0 ? 1.0 : (double) tp / (tp + fp));
            eval.put("recall", truth.isEmpty() ? 1.0 : (double) tp / truth.size());
            eval.put("missedByLabel", missed);
            eval.put("note", "Ground truth comes from labels.csv written by the dataset generator. Double-encoded payloads (…_EVASIVE) are expected misses: the preprocessor decodes only one layer.");
        }

        long kmpTotal = ((Number) kmp.result.summary.get("matches")).longValue(), acTotal = ((Number) ac.result.summary.get("matches")).longValue();
        List<Map<String, Object>> pipeline = new ArrayList<>();
        pipeline.add(stage("Input", "done", 0, corpus.lines.size() + " log lines, " + corpus.rawBytes() / 1024 + " KB"));
        pipeline.add(stage("Preprocessing", "done", d.corpusBuildMillis(), "URL-decode + lower-case → " + corpus.text.length() + " chars"));
        pipeline.add(stage("Detection · Aho-Corasick", "done", ac.millis(), acTotal + " matches / " + patterns.size() + " signatures"));
        pipeline.add(stage("Cross-check · KMP", kmpTotal == acTotal ? "done" : "failed", kmp.millis(), kmpTotal == acTotal ? "KMP agrees with Aho-Corasick" : "MISMATCH " + kmpTotal + " vs " + acTotal));
        pipeline.add(stage("Evaluation", "done", 0, eval == null ? "no ground-truth labels for this dataset" : String.format("precision %.1f%%, recall %.1f%%", 100 * (Double) eval.get("precision"), 100 * (Double) eval.get("recall"))));
        LOG.info("Scan of " + d.id() + ": " + s.hits.size() + " flagged lines, " + acTotal + " matches");

        s.json.put("dataset", Map.of("id", d.id(), "name", d.name(), "custom", d.custom()));
        s.json.put("totals", Map.of("lines", corpus.lines.size(), "flaggedLines", s.hits.size(), "matches", acTotal, "signatures", patterns.size(), "categoriesSeen", byCategory.size()));
        s.json.put("byCategory", byCategory);
        s.json.put("bySeverity", bySeverity);
        s.json.put("bySource", bySource);
        s.json.put("topIps", topIps);
        s.json.put("evaluation", eval);
        s.json.put("pipeline", pipeline);
        s.json.put("crossCheck", Map.of("aho", acTotal, "kmp", kmpTotal, "agree", kmpTotal == acTotal));
        s.json.put("detectionRun", ac.toSummaryMap());
        if (!d.custom()) {
            s.json.put("processes", processAnalysis(d, labels));
            s.json.put("traffic", trafficAnalysis(d, labels));
        }
        return s;
    }

    private static Map<String, Object> stage(String name, String status, double ms, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name); m.put("status", status); m.put("ms", Math.round(ms * 100) / 100.0); m.put("detail", detail);
        return m;
    }

    // ---------------------------------------------------------------- process names (edit distance)
    private Map<String, Object> processAnalysis(Dataset d, Map<String, String> labels) {
        AlgorithmInput in = new AlgorithmInput();
        for (String[] p : d.processNames()) { in.names.add(p[0]); in.patterns.add(p[1].toLowerCase(Locale.ROOT)); }
        in.tokens = new ArrayList<>(d.processEventTokens());
        RunRecord r = engine.execute(new EditDistance(), in, d.id(), false, 0);
        Map<String, Object> out = new LinkedHashMap<>();
        if (!r.ok()) { out.put("error", r.error); return out; }
        Set<String> flagged = new LinkedHashSet<>();
        for (Map<String, Object> it : r.result.items) flagged.add((String) it.get("token"));
        List<String> lines = d.processEventLines();
        Set<String> truthTokens = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : labels.entrySet())
            if (e.getKey().startsWith("process_events.txt:")) {
                String[] p = lines.get(Integer.parseInt(e.getKey().substring(19)) - 1).split(",");
                truthTokens.add(p[p.length - 1].trim());
            }
        int tp = 0;
        for (String t : flagged) if (truthTokens.contains(t)) tp++;
        out.put("flagged", r.result.items);
        out.put("uniqueTruth", truthTokens.size());
        out.put("truePositives", tp);
        out.put("falsePositives", flagged.size() - tp);
        out.put("recall", truthTokens.isEmpty() ? 1.0 : (double) tp / truthTokens.size());
        out.put("run", r.toSummaryMap());
        return out;
    }

    // ---------------------------------------------------------------- traffic analytics (parallel reduce)
    private Map<String, Object> trafficAnalysis(Dataset d, Map<String, String> labels) {
        Map<String, Object> out = new LinkedHashMap<>();
        int[] perMinute = d.flowsPerMinute();
        AlgorithmInput in = new AlgorithmInput();
        in.ints = perMinute;
        RunRecord r = engine.execute(new ParallelReduce(), in, d.id(), false, 0);
        out.put("perMinute", perMinute);
        if (r.ok()) { out.put("spikeMinutes", r.result.summary.get("spikeMinutes")); out.put("mean", r.result.summary.get("mean")); out.put("peak", r.result.summary.get("max")); }
        List<Flow> flows = d.flows();
        long min = flows.isEmpty() ? 0 : flows.get(0).ts();
        for (Flow f : flows) min = Math.min(min, f.ts());
        Set<Integer> ddos = new TreeSet<>();
        for (Map.Entry<String, String> e : labels.entrySet())
            if (e.getKey().startsWith("netflow.csv:") && e.getValue().equals("DDOS")) ddos.add((int) ((flows.get(Integer.parseInt(e.getKey().substring(12)) - 1).ts() - min) / 60));
        out.put("groundTruthDdosMinutes", ddos);
        Map<String, Set<Integer>> ports = new HashMap<>();
        for (Flow f : flows) ports.computeIfAbsent(f.src(), k -> new HashSet<>()).add(f.dport());
        List<Map<String, Object>> scanners = new ArrayList<>();
        ports.entrySet().stream().filter(e -> e.getValue().size() >= 100).forEach(e -> scanners.add(Map.of("src", e.getKey(), "distinctPorts", e.getValue().size())));
        out.put("portScanners", scanners);
        if (r.ok()) out.put("run", r.toSummaryMap());
        return out;
    }

    // ---------------------------------------------------------------- samples for the visualisation lab
    public List<Map<String, Object>> attackSamples(Dataset d, int n) {
        Scan s = scan(d);
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seenCat = new HashSet<>();
        for (Map<String, Object> det : s.detections) {
            String key = String.valueOf(det.get("categories"));
            if (seenCat.add(key) || out.size() < 3) {
                Map<String, Object> m = new LinkedHashMap<>(det);
                m.put("norm", s.corpus.lines.get((Integer) det.get("id")).norm());
                out.add(m);
                if (out.size() >= n) break;
            }
        }
        return out;
    }
}
