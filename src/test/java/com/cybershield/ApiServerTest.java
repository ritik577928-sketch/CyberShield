package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.api.ApiServer;
import com.cybershield.service.AppContext;
import com.cybershield.util.Json;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ApiServerTest {
    static ApiServer server;
    static String base;
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll static void start() throws Exception {
        server = new ApiServer(new AppContext(null));
        base = "http://localhost:" + server.start(0);
    }

    @AfterAll static void stop() { server.stop(); }

    static HttpResponse<String> get(String p) throws Exception { return HTTP.send(HttpRequest.newBuilder(URI.create(base + p)).build(), HttpResponse.BodyHandlers.ofString()); }
    static HttpResponse<String> post(String p, String body) throws Exception { return HTTP.send(HttpRequest.newBuilder(URI.create(base + p)).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()); }
    static Map<String, Object> obj(HttpResponse<String> r) { return Json.asObject(Json.parse(r.body())); }

    @Test void healthAndCatalogue() throws Exception {
        Map<String, Object> h = obj(get("/api/health"));
        assertEquals("UP", h.get("status"));
        assertEquals(24.0, h.get("algorithms"));
        List<?> algos = (List<?>) obj(get("/api/algorithms")).get("algorithms");
        assertEquals(24, algos.size());
        assertTrue(algos.stream().allMatch(a -> ((Map<?, ?>) a).get("time") != null));
    }

    @Test void datasetProfileAndScan() throws Exception {
        Map<String, Object> p = obj(get("/api/datasets/enterprise-sample/profile"));
        assertEquals(3959.0, p.get("lines"));
        Map<String, Object> scan = obj(get("/api/scan?dataset=enterprise-sample&limit=5"));
        assertEquals(5, ((List<?>) scan.get("detections")).size());
        assertTrue(((Number) scan.get("detectionsTotal")).intValue() > 200);
        assertNotNull(scan.get("pipeline"));
        assertFalse(((List<?>) obj(get("/api/datasets/enterprise-sample/samples")).get("samples")).isEmpty());
    }

    @Test void runWithTraceReturnsRealExecutionEvents() throws Exception {
        HttpResponse<String> r = post("/api/run", "{\"algorithm\":\"kmp\",\"dataset\":\"enterprise-sample\",\"mode\":\"visualize\"}");
        assertEquals(200, r.statusCode());
        Map<String, Object> run = obj(r);
        assertEquals("SUCCESS", run.get("status"));
        List<?> trace = (List<?>) run.get("trace");
        assertTrue(trace.size() > 20);
        long matches = trace.stream().filter(e -> "match".equals(((Map<?, ?>) e).get("t"))).count();
        assertEquals(((Number) run.get("itemsTotal")).longValue(), matches);
        String text = (String) ((Map<?, ?>) run.get("inputPreview")).get("text");
        for (Object e : trace) { Map<?, ?> m = (Map<?, ?>) e; if ("match".equals(m.get("t"))) assertEquals(((String) m.get("pattern")), text.substring(((Number) m.get("pos")).intValue(), ((Number) m.get("pos")).intValue() + ((String) m.get("pattern")).length())); }
    }

    @Test void fullModeRunsOnTheWholeDatasetWithoutTrace() throws Exception {
        Map<String, Object> run = obj(post("/api/run", "{\"algorithm\":\"ahocorasick\",\"dataset\":\"enterprise-sample\",\"mode\":\"full\"}"));
        assertNull(run.get("trace"));
        assertEquals(383.0, ((Map<?, ?>) run.get("summary")).get("matches"));
        assertTrue(((Number) run.get("comparisons")).longValue() > 100_000);
    }

    @Test void customTextCanBeVisualised() throws Exception {
        Map<String, Object> run = obj(post("/api/run", "{\"algorithm\":\"ahocorasick\",\"dataset\":\"enterprise-sample\",\"text\":\"GET /?id=1%20UNION%20SELECT x\",\"patterns\":[\"union select\",\"select x\"]}"));
        assertEquals("SUCCESS", run.get("status"));
        assertEquals(2.0, ((Map<?, ?>) run.get("summary")).get("matches"));
    }

    @Test void compareHistoryAndStatus() throws Exception {
        Map<String, Object> c = obj(post("/api/compare", "{\"algorithms\":[\"edmondskarp\",\"dinic\"],\"dataset\":\"enterprise-sample\"}"));
        assertEquals(true, c.get("consistent"));
        assertFalse(((List<?>) obj(get("/api/history")).get("runs")).isEmpty());
        assertNotNull(obj(get("/api/status")).get("lastRuns"));
    }

    @Test void benchmarkEndpoint() throws Exception {
        Map<String, Object> b = obj(post("/api/benchmark", "{\"group\":\"suffix-structures\",\"sizes\":[1000,3000],\"repeats\":1,\"dataset\":\"enterprise-sample\"}"));
        assertEquals(3, ((Map<?, ?>) b.get("series")).size());
    }

    @Test void uploadThenScan() throws Exception {
        Map<String, Object> up = obj(post("/api/datasets/upload?name=probe", "GET /a 200\nGET /?q=<script>alert(1)</script> 200\n"));
        assertEquals("custom-probe", up.get("id"));
        Map<String, Object> scan = obj(get("/api/scan?dataset=custom-probe"));
        assertEquals(1.0, ((Map<?, ?>) scan.get("totals")).get("flaggedLines"));
    }

    @Test void errorsAreReportedAsJsonWithProperStatusCodes() throws Exception {
        assertEquals(404, get("/api/nothing").statusCode());
        assertEquals(404, post("/api/run", "{\"algorithm\":\"nope\"}").statusCode());
        assertEquals(400, post("/api/run", "{not json").statusCode());
        assertEquals(400, post("/api/compare", "{\"algorithms\":[\"kmp\"]}").statusCode());
        assertNotNull(obj(post("/api/run", "{\"algorithm\":\"nope\"}")).get("error"));
        HttpResponse<String> failed = post("/api/run", "{\"algorithm\":\"mincut\",\"dataset\":\"enterprise-sample\"}");
        assertEquals(200, failed.statusCode());
    }

    @Test void dashboardIsServed() throws Exception {
        HttpResponse<String> index = get("/");
        assertEquals(200, index.statusCode());
        assertTrue(index.body().contains("CyberShield"));
        assertEquals(200, get("/js/app.js").statusCode());
        assertEquals(200, get("/css/style.css").statusCode());
        assertEquals(404, get("/missing.html").statusCode());
        int traversal = get("/..%2f..%2fetc/passwd").statusCode();
        assertTrue(traversal == 400 || traversal == 404, "path traversal must never be served, got " + traversal);
    }
}
