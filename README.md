# CyberShield — Enterprise Security Analytics & Live Algorithm Lab (Java 21)

CyberShield turns a DSA syllabus (strings, suffix structures, dynamic programming, network flow, NP-completeness,
randomized/parallel algorithms) into a working security-analytics platform. Every algorithm is plain Java, runs on
real log/network data, records an **execution trace while it runs**, and the dashboard **replays that trace as an
animation** — nothing on screen is scripted.

```
Network/Data Input → Preprocessing → Threat Detection → Java Algorithms → Execution Trace
      → Live Visualization → Threat/Detection Results → Analytics & Comparison
```

## Quick start

Requirements: **JDK 21+** (Maven optional).

```bash
./start.sh            # Linux / macOS   (uses Maven if installed, otherwise plain javac)
start.bat             # Windows
./start.sh 9090       # custom port
```

then open <http://localhost:8080>.

With Maven: `mvn test` runs the 86 JUnit 5 tests, `mvn package` builds `target/cybershield.jar`
(run with `java -jar target/cybershield.jar`). The application has **no runtime dependencies** (JDK `HttpServer`,
own JSON); JUnit is test-scope only.

## What you get

| Dashboard page | What it shows |
|---|---|
| **Overview** | live pipeline strip, dataset stats, threats by category/severity, top attacker IPs, traffic timeline with detected DDoS minutes, detection accuracy vs ground truth, suspicious process names |
| **Live Algorithm Lab** | pick any of the 24 algorithms and a real log line → animated playback (play/pause/step/seek/speed) with explanation text, event log, time / comparisons / operations / memory / complexity |
| **Detections** | every flagged log line with matched patterns, category, severity; ▶ replays the algorithms on that exact line |
| **Compare** | run several algorithms on the same input; charts for time, comparisons, operations, memory; consistency check that all answers agree |
| **Benchmarks** | median-of-N timings on growing slices of the real corpus (string matching, suffix structures) |
| **History** | every run, persisted to `data/history.jsonl` |
| **Algorithms** | catalogue with syllabus module (M1–M6 / CO1–CO6), complexity, cybersecurity use, last run |

Dark/light theme, responsive layout, **Upload log** button to analyse your own log file with the same signatures.

### What the animations show (all generated from the Java trace)

* **KMP** – LPS table construction, pattern sliding, matched/mismatched characters, shifts, matches.
* **Aho-Corasick** – trie growing node by node, failure links appearing, text pointer, goto/fallback moves, outputs.
* **Suffix Array** – rows re-sorting round by round (prefix doubling), equivalence classes, uniqueness.
* **Z-function, Rabin-Karp** – Z-box reuse; rolling hash windows, hash collisions.
* **Edit distance** – DP matrix filling cell by cell with the chosen operation, trace-back path.
* **Max-flow (Edmonds-Karp, Dinic, Ford-Fulkerson, min-cut)** – augmenting paths, flows/capacities, levels, minimum cut.
* **Vertex cover**, plus a generic array/table/state renderer for SA-IS, quicksort partitions, Blelloch up/down sweeps, reservoir, SAT, reductions, Miller-Rabin, etc.

## Project layout

You asked for `src / algorithms / datasets / tests / dashboard / docs`. Maven fixes the source layout, so the mapping is:

| Requested | Location |
|---|---|
| `src/` | `src/main/java/com/cybershield/` (`api`, `service`, `execution`, `metrics`, `dataset`, `util`) |
| `algorithms/` | `src/main/java/com/cybershield/algorithm/{string,suffix,dp,flow,np,random}` |
| `datasets/` | `datasets/enterprise-sample/` (packaged onto the classpath by the pom) |
| `tests/` | `src/test/java/com/cybershield/` |
| `dashboard/` | `dashboard/` (`index.html`, `css/`, `js/`; packaged onto the classpath) |
| `docs/` | `docs/` |

Layers: **algorithm** (pure, stateless, traced) → **execution** (`ExecutionEngine`: timing, counters, memory, failure
isolation) → **dataset** (parsing, preprocessing, ground truth) → **service** (detection pipeline, input factory,
comparison, benchmark, history) → **api** (REST + static hosting) → **dashboard**. See `docs/ARCHITECTURE.md`.

## Datasets — what is real and what is generated

No public capture can be bundled offline, so `datasets/enterprise-sample/` is produced by `DatasetGenerator`
(seeded, byte-for-byte reproducible — a test enforces this). It uses **real formats** (Apache combined access log,
OpenSSH auth log, NetFlow-style CSV, DIMACS CNF) and **publicly documented attack payloads** (UNION SELECT,
`../../etc/passwd`, `${jndi:`, Shellshock, sqlmap/Nikto user agents, mimikatz, EICAR test string, SSH brute force…).
Attacker addresses use reserved documentation ranges (RFC 5737/2544). Every injected record is listed in
`labels.csv`, so accuracy is **measured**, not asserted. All numbers shown in the dashboard are computed by the Java
algorithms from these files at run time. To analyse genuine data, use **Upload log** (or replace the files in
`datasets/enterprise-sample/`, which take precedence over the packaged copy).

Measured on the bundled data (see `DatasetAndDetectionTest`): 3,959 log lines → 254 flagged lines, 383 signature
matches, **precision 100 %, recall 98.1 %**; the 5 misses are deliberately double-URL-encoded payloads (the
preprocessor decodes one layer). Aho-Corasick and KMP agree with an independent naive search on every signature.

## REST API

`GET /api/health|status|algorithms|datasets|scan|history`, `GET /api/datasets/{id}/profile|samples`,
`POST /api/run|compare|benchmark`, `POST /api/datasets/upload?name=…`, `DELETE /api/history` — see `docs/API.md`.

## Verification status (please read)

* **Compiled and executed:** all 24 algorithms ran on the real dataset in both full and traced mode; all 86 tests pass.
* **Test execution caveat:** the build sandbox was offline with a JRE only (no Maven/JUnit download). The tests are
  standard JUnit 5 (`pom.xml` pulls `junit-jupiter 5.10.2`), but I executed them with a small stand-in runner that
  implements the handful of JUnit API calls used. Please run `mvn test` once on your machine to confirm with the real JUnit.
* **Server verified:** started via `start.sh` (javac path) and exercised over HTTP; the Maven path of the scripts was not run here.
* **Dashboard:** JS syntax-checked, API contract tested, and **2,161 real trace events from all 24 algorithms were replayed
  through the renderers with a fake DOM without a single error**. It was *not* opened in a real browser here, so visual
  polish/layout is untested — report anything that looks off.
* **Not implemented:** an Ukkonen suffix *tree* (suffix array + LCP + suffix automaton answer the same substring queries).

## License / usage

Educational project. Attack strings are the standard public test patterns used by WAF/IDS rule sets; nothing here
performs an attack.
