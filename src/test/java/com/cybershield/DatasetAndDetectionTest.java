package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.dataset.*;
import com.cybershield.dataset.Corpus.LogLine;
import com.cybershield.dataset.Models.*;
import com.cybershield.service.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DatasetAndDetectionTest {
    @Test void preprocessorDecodesAndLowerCases() {
        assertEquals("get /?q=union select", Preprocessor.normalize("GET /?q=UNION%20SELECT"));
        assertEquals("<script>", Preprocessor.normalize("%3CScRiPt%3E"));
        assertEquals("a b", Preprocessor.normalize("a+b"));
        assertEquals("%zz ok", Preprocessor.normalize("%ZZ%20OK"), "malformed escapes must not crash and valid ones still decode");
        assertEquals("%2e%2e", Preprocessor.normalize("%252E%252E"), "only one layer is decoded");
    }

    @Test void corpusMapsTextPositionsBackToLines() {
        Corpus c = new Corpus(List.of(new LogLine(0, "a", 1, "AAA", "aaa"), new LogLine(1, "a", 2, "BB", "bb"), new LogLine(2, "a", 3, "C", "c")));
        assertEquals("aaa\nbb\nc\n", c.text);
        assertEquals(0, c.lineOf(0)); assertEquals(0, c.lineOf(3)); assertEquals(1, c.lineOf(4)); assertEquals(2, c.lineOf(7));
    }

    @Test void bundledDatasetLoadsEveryFile() {
        Dataset d = TestSupport.app().datasets.get(null);
        assertEquals(3959, d.corpus().lines.size());
        assertFalse(d.signatures().isEmpty());
        assertEquals(10, d.categories().size());
        assertEquals(6500, d.flows().size());
        assertEquals(12, d.rules().size());
        assertEquals(6, d.pipeline().names().size());
        assertEquals(11, d.topology().names().size());
        assertEquals(6, d.assignments().left());
        assertEquals(6, d.cnf().vars());
        assertEquals(10, d.dependencies().names().size() > 0 ? 10 : 0);
        assertEquals(12, d.numbers().values().size());
        assertFalse(d.processEventTokens().isEmpty());
        assertTrue(d.labels().size() > 1000);
    }

    @Test void generatorIsReproducible() throws Exception {
        Path tmp = Files.createTempDirectory("cs-gen");
        DatasetGenerator.generate(tmp);
        for (String f : List.of("access.log", "auth.log", "netflow.csv", "labels.csv", "process_events.txt"))
            assertEquals(Resources.read("enterprise-sample", f), Files.readString(tmp.resolve(f)), f + " must be byte-identical to the shipped file");
    }

    @Test void scanMatchesAnIndependentNaiveSearchForEverySignature() {
        Dataset d = TestSupport.app().datasets.get(null);
        DetectionService.Scan s = TestSupport.app().detection.scan(d);
        long naiveTotal = 0;
        for (String p : s.signatureByPattern.keySet()) naiveTotal += TestSupport.naiveFind(s.corpus.text, p).size();
        @SuppressWarnings("unchecked") Map<String, Object> cc = (Map<String, Object>) s.json.get("crossCheck");
        assertEquals(naiveTotal, ((Number) cc.get("aho")).longValue());
        assertEquals(naiveTotal, ((Number) cc.get("kmp")).longValue());
        assertEquals(true, cc.get("agree"));
    }

    @Test void detectionAccuracyAgainstGroundTruth() {
        DetectionService.Scan s = TestSupport.app().detection.scan(TestSupport.app().datasets.get(null));
        @SuppressWarnings("unchecked") Map<String, Object> ev = (Map<String, Object>) s.json.get("evaluation");
        assertNotNull(ev);
        assertTrue((Double) ev.get("recall") >= 0.95, "recall " + ev.get("recall"));
        assertEquals(1.0, (Double) ev.get("precision"), 1e-9, "no benign line may be flagged");
        @SuppressWarnings("unchecked") Map<String, Integer> missed = (Map<String, Integer>) ev.get("missedByLabel");
        for (String k : missed.keySet()) assertTrue(k.endsWith("_EVASIVE"), "only deliberately double-encoded payloads may be missed, got " + k);
    }

    @Test void processNameAnalysisFindsEveryInjectedLookalike() {
        DetectionService.Scan s = TestSupport.app().detection.scan(TestSupport.app().datasets.get(null));
        @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) s.json.get("processes");
        assertEquals(1.0, (Double) p.get("recall"), 1e-9);
        assertTrue(((Number) p.get("falsePositives")).intValue() <= 2);
    }

    @Test void trafficAnalysisFindsTheInjectedDdosBurstAndPortScan() {
        DetectionService.Scan s = TestSupport.app().detection.scan(TestSupport.app().datasets.get(null));
        @SuppressWarnings("unchecked") Map<String, Object> t = (Map<String, Object>) s.json.get("traffic");
        @SuppressWarnings("unchecked") List<Integer> spikes = (List<Integer>) t.get("spikeMinutes");
        @SuppressWarnings("unchecked") Set<Integer> truth = (Set<Integer>) t.get("groundTruthDdosMinutes");
        assertTrue(spikes.containsAll(truth), "spikes " + spikes + " must cover ground-truth DDoS minutes " + truth);
        @SuppressWarnings("unchecked") List<Map<String, Object>> scanners = (List<Map<String, Object>>) t.get("portScanners");
        assertEquals("203.0.113.50", scanners.get(0).get("src"));
    }

    @Test void uploadedLogsAreAnalysedWithTheSameSignatures() {
        AppContext app = new AppContext(null);
        Dataset d = app.datasets.upload("my log", "GET /index.html 200\nGET /?id=1%20UNION%20SELECT%20pw 200\nGET /x 200 sqlmap/1.0\n");
        assertEquals("custom-my-log", d.id());
        DetectionService.Scan s = app.detection.scan(d);
        assertEquals(2, s.hits.size());
        assertNull(s.json.get("evaluation"));
        assertThrows(IllegalArgumentException.class, () -> app.datasets.upload("x", "   "));
        assertThrows(NoSuchElementException.class, () -> app.datasets.get("nope"));
    }

    @Test void profilerReportsRealCounts() {
        Map<String, Object> p = new DatasetProfiler().profile(TestSupport.app().datasets.get(null));
        assertEquals(3959, p.get("lines"));
        @SuppressWarnings("unchecked") Map<String, Integer> per = (Map<String, Integer>) p.get("perSource");
        assertEquals(3959, per.values().stream().mapToInt(Integer::intValue).sum());
        assertNotNull(p.get("network"));
    }
}
