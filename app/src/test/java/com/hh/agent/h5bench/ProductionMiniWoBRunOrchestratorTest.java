package com.hh.agent.h5bench;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProductionMiniWoBRunOrchestratorTest {
    @Test
    public void runsSingleSuiteAndClosesBenchmarkHost() throws Exception {
        final boolean[] closed = {false};
        FakeSuiteExecutor suiteExecutor = new FakeSuiteExecutor();
        ProductionMiniWoBRunOrchestrator orchestrator = new ProductionMiniWoBRunOrchestrator(
                suiteExecutor,
                () -> closed[0] = true,
                "android-agent-default",
                "in_app",
                "workspace@v1",
                "1.0.0");

        List<MiniWoBRunRecord> records = orchestrator.runBenchmarks();

        assertEquals(1, records.size());
        assertEquals("workspace/skills/h5_benchmark_runner/baseline-20.json", suiteExecutor.lastSuiteAssetPath);
        assertEquals("miniwob-v0-baseline-20", suiteExecutor.lastSuiteId);
        assertEquals(15, suiteExecutor.lastMaxSteps);
        assertEquals(30000L, suiteExecutor.lastTimeoutMs);
        assertEquals("android-agent-default", records.get(0).getModel());
        assertTrue(closed[0]);
    }

    @Test
    public void closesBenchmarkHostWhenSuiteExecutionFails() {
        final boolean[] closed = {false};
        ProductionMiniWoBRunOrchestrator orchestrator = new ProductionMiniWoBRunOrchestrator(
                (suiteAssetPath, runId, model, provider, promptVersion, suiteId, seedSetVersion, maxSteps, timeoutMs, appVersion) -> {
                    throw new IllegalStateException("benchmark_activity_not_ready");
                },
                () -> closed[0] = true,
                "android-agent-default",
                "in_app",
                "workspace@v1",
                "1.0.0");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                orchestrator::runBenchmarks);

        assertEquals("benchmark_activity_not_ready", exception.getMessage());
        assertTrue(closed[0]);
    }

    private static class FakeSuiteExecutor implements ProductionMiniWoBRunOrchestrator.SuiteExecutor {
        private String lastSuiteAssetPath;
        private String lastSuiteId;
        private int lastMaxSteps;
        private long lastTimeoutMs;

        @Override
        public MiniWoBRunRecord runSuite(String suiteAssetPath, String runId, String model, String provider, String promptVersion, String suiteId, String seedSetVersion, int maxSteps, long timeoutMs, String appVersion) {
            this.lastSuiteAssetPath = suiteAssetPath;
            this.lastSuiteId = suiteId;
            this.lastMaxSteps = maxSteps;
            this.lastTimeoutMs = timeoutMs;
            MiniWoBTaskResult result = MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200);
            MiniWoBSuiteSummary summary = new MiniWoBScoreAggregator().summarize(
                    suiteId,
                    runId,
                    model,
                    provider,
                    promptVersion,
                    java.util.Collections.singletonList(result));
            return new MiniWoBRunRecord(
                    runId,
                    model,
                    provider,
                    promptVersion,
                    suiteId,
                    seedSetVersion,
                    maxSteps,
                    timeoutMs,
                    appVersion,
                    java.util.Collections.singletonList(result),
                    summary);
        }
    }
}
