# Testing & verification

86 JUnit 5 tests in `src/test/java/com/cybershield/`. Run `mvn test`.

| Suite | What it proves |
|---|---|
| `StringAlgorithmsTest` | KMP/Z/Rabin-Karp/Aho-Corasick equal naive search on 300+ random cases; LPS/Z textbook values; KMP pointer never moves backwards; **trace match events == reported matches** |
| `SuffixAlgorithmsTest` | doubling & SA-IS equal naive sorting on 400 random strings (incl. empty/single/unary); Kasai = naive LCP; suffix automaton substring/occurrence counts = brute force; trace ends in the published SA |
| `DynamicProgrammingTest` | known distances, look-alike and swap costs, metric properties on random strings; bitmask DP = brute force over all 2¹² rule subsets; matrix-chain CLRS = 15125 |
| `FlowAlgorithmsTest` | CLRS network = 23 for all four flow algorithms; 60 random networks: all agree **and equal brute-force min-cut**; Kuhn matching = brute force & max-flow |
| `NpAlgorithmsTest` | DPLL = brute-force SAT on 300 random instances; reduction chain agrees with DPLL (both sat/unsat), VC valid and ≤ 2·OPT; DIMACS validation; engine turns bad input into FAILED |
| `RandomizedParallelTest` | Miller-Rabin = `BigInteger.isProbablePrime` for 0..4999 and 300 random 62-bit numbers, rejects Carmichael/strong pseudoprimes; quicksort; reservoir uniformity (20,000 trials); Blelloch = sequential prefix sums; Brent accounting |
| `DatasetAndDetectionTest` | preprocessing, corpus mapping, generator reproducibility, **detection vs naive search per signature**, precision/recall vs ground truth, DDoS minutes found, upload |
| `EngineAndServicesTest` | metrics, failure isolation, trace limit, history persistence, comparison consistency, benchmark growth, **all 24 algorithms in full + traced mode with JSON-safe traces** |
| `ApiServerTest` | every endpoint over real HTTP, trace events align with the input text, error codes, static hosting, path traversal blocked |

Not covered by automated tests: pixel-level rendering in a browser (see README "Verification status").
