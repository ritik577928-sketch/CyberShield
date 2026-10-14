package com.cybershield.dataset;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Registry of the bundled dataset plus any logs uploaded through the dashboard. */
public final class DatasetRepository {
    public static final String DEFAULT_ID = "enterprise-sample";
    public static final int MAX_UPLOAD_CHARS = 8_000_000;
    private static final Logger LOG = Logger.getLogger(DatasetRepository.class.getName());
    private final Map<String, Dataset> datasets = new ConcurrentHashMap<>();

    public DatasetRepository() {
        datasets.put(DEFAULT_ID, new Dataset(DEFAULT_ID, "Enterprise sample (web + SSH + NetFlow)",
                "Apache access log, OpenSSH auth log, NetFlow CSV and supporting security data with labelled ground truth.", DEFAULT_ID, null));
    }

    public Dataset get(String id) {
        Dataset d = datasets.get(id == null || id.isBlank() ? DEFAULT_ID : id);
        if (d == null) throw new NoSuchElementException("Unknown dataset: " + id);
        return d;
    }

    public List<Dataset> list() { return new ArrayList<>(datasets.values()); }

    public Dataset upload(String name, String logText) {
        if (logText == null || logText.isBlank()) throw new IllegalArgumentException("Uploaded log is empty");
        if (logText.length() > MAX_UPLOAD_CHARS) throw new IllegalArgumentException("Uploaded log exceeds " + MAX_UPLOAD_CHARS + " characters");
        String safe = (name == null || name.isBlank() ? "upload" : name).replaceAll("[^A-Za-z0-9_-]", "-");
        String id = "custom-" + safe;
        Dataset d = new Dataset(id, "Custom log: " + safe, "Uploaded log analysed with the bundled signature set; other inputs come from the sample dataset.", DEFAULT_ID, logText);
        datasets.put(id, d);
        LOG.info("Uploaded dataset " + id + " (" + logText.length() + " chars)");
        return d;
    }
}
