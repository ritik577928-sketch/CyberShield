package com.cybershield.api;

import com.cybershield.algorithm.*;
import com.cybershield.dataset.Dataset;
import com.cybershield.execution.RunRecord;
import com.cybershield.metrics.MemoryProbe;
import com.cybershield.service.*;
import com.cybershield.util.Json;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/** REST API + static hosting of the dashboard, on the JDK's built-in HTTP server (no external dependencies). */
public final class ApiServer {
    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final Map<String, String> MIME = Map.of("html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "json", "application/json", "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon");
    private final AppContext app;
    private final long started = System.currentTimeMillis();
    private HttpServer server;
    private ExecutorService pool;

    public ApiServer(AppContext app) { this.app = app; }

    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        pool = Executors.newFixedThreadPool(8, r -> { Thread t = new Thread(r, "cybershield-http"); t.setDaemon(true); return t; });
        server.setExecutor(pool);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.start();
        int bound = server.getAddress().getPort();
        LOG.info("CyberShield dashboard listening on http://localhost:" + bound);
        return bound;
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    // ------------------------------------------------------------------ static files
    private void handleStatic(HttpExchange ex) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            if (path.contains("..") || path.contains("\\")) { send(ex, 400, "text/plain", "Bad path".getBytes(StandardCharsets.UTF_8)); return; }
            byte[] body = null;
            Path fs = Paths.get("dashboard", path.substring(1));
            if (Files.isRegularFile(fs)) body = Files.readAllBytes(fs);
            else try (InputStream in = ApiServer.class.getResourceAsStream("/dashboard" + path)) { if (in != null) body = in.readAllBytes(); }
            if (body == null) { send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8)); return; }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            send(ex, 200, MIME.getOrDefault(ext, "application/octet-stream"), body);
        }
    }

    // ------------------------------------------------------------------ API
    private void handleApi(HttpExchange ex) throws IOException {
        try (ex) {
            Object result;
            int status = 200;
            try {
                result = route(ex);
            } catch (Json.JsonException | IllegalArgumentException e) {
                status = 400; result = Map.of("error", e.getMessage() == null ? "Bad request" : e.getMessage());
            } catch (NoSuchElementException e) {
                status = 404; result = Map.of("error", e.getMessage());
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Unhandled error for " + ex.getRequestURI(), e);
                status = 500; result = Map.of("error", "Internal error: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            }
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            send(ex, status, "application/json; charset=utf-8", Json.write(result).getBytes(StandardCharsets.UTF_8));
        }
    }

    private Object route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath().substring(4); // strip "/api"
        Map<String, String> q = query(ex.getRequestURI());
        String[] seg = path.replaceAll("^/|/$", "").split("/");
        String head = seg[0];
        if (method.equals("GET")) {
            switch (head) {
                case "health": return health();
                case "status": return status();
                case "algorithms": return algorithms();
                case "datasets":
                    if (seg.length == 1) return datasets();
                    if (seg.length == 3 && seg[2].equals("profile")) return app.profiler.profile(app.datasets.get(seg[1]));
                    if (seg.length == 3 && seg[2].equals("samples")) return Map.of("samples", app.detection.attackSamples(app.datasets.get(seg[1]), 12));
                    break;
                case "scan": return scan(app.datasets.get(q.get("dataset")), Integer.parseInt(q.getOrDefault("limit", "300")));
                case "history": return Map.of("runs", app.history.list(Integer.parseInt(q.getOrDefault("limit", "300"))));
                default: break;
            }
        } else if (method.equals("POST")) {
            switch (head) {
                case "run": return run(body(ex));
                case "compare": return compare(body(ex));
                case "benchmark": return benchmark(body(ex));
                case "datasets":
                    if (seg.length == 2 && seg[1].equals("upload")) {
                        Dataset d = app.datasets.upload(q.get("name"), new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                        app.detection.invalidate(d.id());
                        return Map.of("id", d.id(), "name", d.name(), "lines", d.corpus().lines.size());
                    }
                    break;
                default: break;
            }
        } else if (method.equals("DELETE") && head.equals("history")) {
            app.history.clear();
            return Map.of("cleared", true);
        }
        throw new NoSuchElementException("No such endpoint: " + method + " " + ex.getRequestURI().getPath());
    }

    private Object health() {
        return Map.of("status", "UP", "java", System.getProperty("java.version"), "uptimeMs", System.currentTimeMillis() - started,
                "algorithms", app.registry.all().size(), "exactMemoryProbe", MemoryProbe.exact());
    }

    private Object status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeRuns", app.engine.activeRuns());
        m.put("running", app.engine.runningAlgorithms());
        m.put("lastRuns", app.engine.lastRuns());
        m.put("heapUsedMB", MemoryProbe.heapUsed() / 1_048_576);
        m.put("processors", Runtime.getRuntime().availableProcessors());
        m.put("uptimeMs", System.currentTimeMillis() - started);
        return m;
    }

    private Object algorithms() {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Algorithm a : app.registry.all()) l.add(a.info().toMap());
        return Map.of("algorithms", l);
    }

    private Object datasets() {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Dataset d : app.datasets.list()) l.add(Map.of("id", d.id(), "name", d.name(), "description", d.description(), "custom", d.custom()));
        return Map.of("datasets", l);
    }

    private Object scan(Dataset d, int limit) {
        DetectionService.Scan s = app.detection.scan(d);
        Map<String, Object> out = new LinkedHashMap<>(s.json);
        out.put("detections", s.detections.size() > limit ? s.detections.subList(0, limit) : s.detections);
        out.put("detectionsTotal", s.detections.size());
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object run(Map<String, Object> req) {
        Algorithm a = app.registry.get(String.valueOf(req.get("algorithm")));
        Dataset d = app.datasets.get((String) req.get("dataset"));
        boolean visualize = !"full".equals(req.get("mode"));
        int limit = req.get("traceLimit") instanceof Number n ? Math.max(100, Math.min(20_000, n.intValue())) : 6000;
        AlgorithmInput in = app.inputs.build(a.info(), d, req, visualize);
        RunRecord r = app.engine.execute(a, in, d.id(), visualize, limit);
        Map<String, Object> out = r.toFullMap(visualize ? 400 : 200);
        out.put("mode", visualize ? "visualize" : "full");
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("text", in.text.length() <= 3000 ? in.text : null);
        preview.put("patterns", in.patterns.size() <= 40 ? in.patterns : null);
        preview.put("tokens", in.tokens.size() <= 40 ? in.tokens : null);
        out.put("inputPreview", preview);
        return out;
    }

    private Object compare(Map<String, Object> req) {
        List<String> ids = new ArrayList<>();
        if (req.get("algorithms") instanceof List<?> l) for (Object o : l) ids.add(String.valueOf(o));
        return app.comparison.compare(ids, app.datasets.get((String) req.get("dataset")), req);
    }

    private Object benchmark(Map<String, Object> req) {
        List<Integer> sizes = new ArrayList<>();
        if (req.get("sizes") instanceof List<?> l) for (Object o : l) if (o instanceof Number n) sizes.add(n.intValue());
        if (sizes.isEmpty()) sizes = List.of(10_000, 25_000, 50_000, 100_000, 200_000);
        int repeats = req.get("repeats") instanceof Number n ? n.intValue() : 3;
        return app.benchmark.run(String.valueOf(req.getOrDefault("group", "string-matching")), sizes, repeats, app.datasets.get((String) req.get("dataset")));
    }

    // ------------------------------------------------------------------ helpers
    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        String s = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return s.isBlank() ? new LinkedHashMap<>() : Json.asObject(Json.parse(s));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> m = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) ex.getResponseBody().write(body);
    }
}
