# Architecture

```
dashboard (HTML/CSS/JS) ──HTTP/JSON──▶ api.ApiServer ──▶ service.* ──▶ execution.ExecutionEngine ──▶ algorithm.*
                                                         │                     │
                                                         ▼                     ▼
                                                  dataset.* (files)    metrics.MemoryProbe, Trace, counters
```

| Package | Responsibility |
|---|---|
| `algorithm` | `Algorithm` interface, `AlgorithmInput/Result/Info`; sub-packages hold the 24 implementations. Algorithms are **stateless** and write only to the `ExecutionContext` they are handed. |
| `execution` | `ExecutionEngine` (time, comparisons, operations, thread-allocated bytes, failure isolation, "currently running" tracking), `RunRecord`, `Trace`, `ExecutionContext`. |
| `metrics` | `MemoryProbe` — exact per-thread allocation via `com.sun.management.ThreadMXBean`, heap delta fallback. |
| `dataset` | file access (disk first, classpath fallback), `Preprocessor` (URL-decode + lower-case), `Corpus` (text↔line mapping), parsers (DIMACS, topology, rules…), `DatasetGenerator`. |
| `service` | `DetectionService` (the pipeline), `InputFactory` (real data → algorithm input), `ComparisonService`, `BenchmarkService`, `HistoryService`, `DatasetProfiler`, `AppContext` (manual wiring). |
| `api` | `ApiServer` on the JDK `HttpServer`: JSON endpoints + static dashboard hosting. Errors map to 400/404/500 JSON, never stack traces. |

## Execution trace → animation

1. `POST /api/run` with `mode: visualize` builds a *small real input* (e.g. a 120-char window around the first
   detection in an attack log line) and enables `Trace`.
2. The algorithm calls `ctx.trace.emit(type, explanation, key, value, …)` at each meaningful step, guarded by
   `ctx.tracing()` so non-traced runs pay nothing. Events carry state snapshots (LPS array, Z array, suffix order,
   flow per edge, …).
3. The response contains the events; `dashboard/js/viz.js` rebuilds each frame from events `0..i`
   (so seeking backwards is exact). There is no animation data on the front end except renderers.
4. Traces are capped (default 6,000 events, `traceLimit` up to 20,000) and flagged `traceTruncated`.
   `mode: full` runs the same algorithm on the whole dataset without a trace for honest at-scale metrics.

## Detection pipeline (`DetectionService.scan`)

Input (access.log + auth.log) → `Preprocessor.normalize` per line → Aho-Corasick over the joined corpus with all
signatures → map match offsets to lines (`Corpus.lineOf`, binary search) → KMP cross-check (match totals must be
identical) → evaluation against `labels.csv` → process-name analysis (Damerau-Levenshtein) → traffic analysis
(Parallel Reduce on flows/minute, port-scanner heuristic). Results are cached per dataset.

## Error handling & logging

* Algorithms throw `IllegalArgumentException` for invalid input (e.g. non-3-SAT clause, bad topology); the engine
  converts any `Exception`/`StackOverflowError` into a `FAILED` record with the message — the API stays up.
* Uploads are size-limited; unknown datasets/algorithms → 404; malformed JSON → 400.
* `java.util.logging` with a one-line console format; history I/O failures are logged, never fatal.
