# REST API (JSON)

| Method & path | Purpose |
|---|---|
| `GET /api/health` | status, Java version, uptime, algorithm count |
| `GET /api/status` | running algorithms, last run per algorithm, heap, CPUs |
| `GET /api/algorithms` | catalogue (module, CO, complexity, description, use case) |
| `GET /api/datasets` | available datasets (bundled + uploaded) |
| `GET /api/datasets/{id}/profile` | overview numbers computed from the files |
| `GET /api/datasets/{id}/samples` | real attack lines for the lab |
| `POST /api/datasets/upload?name=x` | body = raw log text; creates dataset `custom-x` |
| `GET /api/scan?dataset=&limit=` | full detection pipeline result (cached) |
| `POST /api/run` | `{algorithm, dataset, mode: "visualize"\|"full", text?, patterns?[], line?, traceLimit?, params?{}}` |
| `POST /api/compare` | `{algorithms:[…], dataset}` → metrics per algorithm + `consistent` |
| `POST /api/benchmark` | `{group:"string-matching"\|"suffix-structures", sizes:[…], repeats, dataset}` |
| `GET /api/history?limit=` / `DELETE /api/history` | execution history |

`/api/run` response (abridged): `status, timeMs, comparisons, operations, memoryBytes, headline, summary{…}, items[…],
trace[{t,msg,…state}], traceTruncated, inputPreview{text,patterns}`. Errors: `{"error": "..."}` with 400/404/500.

Algorithm parameters (`params`): `minLen` (suffix phrases), `maxChars`, `threshold`/`damerau` (edit distance), `k`/`seed`
(reservoir), `seed` (quicksort), `rounds` (Miller-Rabin Monte-Carlo bases), `zThreshold` (parallel reduce).
