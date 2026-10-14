package com.cybershield.algorithm.suffix;

import com.cybershield.algorithm.AlgorithmResult;
import com.cybershield.execution.ExecutionContext;
import java.util.*;

/** Shared post-processing: Kasai LCP array and "repeated attack phrase" extraction. */
public final class SuffixAnalysis {
    private SuffixAnalysis() {}

    /** Kasai's algorithm. lcp[i] = LCP of suffixes sa[i-1] and sa[i]; lcp[0] = 0. */
    public static int[] kasai(String s, int[] sa, ExecutionContext ctx) {
        int n = s.length();
        int[] rank = new int[n], lcp = new int[n];
        for (int i = 0; i < n; i++) rank[sa[i]] = i;
        int h = 0;
        for (int i = 0; i < n; i++) {
            if (rank[i] > 0) {
                int j = sa[rank[i] - 1];
                int start = h;
                while (i + h < n && j + h < n && s.charAt(i + h) == s.charAt(j + h)) { h++; ctx.cmp(); }
                ctx.cmp();
                ctx.op();
                lcp[rank[i]] = h;
                if (ctx.tracing())
                    ctx.trace.emit("kasai", "Suffix " + i + " (rank " + rank[i] + ") vs previous suffix " + j + ": start at h=" + start + ", extend to LCP = " + h + ". Then h drops by one for the next suffix.",
                            "i", i, "rank", rank[i], "prev", j, "h", h, "lcp", lcp.clone());
                if (h > 0) h--;
            } else {
                h = 0;
            }
        }
        return lcp;
    }

    /** Fills longest repeated substring + top repeated phrases into the result. */
    public static void summarize(String text, int[] sa, int[] lcp, AlgorithmResult r, int minLen) {
        int n = text.length(), best = 0, bestIdx = 0;
        for (int i = 1; i < n; i++) if (lcp[i] > best) { best = lcp[i]; bestIdx = i; }
        String longest = best > 0 ? text.substring(sa[bestIdx], sa[bestIdx] + best) : "";
        List<Object[]> phrases = new ArrayList<>();
        for (int i = 1; i < n; ) {
            if (lcp[i] < minLen) { i++; continue; }
            int j = i, min = lcp[i];
            while (j + 1 < n && lcp[j + 1] >= minLen) { j++; min = Math.min(min, lcp[j]); }
            phrases.add(new Object[]{text.substring(sa[i], sa[i] + min), j - i + 2, min});
            i = j + 1;
        }
        phrases.sort((a, b) -> Long.compare((long) (int) b[1] * (int) b[2], (long) (int) a[1] * (int) a[2]));
        r.put("length", n).put("longestRepeatedLength", best).put("longestRepeated", display(longest)).check(best);
        List<Map<String, Object>> top = new ArrayList<>();
        for (int i = 0; i < Math.min(10, phrases.size()); i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phrase", display((String) phrases.get(i)[0]));
            m.put("count", phrases.get(i)[1]);
            m.put("length", phrases.get(i)[2]);
            top.add(m);
            r.item("phrase", display((String) phrases.get(i)[0]), "count", phrases.get(i)[1], "length", phrases.get(i)[2]);
        }
        r.put("topRepeatedPhrases", top);
        r.headline = "Longest repeated substring: " + best + " chars; " + phrases.size() + " repeated phrase group(s) (≥" + minLen + " chars)";
    }

    public static String display(String s) { return s.replace('\n', '⏎'); }
}
