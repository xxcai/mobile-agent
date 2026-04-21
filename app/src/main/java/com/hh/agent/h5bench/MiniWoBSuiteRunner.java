package com.hh.agent.h5bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MiniWoBSuiteRunner {
    private final TaskLoader taskLoader;
    private final MiniWoBBenchmarkRunner benchmarkRunner;

    public MiniWoBSuiteRunner(TaskLoader taskLoader, MiniWoBBenchmarkRunner benchmarkRunner) {
        this.taskLoader = Objects.requireNonNull(taskLoader, "taskLoader");
        this.benchmarkRunner = Objects.requireNonNull(benchmarkRunner, "benchmarkRunner");
    }

    public MiniWoBRunRecord runSuite(
            String suiteAssetPath,
            String runId,
            String model,
            String provider,
            String promptVersion,
            String suiteId,
            String seedSetVersion,
            int maxSteps,
            long timeoutMs,
            String appVersion) throws Exception {
        List<MiniWoBTaskDefinition> tasks = taskLoader.loadSuite(suiteAssetPath);
        List<MiniWoBTaskResult> results = new ArrayList<>();
        for (MiniWoBTaskDefinition task : tasks) {
            results.add(benchmarkRunner.run(task));
        }
        return benchmarkRunner.buildRunRecord(
                runId,
                model,
                provider,
                promptVersion,
                suiteId,
                seedSetVersion,
                maxSteps,
                timeoutMs,
                appVersion,
                results);
    }

    public interface TaskLoader {
        List<MiniWoBTaskDefinition> loadSuite(String assetPath) throws Exception;
    }
}
