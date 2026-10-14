package com.cybershield.algorithm.random;

import com.cybershield.algorithm.*;
import com.cybershield.execution.ExecutionContext;
import java.math.BigInteger;
import java.util.*;

/** Miller-Rabin primality test (Monte Carlo): deterministic for n < 3.3e24 with the first 12 prime bases. */
public final class MillerRabin implements Algorithm {
    public static final AlgorithmInfo INFO = new AlgorithmInfo("millerrabin", "Miller-Rabin primality", "M6 · Randomized & Parallel", "CO6",
            "randomized-parallel", InputKind.NUMBERS, "O(k·log³ n)", "O(log n)",
            "Writes n-1 = 2^s·d and checks each base a: a^d ≡ ±1 or a^(2^r·d) ≡ -1. A failing base is a witness that n is composite. Random bases make it a Monte Carlo algorithm with error ≤ 4^-k.",
            "Large-number security computations: screening RSA/DH key and session-secret candidates.");

    static final int[] BASES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37};

    @Override public AlgorithmInfo info() { return INFO; }

    public static boolean isPrime(BigInteger n, ExecutionContext ctx, Random rnd, int randomRounds) {
        if (n.compareTo(BigInteger.TWO) < 0) return false;
        for (int p : BASES) {
            BigInteger bp = BigInteger.valueOf(p);
            if (n.equals(bp)) return true;
            if (n.mod(bp).signum() == 0) {
                if (ctx.tracing()) ctx.trace.emit("round", "Small prime " + p + " divides n → composite.", "a", p, "result", "divisible");
                return false;
            }
        }
        BigInteger nm1 = n.subtract(BigInteger.ONE), d = nm1;
        int s = 0;
        while (!d.testBit(0)) { d = d.shiftRight(1); s++; }
        if (ctx.tracing()) ctx.trace.emit("decompose", "n - 1 = 2^" + s + " · d with d = " + d + ".", "s", s, "d", d.toString());
        List<BigInteger> bases = new ArrayList<>();
        if (randomRounds > 0) {
            for (int i = 0; i < randomRounds; i++) bases.add(new BigInteger(Math.max(8, n.bitLength() - 1), rnd).mod(n.subtract(BigInteger.valueOf(3))).add(BigInteger.TWO));
        } else for (int b : BASES) bases.add(BigInteger.valueOf(b));
        for (BigInteger a : bases) {
            BigInteger x = a.modPow(d, n);
            ctx.op(s + 1L);
            ctx.cmp();
            boolean pass = x.equals(BigInteger.ONE) || x.equals(nm1);
            for (int r = 1; r < s && !pass; r++) {
                x = x.multiply(x).mod(n);
                ctx.cmp();
                if (x.equals(nm1)) pass = true;
            }
            if (ctx.tracing())
                ctx.trace.emit("round", "Base a = " + a + ": " + (pass ? "passes (n looks prime for this base)." : "FAILS → a is a witness, n is composite."), "a", a.toString(), "result", pass ? "pass" : "witness");
            if (!pass) return false;
        }
        return true;
    }

    @Override
    public AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) {
        AlgorithmResult r = new AlgorithmResult();
        int randomRounds = in.intParam("rounds", 0);
        Random rnd = new Random((long) in.param("seed", 7));
        int primes = 0, composites = 0, labelMatches = 0, labelled = 0;
        for (int i = 0; i < in.tokens.size(); i++) {
            String tok = in.tokens.get(i);
            BigInteger n = new BigInteger(tok);
            if (ctx.tracing()) ctx.trace.emit("number", "Testing n = " + tok, "n", tok);
            boolean p = isPrime(n, ctx, rnd, randomRounds);
            if (p) primes++; else composites++;
            String label = i < in.names.size() ? in.names.get(i) : "";
            if (!label.isEmpty()) { labelled++; if (label.equals(p ? "prime" : "composite")) labelMatches++; }
            r.item("n", tok, "verdict", p ? "probably prime" : "composite", "expected", label);
        }
        r.headline = primes + " prime(s), " + composites + " composite(s)" + (labelled > 0 ? "; " + labelMatches + "/" + labelled + " agree with the reference labels" : "");
        r.put("tested", in.tokens.size()).put("primes", primes).put("composites", composites).put("labelled", labelled).put("labelAgreement", labelMatches)
                .put("mode", randomRounds > 0 ? "Monte Carlo (" + randomRounds + " random bases)" : "deterministic bases (first 12 primes)").check(primes);
        return r;
    }
}
