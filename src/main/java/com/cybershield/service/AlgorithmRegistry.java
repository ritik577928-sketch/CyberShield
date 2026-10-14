package com.cybershield.service;

import com.cybershield.algorithm.Algorithm;
import com.cybershield.algorithm.dp.*;
import com.cybershield.algorithm.flow.*;
import com.cybershield.algorithm.np.*;
import com.cybershield.algorithm.random.*;
import com.cybershield.algorithm.string.*;
import com.cybershield.algorithm.suffix.*;
import java.util.*;

/** Single place that lists every algorithm of the platform, grouped by syllabus module. */
public final class AlgorithmRegistry {
    private final Map<String, Algorithm> byId = new LinkedHashMap<>();

    public AlgorithmRegistry() {
        for (Algorithm a : new Algorithm[]{
                new KmpSearch(), new ZFunctionSearch(), new RabinKarpSearch(), new AhoCorasickSearch(),
                new SuffixArrayDoubling(), new SaIs(), new KasaiLcp(), new SuffixAutomaton(),
                new EditDistance(), new BitmaskRuleCover(), new MatrixChain(),
                new EdmondsKarp(), new Dinic(), new FordFulkerson(), new BipartiteMatching(), new MinCut(),
                new DpllSat(), new ReductionChain(), new VertexCover2Approx(),
                new MillerRabin(), new RandomizedQuickSort(), new ReservoirSampling(), new BlellochScan(), new ParallelReduce()})
            byId.put(a.info().id(), a);
    }

    public Algorithm get(String id) {
        Algorithm a = byId.get(id);
        if (a == null) throw new NoSuchElementException("Unknown algorithm: " + id);
        return a;
    }

    public Collection<Algorithm> all() { return byId.values(); }
    public List<String> ids() { return new ArrayList<>(byId.keySet()); }
    public List<Algorithm> group(String group) {
        List<Algorithm> l = new ArrayList<>();
        for (Algorithm a : byId.values()) if (a.info().group().equals(group)) l.add(a);
        return l;
    }
}
