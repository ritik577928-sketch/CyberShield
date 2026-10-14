# Algorithm catalogue

| Module | Algorithm | Time | Space | CyberShield use | Input |
|---|---|---|---|---|---|
| M1 / CO1 | KMP | O(n+m) | O(m) | malware signature search | normalised log text + signatures |
| M1 | Z-function | O(n+m) | O(n+m) | repeated attack patterns | same |
| M1 | Rabin-Karp | O(n+m) avg | O(1) | hash-based signature search (collisions counted) | same |
| M1 | Aho-Corasick | O(n+m+z) | O(mσ) | all signatures in one pass | same |
| M2 / CO2 | Suffix array (prefix doubling) | O(n log n) | O(n) | index logs, repeated phrases | log text |
| M2 | SA-IS | O(n) | O(n) | linear-time indexing | log text |
| M2 | LCP (Kasai) | O(n) | O(n) | repeated attack phrases | log text |
| M2 | Suffix automaton | O(n) | O(nσ) | occurrence counts, distinct substrings | log text |
| M3 / CO3 | Weighted Damerau-Levenshtein | O(nm)/pair | O(nm) | obfuscated / look-alike process names (look-alike substitution 0.3, swap 0.7, others 1.0) | process events + known names |
| M3 | Bitmask DP | O(2^k·r) | O(2^k) | cheapest set of rules covering all threat categories | rules.csv |
| M3 | Matrix-chain | O(n³) | O(n²) | order of processing stages | pipeline_dims.txt |
| M4 / CO4 | Ford-Fulkerson (DFS) | O(E·f) | O(V+E) | max network flow | topology.csv |
| M4 | Edmonds-Karp | O(VE²) | O(V+E) | capacity analysis | topology.csv |
| M4 | Dinic | O(V²E) | O(V+E) | larger networks | topology.csv |
| M4 | Min-cut | O(VE²) | O(V+E) | critical links | topology.csv |
| M4 | Bipartite matching (Kuhn) | O(VE) | O(V+E) | analysts ↔ alerts (cross-checked against max-flow) | assignments.csv |
| M5 / CO5 | SAT (DPLL) | O(2ⁿ) | O(n+m) | policy constraints (3-SAT) | policies.cnf |
| M5 | 3-SAT→CLIQUE→IS→VC | poly reductions, O(3^m) search | O(m²) | threat-conflict analysis (checked against DPLL) | policies.cnf |
| M5 | Vertex-cover 2-approximation | O(V+E) | O(V) | which services to monitor (compared with exact optimum) | dependencies.csv |
| M6 / CO6 | Miller-Rabin | O(k log³n) | O(log n) | big-number screening; deterministic bases or Monte-Carlo random bases | numbers.csv |
| M6 | Randomized QuickSort (Las Vegas) | O(n log n) exp. | O(log n) | rank events by severity | detections |
| M6 | Reservoir sampling | O(n) | O(k) | sample the event stream, estimate threat rate | log stream |
| M6 | Blelloch scan | work O(n), depth O(log n) | O(n) | cumulative flows, busiest window | flows per minute |
| M6 | Parallel reduce + Brent's theorem | work n−1, depth ⌈log₂n⌉ | O(n) | traffic mean/σ/peak, anomalous minutes; reports T_p ≤ W/p + D | flows per minute |

`checkValue` in each result lets the Compare page prove that algorithms solving the same problem agree.
