package com.cybershield.service;

import com.cybershield.dataset.DatasetRepository;
import com.cybershield.execution.ExecutionEngine;
import java.nio.file.Path;

/** Wires the service layer together (manual dependency injection keeps the code easy to follow). */
public final class AppContext {
    public final DatasetRepository datasets = new DatasetRepository();
    public final AlgorithmRegistry registry = new AlgorithmRegistry();
    public final ExecutionEngine engine = new ExecutionEngine();
    public final DetectionService detection = new DetectionService(engine);
    public final InputFactory inputs = new InputFactory(detection);
    public final HistoryService history;
    public final ComparisonService comparison;
    public final BenchmarkService benchmark;
    public final DatasetProfiler profiler = new DatasetProfiler();

    public AppContext(Path historyFile) {
        history = new HistoryService(historyFile);
        engine.setListener(history::add);
        comparison = new ComparisonService(registry, engine, inputs);
        benchmark = new BenchmarkService(registry, engine, inputs);
    }
}
