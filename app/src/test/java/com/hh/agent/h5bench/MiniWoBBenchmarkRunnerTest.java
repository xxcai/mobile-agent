package com.hh.agent.h5bench;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class MiniWoBBenchmarkRunnerTest {
    @Test
    public void marksTimeoutWhenDoneFlagNeverTurnsTrue() throws Exception {
        FakeMiniWoBPageController pageController = new FakeMiniWoBPageController(
                new MiniWoBBenchmarkRunner.MiniWoBPageStatus(false, 0.0, 0.0, "episode-timeout", 30000));
        FakeMiniWoBAgentRunDriver agentDriver = new FakeMiniWoBAgentRunDriver(15);

        MiniWoBTaskResult result = new MiniWoBBenchmarkRunner(pageController, agentDriver)
                .run(new MiniWoBTaskDefinition("scroll-text", "workspace/skills/h5_benchmark_runner/miniwob/scroll-text.html",
                        "Scroll to the bottom.", "scroll", 301, 15, 30000));

        assertEquals("timeout", result.getFinishReason());
        assertEquals(15, result.getSteps());
        assertEquals("workspace/skills/h5_benchmark_runner/miniwob/scroll-text.html", pageController.lastLoadedAssetPath);
        assertEquals(301, pageController.lastSeed);
    }

    @Test
    public void buildsRunRecordWithSummaryMetadata() {
        MiniWoBTaskResult success = MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200);
        MiniWoBBenchmarkRunner runner = new MiniWoBBenchmarkRunner(
                new FakeMiniWoBPageController(new MiniWoBBenchmarkRunner.MiniWoBPageStatus(true, 1.0, 1.0, "episode-1", 3200)),
                new FakeMiniWoBAgentRunDriver(4));

        MiniWoBRunRecord record = runner.buildRunRecord(
                "run-1",
                "gpt-5.4",
                "openai",
                "prompt-v1",
                "miniwob-v0-baseline-20",
                "seed-set-v1",
                15,
                30000,
                "1.0.0",
                Collections.singletonList(success));

        assertEquals("run-1", record.getRunId());
        assertEquals("gpt-5.4", record.getModel());
        assertEquals(100.0, record.getSummary().getSuccessRate(), 0.01);
    }

    private static class FakeMiniWoBAgentRunDriver extends MiniWoBAgentRunDriver {
        private final int steps;

        FakeMiniWoBAgentRunDriver(int steps) {
            this.steps = steps;
        }

        @Override
        public int runTask(MiniWoBTaskDefinition task) {
            return steps;
        }
    }

    private static class FakeMiniWoBPageController implements MiniWoBBenchmarkRunner.MiniWoBPageController {
        private final MiniWoBBenchmarkRunner.MiniWoBPageStatus pageStatus;
        private String lastLoadedAssetPath;
        private int lastSeed;

        FakeMiniWoBPageController(MiniWoBBenchmarkRunner.MiniWoBPageStatus pageStatus) {
            this.pageStatus = pageStatus;
        }

        @Override
        public void load(String assetPath) {
            this.lastLoadedAssetPath = assetPath;
        }

        @Override
        public void clearStorage() {
        }

        @Override
        public void startEpisode(int seed) {
            this.lastSeed = seed;
        }

        @Override
        public MiniWoBBenchmarkRunner.MiniWoBPageStatus awaitCompletion(long timeoutMs) {
            return pageStatus;
        }
    }
}
