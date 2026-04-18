package com.hh.agent.h5bench;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MiniWoBInProcessPageControllerTest {
    @Test
    public void loadClearAndStartEpisodeDelegateToHostAndRuntime() throws Exception {
        FakeTaskHost taskHost = new FakeTaskHost();
        FakeRuntime runtime = new FakeRuntime(Arrays.asList(
                new MiniWoBBenchmarkRunner.MiniWoBPageStatus(false, 0.0, 0.0, "episode-1", 0)
        ));
        MiniWoBInProcessPageController controller = new MiniWoBInProcessPageController(taskHost, runtime, 0L);

        controller.load("workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html");
        controller.clearStorage();
        controller.startEpisode(101);

        assertEquals("workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html", taskHost.lastOpenedAssetPath);
        assertEquals(1, runtime.waitForReadyCalls);
        assertEquals(1, runtime.clearStorageCalls);
        assertEquals(101, runtime.lastSeed);
    }

    @Test
    public void awaitCompletionPollsUntilDoneOrTimeout() throws Exception {
        FakeTaskHost taskHost = new FakeTaskHost();
        FakeRuntime runtime = new FakeRuntime(Arrays.asList(
                new MiniWoBBenchmarkRunner.MiniWoBPageStatus(false, 0.0, 0.0, "episode-1", 100),
                new MiniWoBBenchmarkRunner.MiniWoBPageStatus(false, 0.0, 0.0, "episode-1", 200),
                new MiniWoBBenchmarkRunner.MiniWoBPageStatus(true, 1.0, 1.0, "episode-1", 300)
        ));
        MiniWoBInProcessPageController controller = new MiniWoBInProcessPageController(taskHost, runtime, 0L);

        MiniWoBBenchmarkRunner.MiniWoBPageStatus status = controller.awaitCompletion(1000);

        assertEquals(true, status.isDone());
        assertEquals(3, runtime.readStatusCalls);
        assertEquals(1.0, status.getReward(), 0.01);
    }

    private static class FakeTaskHost implements MiniWoBInProcessPageController.TaskHost {
        private String lastOpenedAssetPath;

        @Override
        public void openTaskPage(String assetPath) {
            this.lastOpenedAssetPath = assetPath;
        }
    }

    private static class FakeRuntime implements MiniWoBInProcessPageController.Runtime {
        private final List<MiniWoBBenchmarkRunner.MiniWoBPageStatus> statuses;
        private int waitForReadyCalls;
        private int clearStorageCalls;
        private int lastSeed;
        private int readStatusCalls;
        private int index;

        private FakeRuntime(List<MiniWoBBenchmarkRunner.MiniWoBPageStatus> statuses) {
            this.statuses = statuses;
        }

        @Override
        public void waitForReady() {
            waitForReadyCalls++;
        }

        @Override
        public void clearStorage() {
            clearStorageCalls++;
        }

        @Override
        public void startEpisode(int seed) {
            lastSeed = seed;
        }

        @Override
        public MiniWoBBenchmarkRunner.MiniWoBPageStatus readStatus() {
            readStatusCalls++;
            MiniWoBBenchmarkRunner.MiniWoBPageStatus status = statuses.get(Math.min(index, statuses.size() - 1));
            index++;
            return status;
        }
    }
}
