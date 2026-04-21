package com.hh.agent.h5bench;

import java.util.Objects;

public class MiniWoBInProcessPageController implements MiniWoBBenchmarkRunner.MiniWoBPageController {
    private final TaskHost taskHost;
    private final Runtime runtime;
    private final long pollIntervalMs;

    public MiniWoBInProcessPageController(TaskHost taskHost, Runtime runtime, long pollIntervalMs) {
        this.taskHost = Objects.requireNonNull(taskHost, "taskHost");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.pollIntervalMs = Math.max(0L, pollIntervalMs);
    }

    @Override
    public void load(String assetPath) throws Exception {
        taskHost.openTaskPage(assetPath);
        runtime.waitForReady();
    }

    @Override
    public void clearStorage() throws Exception {
        runtime.clearStorage();
    }

    @Override
    public void startEpisode(int seed) throws Exception {
        runtime.startEpisode(seed);
    }

    @Override
    public MiniWoBBenchmarkRunner.MiniWoBPageStatus awaitCompletion(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        MiniWoBBenchmarkRunner.MiniWoBPageStatus latestStatus =
                new MiniWoBBenchmarkRunner.MiniWoBPageStatus(false, 0.0, 0.0, null, 0L);
        while (System.currentTimeMillis() <= deadline) {
            latestStatus = runtime.readStatus();
            if (latestStatus.isDone()) {
                return latestStatus;
            }
            if (pollIntervalMs > 0L) {
                Thread.sleep(pollIntervalMs);
            }
        }
        return latestStatus;
    }

    public interface TaskHost {
        void openTaskPage(String assetPath) throws Exception;
    }

    public interface Runtime {
        void waitForReady() throws Exception;

        void clearStorage() throws Exception;

        void startEpisode(int seed) throws Exception;

        MiniWoBBenchmarkRunner.MiniWoBPageStatus readStatus() throws Exception;
    }
}
