package com.cybershield;

import static org.junit.jupiter.api.Assertions.*;

import com.cybershield.algorithm.*;
import com.cybershield.algorithm.random.*;
import com.cybershield.dataset.Models.Numbers;
import com.cybershield.execution.*;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;

class RandomizedParallelTest {
    @Test void millerRabinMatchesBigIntegerForSmallNumbers() {
        for (int n = 0; n < 5000; n++)
            assertEquals(BigInteger.valueOf(n).isProbablePrime(40), MillerRabin.isPrime(BigInteger.valueOf(n), TestSupport.ctx(), new Random(1), 0), "n=" + n);
    }

    @Test void millerRabinRejectsCarmichaelAndStrongPseudoprimes() {
        for (String s : new String[]{"561", "41041", "825265", "3215031751", "3825123056546413051", "9223372036854775807"})
            assertFalse(MillerRabin.isPrime(new BigInteger(s), TestSupport.ctx(), new Random(1), 0), s);
        for (String s : new String[]{"2305843009213693951", "1000000007", "18446744073709551557"})
            assertTrue(MillerRabin.isPrime(new BigInteger(s), TestSupport.ctx(), new Random(1), 0), s);
    }

    @Test void millerRabinAgreesOnRandom64BitNumbers() {
        Random rnd = new Random(31);
        for (int i = 0; i < 300; i++) {
            BigInteger n = new BigInteger(62, rnd).setBit(0);
            assertEquals(n.isProbablePrime(60), MillerRabin.isPrime(n, TestSupport.ctx(), rnd, 0), n.toString());
        }
    }

    @Test void monteCarloModeUsesRandomBasesAndStillRecognisesPrimes() throws Exception {
        AlgorithmInput in = new AlgorithmInput();
        in.tokens = List.of("1000000007", "1000000008");
        in.params.put("rounds", 20.0);
        AlgorithmResult r = TestSupport.run(new MillerRabin(), in);
        assertEquals(1, r.summary.get("primes"));
        assertTrue(String.valueOf(r.summary.get("mode")).contains("Monte Carlo"));
    }

    @Test void bundledNumbersAgreeWithTheirMathematicalLabels() throws Exception {
        Numbers n = TestSupport.app().datasets.get(null).numbers();
        AlgorithmInput in = new AlgorithmInput();
        in.tokens = n.values(); in.names = n.labels();
        AlgorithmResult r = TestSupport.run(new MillerRabin(), in);
        assertEquals(n.values().size(), r.summary.get("labelAgreement"));
    }

    @Test void randomizedQuickSortSortsDescendingAndHandlesDuplicates() throws Exception {
        Random rnd = new Random(32);
        for (int round = 0; round < 200; round++) {
            AlgorithmInput in = new AlgorithmInput();
            in.ints = rnd.ints(rnd.nextInt(80), 0, round % 2 == 0 ? 5 : 1000).toArray();
            in.params.put("seed", (double) round);
            AlgorithmResult r = TestSupport.run(new RandomizedQuickSort(), in);
            assertEquals(true, r.summary.get("sortedCorrectly"));
        }
    }

    @Test void quickSortComparisonsStayNearNLogN() throws Exception {
        AlgorithmInput in = new AlgorithmInput();
        in.ints = IntStream.range(0, 4000).toArray(); // already sorted: worst case for a naive first-element pivot
        ExecutionContext ctx = TestSupport.ctx();
        new RandomizedQuickSort().run(in, ctx);
        assertTrue(ctx.comparisons() < 4000 * 12 * 2L, "comparisons " + ctx.comparisons());
    }

    @Test void reservoirSamplingIsUniform() throws Exception {
        int n = 20, k = 5, trials = 20000;
        int[] count = new int[n];
        AlgorithmInput in = new AlgorithmInput();
        for (int i = 0; i < n; i++) in.lines.add("L" + i);
        in.params.put("k", (double) k);
        for (int t = 0; t < trials; t++) {
            in.params.put("seed", (double) t);
            AlgorithmResult r = new ReservoirSampling().run(in, TestSupport.ctx());
            assertEquals(k, r.items.size());
            Set<Object> distinct = new HashSet<>();
            for (Map<String, Object> it : r.items) { count[(Integer) it.get("index")]++; distinct.add(it.get("index")); }
            assertEquals(k, distinct.size());
        }
        double expect = trials * (double) k / n, sigma = Math.sqrt(trials * 0.25 * 0.75);
        for (int c : count) assertTrue(Math.abs(c - expect) < 5 * sigma, "count " + c + " expected " + expect);
    }

    @Test void blellochScanEqualsSequentialPrefixSums() throws Exception {
        Random rnd = new Random(33);
        for (int round = 0; round < 100; round++) {
            AlgorithmInput in = new AlgorithmInput();
            in.ints = rnd.ints(1 + rnd.nextInt(70), 0, 500).toArray();
            AlgorithmResult r = TestSupport.run(new BlellochScan(), in);
            assertEquals(true, r.summary.get("matchesSequential"));
            long[] prefix = (long[]) r.summary.get("prefix");
            long run = 0;
            for (int i = 0; i < in.ints.length; i++) { assertEquals(run, prefix[i]); run += in.ints[i]; }
            assertEquals(run, prefix[in.ints.length]);
        }
    }

    @Test void blellochTraceShowsUpSweepThenDownSweep() throws Exception {
        AlgorithmInput in = new AlgorithmInput();
        in.ints = new int[]{3, 1, 7, 0, 4, 1, 6, 3};
        ExecutionContext ctx = TestSupport.traced();
        new BlellochScan().run(in, ctx);
        List<String> types = ctx.trace.events().stream().map(e -> (String) e.get("t")).toList();
        assertEquals(List.of("start", "up", "up", "up", "root", "down", "down", "down"), types);
        int[] last = (int[]) ctx.trace.events().get(7).get("arr");
        assertArrayEquals(new int[]{0, 3, 4, 11, 11, 15, 16, 22}, last);
    }

    @Test void parallelReduceMatchesStreamsAndBrentAccounting() throws Exception {
        Random rnd = new Random(34);
        for (int round = 0; round < 50; round++) {
            AlgorithmInput in = new AlgorithmInput();
            in.ints = rnd.ints(1 + rnd.nextInt(300), 0, 1000).toArray();
            AlgorithmResult r = TestSupport.run(new ParallelReduce(), in);
            assertEquals((long) IntStream.of(in.ints).asLongStream().sum(), r.summary.get("sum"));
            assertEquals((long) IntStream.of(in.ints).max().getAsInt(), r.summary.get("max"));
            assertEquals(true, r.summary.get("workIsNMinus1"));
            int depth = (Integer) r.summary.get("depth");
            assertEquals(in.ints.length <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(in.ints.length - 1), depth);
            assertTrue((Double) r.summary.get("brentBound") >= depth);
        }
    }

    @Test void parallelReduceFlagsAnObviousSpike() throws Exception {
        AlgorithmInput in = new AlgorithmInput();
        in.ints = new int[60];
        Arrays.fill(in.ints, 10);
        in.ints[42] = 900;
        AlgorithmResult r = TestSupport.run(new ParallelReduce(), in);
        assertEquals(List.of(42), r.summary.get("spikeMinutes"));
        assertThrows(IllegalArgumentException.class, () -> new ParallelReduce().run(new AlgorithmInput(), TestSupport.ctx()));
    }
}
