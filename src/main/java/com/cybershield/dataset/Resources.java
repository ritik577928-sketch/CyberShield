package com.cybershield.dataset;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads dataset files from ./datasets/&lt;id&gt;/ (editable on disk) or, as a fallback, from the packaged classpath copy. */
public final class Resources {
    private Resources() {}

    public static boolean exists(String datasetId, String file) {
        return Files.isRegularFile(Paths.get("datasets", datasetId, file)) || Resources.class.getResource("/datasets/" + datasetId + "/" + file) != null;
    }

    public static String read(String datasetId, String file) {
        Path p = Paths.get("datasets", datasetId, file);
        try {
            if (Files.isRegularFile(p)) return Files.readString(p, StandardCharsets.UTF_8);
            try (InputStream in = Resources.class.getResourceAsStream("/datasets/" + datasetId + "/" + file)) {
                if (in == null) throw new FileNotFoundException("Dataset file not found: " + datasetId + "/" + file);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read dataset file " + datasetId + "/" + file + ": " + e.getMessage(), e);
        }
    }

    public static List<String> lines(String text) {
        if (text.isEmpty()) return new ArrayList<>();
        List<String> out = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }
}
