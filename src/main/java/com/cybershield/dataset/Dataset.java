package com.cybershield.dataset;

import com.cybershield.algorithm.AlgorithmInput;
import com.cybershield.dataset.Corpus.LogLine;
import com.cybershield.dataset.Models.*;
import java.util.*;

/** One analysable dataset bundle. Parsed pieces are cached lazily; custom datasets override only the log text. */
public final class Dataset {
    private final String id, name, description, baseId;
    private final String uploadedLog;
    private volatile Corpus corpus;
    private volatile long corpusBuildNanos;
    private volatile List<Flow> flows;
    private volatile List<Signature> signatures;

    public Dataset(String id, String name, String description, String baseId, String uploadedLog) {
        this.id = id; this.name = name; this.description = description; this.baseId = baseId; this.uploadedLog = uploadedLog;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public boolean custom() { return uploadedLog != null; }
    public long corpusBuildMillis() { corpus(); return corpusBuildNanos / 1_000_000; }

    private List<String> read(String file) { return Resources.lines(Resources.read(baseId, file)); }
    private static boolean data(String l) { return !l.isBlank() && !l.startsWith("#"); }

    // ---------------------------------------------------------------- logs (Input + Preprocessing stages)
    public Corpus corpus() {
        Corpus c = corpus;
        if (c == null) synchronized (this) {
            if ((c = corpus) == null) {
                long t0 = System.nanoTime();
                List<LogLine> lines = new ArrayList<>();
                if (uploadedLog != null) addLines(lines, "upload.log", Resources.lines(uploadedLog));
                else for (String f : new String[]{"access.log", "auth.log"}) addLines(lines, f, read(f));
                c = new Corpus(lines);
                corpusBuildNanos = System.nanoTime() - t0;
                corpus = c;
            }
        }
        return c;
    }

    private static void addLines(List<LogLine> out, String source, List<String> raw) {
        for (int i = 0; i < raw.size(); i++) out.add(new LogLine(out.size(), source, i + 1, raw.get(i), Preprocessor.normalize(raw.get(i))));
    }

    public List<Signature> signatures() {
        List<Signature> s = signatures;
        if (s == null) {
            s = new ArrayList<>();
            for (String l : read("signatures.txt")) {
                if (!data(l)) continue;
                String[] p = l.split("\\|", 3);
                if (p.length != 3) throw new IllegalArgumentException("Bad signature line: " + l);
                s.add(new Signature(p[0].trim(), Integer.parseInt(p[1].trim()), p[2].toLowerCase(Locale.ROOT)));
            }
            signatures = s;
        }
        return s;
    }

    public List<String> categories() {
        LinkedHashSet<String> c = new LinkedHashSet<>();
        for (Signature s : signatures()) c.add(s.category());
        return new ArrayList<>(c);
    }

    /** source:line → label for every injected record (ground truth). */
    public Map<String, String> labels() {
        Map<String, String> m = new LinkedHashMap<>();
        if (uploadedLog != null) return m;
        List<String> l = read("labels.csv");
        for (int i = 1; i < l.size(); i++) {
            String[] p = l.get(i).split(",");
            if (p.length == 3) m.put(p[0] + ":" + p[1], p[2]);
        }
        return m;
    }

    // ---------------------------------------------------------------- network flows
    public List<Flow> flows() {
        List<Flow> f = flows;
        if (f == null) {
            f = new ArrayList<>();
            List<String> l = read("netflow.csv");
            for (int i = 1; i < l.size(); i++) {
                String[] p = l.get(i).split(",");
                if (p.length < 9) continue;
                f.add(new Flow(Long.parseLong(p[0]), p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]), p[5], Long.parseLong(p[6]), Integer.parseInt(p[7]), p[8]));
            }
            flows = f;
        }
        return f;
    }

    /** Number of flows starting in each minute of the capture. */
    public int[] flowsPerMinute() {
        List<Flow> f = flows();
        if (f.isEmpty()) return new int[0];
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (Flow x : f) { min = Math.min(min, x.ts()); max = Math.max(max, x.ts()); }
        int[] c = new int[(int) ((max - min) / 60) + 1];
        for (Flow x : f) c[(int) ((x.ts() - min) / 60)]++;
        return c;
    }

    // ---------------------------------------------------------------- other structured inputs
    public List<String[]> processNames() {
        List<String[]> out = new ArrayList<>();
        for (String l : read("process_names.txt")) if (data(l)) out.add(l.split("\\|", 2));
        return out;
    }

    public List<String> processEventLines() { return read("process_events.txt"); }

    public List<String> processEventTokens() {
        LinkedHashSet<String> t = new LinkedHashSet<>();
        for (String l : read("process_events.txt")) {
            if (!data(l)) continue;
            String[] p = l.split(",");
            t.add(p[p.length - 1].trim());
        }
        return new ArrayList<>(t);
    }

    public List<AlgorithmInput.Rule> rules() {
        List<String> cats = categories();
        List<AlgorithmInput.Rule> out = new ArrayList<>();
        for (String l : read("rules.csv")) {
            if (!data(l)) continue;
            String[] p = l.split(",", 4);
            int mask = 0;
            for (String c : p[3].split("\\|")) {
                int idx = cats.indexOf(c.trim());
                if (idx < 0) throw new IllegalArgumentException("Rule " + p[0] + " references unknown category " + c);
                mask |= 1 << idx;
            }
            out.add(new AlgorithmInput.Rule(p[1], Integer.parseInt(p[2].trim()), mask));
        }
        return out;
    }

    public Pipeline pipeline() {
        List<String> names = new ArrayList<>();
        int[] dims = new int[0];
        for (String l : read("pipeline_dims.txt")) {
            if (l.startsWith("names=")) names = Arrays.asList(l.substring(6).split(","));
            else if (l.startsWith("dims=")) dims = Arrays.stream(l.substring(5).split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        }
        return new Pipeline(names, dims);
    }

    public Topology topology() {
        String src = null, snk = null;
        List<String> names = new ArrayList<>();
        List<String[]> es = new ArrayList<>();
        for (String l : read("topology.csv")) {
            if (l.startsWith("#source=")) { src = l.substring(8).trim(); continue; }
            if (l.startsWith("#sink=")) { snk = l.substring(6).trim(); continue; }
            if (!data(l)) continue;
            String[] p = l.split(",");
            es.add(p);
            for (int i = 0; i < 2; i++) if (!names.contains(p[i])) names.add(p[i]);
        }
        if (src == null || snk == null || !names.contains(src) || !names.contains(snk)) throw new IllegalArgumentException("topology.csv must declare #source= and #sink= naming existing nodes");
        int[][] e = new int[es.size()][];
        for (int i = 0; i < e.length; i++) e[i] = new int[]{names.indexOf(es.get(i)[0]), names.indexOf(es.get(i)[1]), Integer.parseInt(es.get(i)[2].trim())};
        return new Topology(names, e, names.indexOf(src), names.indexOf(snk));
    }

    public Assignments assignments() {
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        List<String[]> es = new ArrayList<>();
        for (String l : read("assignments.csv")) {
            if (!data(l)) continue;
            String[] p = l.split(",");
            es.add(p);
            if (!left.contains(p[0])) left.add(p[0]);
            if (!right.contains(p[1])) right.add(p[1]);
        }
        List<String> names = new ArrayList<>(left);
        names.addAll(right);
        int[][] e = new int[es.size()][];
        for (int i = 0; i < e.length; i++) e[i] = new int[]{left.indexOf(es.get(i)[0]), left.size() + right.indexOf(es.get(i)[1])};
        return new Assignments(names, left.size(), right.size(), e);
    }

    public Cnf cnf() { return DimacsParser.parse(Resources.read(baseId, "policies.cnf")); }

    public Graph dependencies() {
        List<String> names = new ArrayList<>();
        List<String[]> es = new ArrayList<>();
        for (String l : read("dependencies.csv")) {
            if (!data(l)) continue;
            String[] p = l.split(",");
            es.add(p);
            for (String s : p) if (!names.contains(s)) names.add(s);
        }
        int[][] e = new int[es.size()][];
        for (int i = 0; i < e.length; i++) e[i] = new int[]{names.indexOf(es.get(i)[0]), names.indexOf(es.get(i)[1])};
        return new Graph(names, e);
    }

    public Numbers numbers() {
        List<String> v = new ArrayList<>(), lab = new ArrayList<>();
        for (String l : read("numbers.csv")) {
            if (!data(l)) continue;
            String[] p = l.split(",");
            v.add(p[0].trim());
            lab.add(p.length > 1 ? p[1].trim() : "");
        }
        return new Numbers(v, lab);
    }
}
