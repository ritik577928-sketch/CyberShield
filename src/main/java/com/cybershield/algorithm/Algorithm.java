package com.cybershield.algorithm;

import com.cybershield.execution.ExecutionContext;

/** Contract implemented by every CyberShield algorithm. Implementations must be stateless / thread-safe. */
public interface Algorithm {
    AlgorithmInfo info();

    /** Run on the given input, counting work on {@code ctx} and emitting trace events if tracing is on. */
    AlgorithmResult run(AlgorithmInput in, ExecutionContext ctx) throws Exception;
}
