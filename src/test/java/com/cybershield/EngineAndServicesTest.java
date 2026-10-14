package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.algorithm.*;
import com.cybershield.dataset.Dataset;
import com.cybershield.execution.*;
import com.cybershield.service.*;
import com.cybershield.util.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EngineAndServicesTest {
    static final AlgorithmInfo BOOM = new AlgorithmInfo("boom", "Boom", "M0", "CO0", "test", InputKind.TEXT, "-", "-", "", "");

    @Test void engineMeasuresTimeComparisonsOperationsAndMemory() {
        AppContext app = TestSupport.app();
        Algorithm a = app.registry.get("kmp");
        AlgorithmInput in = AlgorithmInput.ofText("abcabcabc".repeat(2000), "cab", "zzz");
        RunRecord r = app.engine.measure(a, in, "test", false, 0);
        assertTrue(r.ok());
        assertTrue(r.nanos > 0 && r.comparisons >= 18000 && r.operations > 0);
        assertTrue(r.memoryBytes >= 0);
        assertEquals(0, app.engine.activeRuns());
    }

    @Test void engineTurnsAlgorithmFailuresIntoFailedRecords() {
        Algorithm bad = new Algorithm() {
            public AlgorithmInfo info() { return BOOM; }
            public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) { throw new IllegalStateException("kaboom"); }
        };
        RunRecord r = TestSupport.app().engine.measure(bad, new AlgorithmInput(), "test", true, 100);
        assertEquals("FAILED", r.status);
        assertTrue(r.error.contains("kaboom"));
        assertEquals(0, TestSupport.app().engine.activeRuns());
        assertNull(r.result);
        assertEquals("FAILED", r.toSummaryMap().get("status"));
    }

    @Test void traceStopsAtItsLimitAndReportsTruncation() {
        Trace t = Trace.on(5);
        for (int i = 0; i < 20; i++) t.emit("e", "m" + i, "i", i);
        assertEquals(5, t.events().size());
        assertTrue(t.truncated());
        Trace off = Trace.off();
        off.emit("e", "m");
        assertTrue(off.events().isEmpty() && !off.enabled());
    }

    @Test void historyIsPersistedAndReloaded() throws Exception {
        Path f = Files.createTempFile("cs-history", ".jsonl");
        Files.delete(f);
        AppContext app = new AppContext(f);
        Algorithm a = app.registry.get("z");
        app.engine.execute(a, AlgorithmInput.ofText("aaaa", "aa"), "test", false, 0);
        app.engine.execute(app.registry.get("kmp"), AlgorithmInput.ofText("aaaa", "aa"), "test", false, 0);
        assertEquals(2, app.history.list(10).size());
        HistoryService reloaded = new HistoryService(f);
        List<Map<String, Object>> l = reloaded.list(10);
        assertEquals(2, l.size());
        assertEquals("kmp", l.get(0).get("algorithm"));
        reloaded.clear();
        assertEquals(0, reloaded.list(10).size());
        assertFalse(Files.exists(f));
    }

    @Test void comparisonServiceChecksThatAlgorithmsAgree() {
        AppContext app = TestSupport.app();
        Dataset d = app.datasets.get(null);
        for (List<String> ids : List.of(List.of("kmp", "z", "rabinkarp", "ahocorasick"), List.of("suffixarray", "sais", "kasai", "suffixautomaton"), List.of("fordfulkerson", "edmondskarp", "dinic", "mincut"))) {
            Map<String, Object> r = app.comparison.compare(ids, d, Map.of());
            assertEquals(true, r.get("consistent"), ids + " -> " + r.get("checkValues"));
            assertEquals(true, r.get("sameFamily"));
            assertEquals(ids.size(), ((List<?>) r.get("runs")).size());
        }
        assertThrows(IllegalArgumentException.class, () -> app.comparison.compare(List.of("kmp"), d, Map.of()));
        assertThrows(NoSuchElementException.class, () -> app.comparison.compare(List.of("kmp", "nope"), d, Map.of()));
    }

    @Test void benchmarkProducesOnePointPerAlgorithmAndSize() {
        AppContext app = new AppContext(null);
        Map<String, Object> r = app.benchmark.run("string-matching", List.of(2000, 8000), 2, app.datasets.get(null));
        @SuppressWarnings("unchecked") Map<String, List<Map<String, Object>>> series = (Map<String, List<Map<String, Object>>>) r.get("series");
        assertEquals(Set.of("kmp", "z", "rabinkarp", "ahocorasick"), series.keySet());
        for (List<Map<String, Object>> pts : series.values()) {
            assertEquals(2, pts.size());
            assertTrue((Long) pts.get(1).get("comparisons") > (Long) pts.get(0).get("comparisons"), "work must grow with the input");
        }
        assertEquals(8, app.history.list(100).size(), "one history row per (algorithm,size)");
        assertThrows(IllegalArgumentException.class, () -> app.benchmark.run("bogus", List.of(1000), 1, app.datasets.get(null)));
    }

    @Test void everyAlgorithmRunsOnRealDataInBothModesAndProducesAValidTrace() {
        AppContext app = TestSupport.app();
        Dataset d = app.datasets.get(null);
        assertEquals(24, app.registry.all().size());
        for (Algorithm a : app.registry.all()) {
            AlgorithmInput full = app.inputs.build(a.info(), d, new HashMap<>(), false);
            RunRecord rf = app.engine.measure(a, full, d.id(), false, 0);
            assertTrue(rf.ok(), a.info().id() + " full run failed: " + rf.error);
            assertFalse(rf.result.headline.isBlank());
            AlgorithmInput small = app.inputs.build(a.info(), d, new HashMap<>(), true);
            RunRecord rt = app.engine.measure(a, small, d.id(), true, 6000);
            assertTrue(rt.ok(), a.info().id() + " trace run failed: " + rt.error);
            assertFalse(rt.trace.events().isEmpty(), a.info().id() + " produced no trace events");
            for (Map<String, Object> e : rt.trace.events()) {
                assertNotNull(e.get("t")); assertNotNull(e.get("msg"));
            }
            String json = Json.write(rt.toFullMap(100));
            assertEquals(rt.trace.events().size(), ((List<?>) Json.asObject(Json.parse(json)).get("trace")).size(), a.info().id() + " trace must survive JSON");
        }
    }
}
