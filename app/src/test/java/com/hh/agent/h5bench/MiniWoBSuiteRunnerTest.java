package com.hh.agent.h5bench;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MiniWoBSuiteRunnerTest {
    @Test
    public void runsSuiteAndBuildsRunRecordFromAllTasks() throws Exception {
        List<MiniWoBTaskDefinition> tasks = Arrays.asList(
                new MiniWoBTaskDefinition("click-test-2", "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html", "Click button ONE.", "click", 101, 15, 30000),
                new MiniWoBTaskDefinition("scroll-text", "workspace/skills/h5_benchmark_runner/miniwob/scroll-text.html", "Scroll to the bottom.", "scroll", 102, 15, 30000)
        );
        FakeTaskLoader taskLoader = new FakeTaskLoader(tasks);
        FakeBenchmarkRunner benchmarkRunner = new FakeBenchmarkRunner(Arrays.asList(
                MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200),
                MiniWoBTaskResult.failure("scroll-text", "scroll", 0.0, 15, 30000, "timeout")
        ));

        MiniWoBRunRecord runRecord = new MiniWoBSuiteRunner(taskLoader, benchmarkRunner).runSuite(
                "workspace/skills/h5_benchmark_runner/baseline-20.json",
                "run-1",
                "gpt-5.4",
                "openai",
                "prompt-v1",
                "miniwob-v0-baseline-20",
                "seed-set-v1",
                15,
                30000,
                "1.0.0");

        assertEquals("workspace/skills/h5_benchmark_runner/baseline-20.json", taskLoader.lastAssetPath);
        assertEquals(2, benchmarkRunner.executedTaskIds.size());
        assertEquals("click-test-2", benchmarkRunner.executedTaskIds.get(0));
        assertEquals("scroll-text", benchmarkRunner.executedTaskIds.get(1));
        assertEquals("run-1", runRecord.getRunId());
        assertEquals("gpt-5.4", runRecord.getModel());
        assertEquals(50.0, runRecord.getSummary().getSuccessRate(), 0.01);
        assertEquals(2, runRecord.getResults().size());
    }

    private static class FakeTaskLoader implements MiniWoBSuiteRunner.TaskLoader {
        private final List<MiniWoBTaskDefinition> tasks;
        private String lastAssetPath;

        private FakeTaskLoader(List<MiniWoBTaskDefinition> tasks) {
            this.tasks = tasks;
        }

        @Override
        public List<MiniWoBTaskDefinition> loadSuite(String assetPath) {
            this.lastAssetPath = assetPath;
            return tasks;
        }
    }

    private static class FakeBenchmarkRunner extends MiniWoBBenchmarkRunner {
        private final List<MiniWoBTaskResult> results;
        private int index;
        private final java.util.List<String> executedTaskIds = new java.util.ArrayList<>();

        private FakeBenchmarkRunner(List<MiniWoBTaskResult> results) {
            super(new NoopPageController(), new NoopAgentRunDriver());
            this.results = results;
        }

        @Override
        public MiniWoBTaskResult run(MiniWoBTaskDefinition task) {
            executedTaskIds.add(task.getTaskId());
            return results.get(index++);
        }
    }

    private static class NoopPageController implements MiniWoBBenchmarkRunner.MiniWoBPageController {
        @Override
        public void load(String assetPath) {
        }

        @Override
        public void clearStorage() {
        }

        @Override
        public void startEpisode(int seed) {
        }

        @Override
        public MiniWoBBenchmarkRunner.MiniWoBPageStatus awaitCompletion(long timeoutMs) {
            return new MiniWoBBenchmarkRunner.MiniWoBPageStatus(true, 1.0, 1.0, "noop", 1);
        }
    }

    private static class NoopAgentRunDriver extends MiniWoBAgentRunDriver {
        @Override
        public int runTask(MiniWoBTaskDefinition task) {
            return 0;
        }
    }
}
