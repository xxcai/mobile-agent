package com.hh.agent.h5bench;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ProductionMiniWoBRunOrchestrator implements MiniWoBRunOrchestrator {
    static final String SUITE_ASSET_PATH = "workspace/skills/h5_benchmark_runner/baseline-20.json";
    static final String SUITE_ID = "miniwob-v0-baseline-20";
    static final String SEED_SET_VERSION = "miniwob-v0-baseline-20@v1";
    static final int DEFAULT_MAX_STEPS = 15;
    static final long DEFAULT_TIMEOUT_MS = 30000L;

    private final SuiteExecutor suiteExecutor;
    private final BenchmarkHostCloser benchmarkHostCloser;
    private final String model;
    private final String provider;
    private final String promptVersion;
    private final String appVersion;

    public ProductionMiniWoBRunOrchestrator(
            SuiteExecutor suiteExecutor,
            BenchmarkHostCloser benchmarkHostCloser,
            String model,
            String provider,
            String promptVersion,
            String appVersion) {
        this.suiteExecutor = Objects.requireNonNull(suiteExecutor, "suiteExecutor");
        this.benchmarkHostCloser = Objects.requireNonNull(benchmarkHostCloser, "benchmarkHostCloser");
        this.model = Objects.requireNonNull(model, "model");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion");
        this.appVersion = Objects.requireNonNull(appVersion, "appVersion");
    }

    @Override
    public List<MiniWoBRunRecord> runBenchmarks() throws Exception {
        String runId = "miniwob-" + UUID.randomUUID();
        try {
            MiniWoBRunRecord runRecord = suiteExecutor.runSuite(
                    SUITE_ASSET_PATH,
                    runId,
                    model,
                    provider,
                    promptVersion,
                    SUITE_ID,
                    SEED_SET_VERSION,
                    DEFAULT_MAX_STEPS,
                    DEFAULT_TIMEOUT_MS,
                    appVersion);
            return Collections.singletonList(runRecord);
        } finally {
            benchmarkHostCloser.closeIfOpen();
        }
    }

    public interface SuiteExecutor {
        MiniWoBRunRecord runSuite(
                String suiteAssetPath,
                String runId,
                String model,
                String provider,
                String promptVersion,
                String suiteId,
                String seedSetVersion,
                int maxSteps,
                long timeoutMs,
                String appVersion) throws Exception;
    }

    public interface BenchmarkHostCloser {
        void closeIfOpen() throws Exception;
    }
}
