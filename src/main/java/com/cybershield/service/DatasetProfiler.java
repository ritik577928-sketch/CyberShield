package com.cybershield.service;

import com.cybershield.dataset.*;
import com.cybershield.dataset.Corpus.LogLine;
import com.cybershield.dataset.Models.Flow;
import java.util.*;
import java.util.regex.*;

/** Computes the "dataset overview" numbers shown on the dashboard directly from the real files. */
public final class DatasetProfiler {
    private static final Pattern IP = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3}) ");
    private static final Pattern HTTP = Pattern.compile("\"([A-Z]+) \\S+ HTTP/[\\d.]+\" (\\d{3}) ");

    public Map<String, Object> profile(Dataset d) {
        Corpus c = d.corpus();
        Map<String, Integer> perSource = new LinkedHashMap<>(), methods = new TreeMap<>(), status = new TreeMap<>(), ips = new HashMap<>();
        for (LogLine l : c.lines) {
            perSource.merge(l.source(), 1, Integer::sum);
            Matcher m = IP.matcher(l.raw());
            if (m.find()) ips.merge(m.group(1), 1, Integer::sum);
            Matcher h = HTTP.matcher(l.raw());
            if (h.find()) { methods.merge(h.group(1), 1, Integer::sum); status.merge(h.group(2), 1, Integer::sum); }
        }
        List<Map<String, Object>> top = new ArrayList<>();
        ips.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6).forEach(e -> top.add(Map.of("ip", e.getKey(), "count", e.getValue())));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.id());
        out.put("name", d.name());
        out.put("description", d.description());
        out.put("custom", d.custom());
        out.put("lines", c.lines.size());
        out.put("kilobytes", c.rawBytes() / 1024);
        out.put("perSource", perSource);
        out.put("uniqueSourceIps", ips.size());
        out.put("topSourceIps", top);
        out.put("httpMethods", methods);
        out.put("httpStatus", status);
        out.put("signatures", d.signatures().size());
        out.put("categories", d.categories());
        out.put("preview", c.lines.stream().limit(8).map(l -> l.source() + ":" + l.number() + "  " + (l.raw().length() > 150 ? l.raw().substring(0, 150) + "…" : l.raw())).toList());
        if (!d.custom()) {
            List<Flow> flows = d.flows();
            long bytes = 0;
            Map<String, Integer> proto = new TreeMap<>(), ports = new HashMap<>();
            Set<String> srcs = new HashSet<>();
            for (Flow f : flows) { bytes += f.bytes(); proto.merge(f.proto(), 1, Integer::sum); ports.merge(String.valueOf(f.dport()), 1, Integer::sum); srcs.add(f.src()); }
            List<Map<String, Object>> topPorts = new ArrayList<>();
            ports.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6).forEach(e -> topPorts.add(Map.of("port", e.getKey(), "flows", e.getValue())));
            Map<String, Object> net = new LinkedHashMap<>();
            net.put("flows", flows.size());
            net.put("megabytes", bytes / 1_048_576);
            net.put("uniqueSources", srcs.size());
            net.put("protocols", proto);
            net.put("topPorts", topPorts);
            out.put("network", net);
        }
        return out;
    }
}
