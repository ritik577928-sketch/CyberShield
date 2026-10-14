package com.cybershield.algorithm.flow;

import java.util.*;

/** Residual flow network using linked adjacency arrays. Forward edge i is stored at index 2i, its reverse at 2i+1. */
public final class FlowNetwork {
    public final int n;
    private final int[] head;
    private int[] nxt, to, cap, orig;
    private int m;

    public FlowNetwork(int n, int expectedEdges) {
        this.n = n;
        head = new int[n];
        Arrays.fill(head, -1);
        int c = Math.max(4, expectedEdges * 2);
        nxt = new int[c]; to = new int[c]; cap = new int[c]; orig = new int[c];
    }

    public int add(int u, int v, int c) {
        if (u < 0 || v < 0 || u >= n || v >= n) throw new IllegalArgumentException("Edge endpoint out of range: " + u + "->" + v);
        if (c < 0) throw new IllegalArgumentException("Negative capacity on edge " + u + "->" + v);
        if (m + 2 > to.length) {
            int nc = to.length * 2;
            nxt = Arrays.copyOf(nxt, nc); to = Arrays.copyOf(to, nc); cap = Arrays.copyOf(cap, nc); orig = Arrays.copyOf(orig, nc);
        }
        int e = m;
        to[m] = v; cap[m] = c; orig[m] = c; nxt[m] = head[u]; head[u] = m++;
        to[m] = u; cap[m] = 0; orig[m] = 0; nxt[m] = head[v]; head[v] = m++;
        return e;
    }

    public int head(int u) { return head[u]; }
    public int nxt(int e) { return nxt[e]; }
    public int to(int e) { return to[e]; }
    public int cap(int e) { return cap[e]; }
    public void push(int e, int d) { cap[e] -= d; cap[e ^ 1] += d; }
    public int edgeCount() { return m / 2; }
    public int from(int forwardIdx) { return to[2 * forwardIdx + 1]; }
    public int target(int forwardIdx) { return to[2 * forwardIdx]; }
    public int capacity(int forwardIdx) { return orig[2 * forwardIdx]; }

    /** Current flow on every forward edge. */
    public int[] flows() {
        int[] f = new int[m / 2];
        for (int i = 0; i < f.length; i++) f[i] = orig[2 * i] - cap[2 * i];
        return f;
    }

    /** Nodes reachable from s in the residual graph (the source side of the minimum cut). */
    public boolean[] reachable(int s) {
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        seen[s] = true;
        q.add(s);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int e = head[u]; e != -1; e = nxt[e]) if (!seen[to[e]] && cap[e] > 0) { seen[to[e]] = true; q.add(to[e]); }
        }
        return seen;
    }

    /** Forward-edge indices crossing the minimum cut (call after a maximum flow has been computed). */
    public List<Integer> cutEdges(int s) {
        boolean[] side = reachable(s);
        List<Integer> cut = new ArrayList<>();
        for (int i = 0; i < m / 2; i++) if (side[from(i)] && !side[target(i)] && orig[2 * i] > 0) cut.add(i);
        return cut;
    }
}
