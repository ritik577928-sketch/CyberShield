package com.cybershield.dataset;

import java.util.*;

/** All log lines of a dataset, their normalised text joined with '\n', and a position → line lookup. */
public final class Corpus {
    public record LogLine(int index, String source, int number, String raw, String norm) {}

    public final List<LogLine> lines;
    public final String text;
    private final int[] starts;

    public Corpus(List<LogLine> lines) {
        this.lines = lines;
        StringBuilder sb = new StringBuilder();
        starts = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            starts[i] = sb.length();
            sb.append(lines.get(i).norm()).append('\n');
        }
        text = sb.toString();
    }

    public int lineOf(int pos) {
        int lo = 0, hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= pos) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    public int lineStart(int idx) { return starts[idx]; }
    public long rawBytes() { long b = 0; for (LogLine l : lines) b += l.raw().length() + 1; return b; }
}
