package com.hh.agent.h5bench;

import java.util.List;
import java.util.Objects;

public class MiniWoBBenchmarkRunner {
    private final MiniWoBPageController pageController;
    private final MiniWoBAgentRunDriver agentRunDriver;
    private final MiniWoBScoreAggregator scoreAggregator;

    public MiniWoBBenchmarkRunner(MiniWoBPageController pageController, MiniWoBAgentRunDriver agentRunDriver) {
        this(pageController, agentRunDriver, new MiniWoBScoreAggregator());
    }

    MiniWoBBenchmarkRunner(MiniWoBPageController pageController, MiniWoBAgentRunDriver agentRunDriver, MiniWoBScoreAggregator scoreAggregator) {
        this.pageController = Objects.requireNonNull(pageController, "pageController");
        this.agentRunDriver = Objects.requireNonNull(agentRunDriver, "agentRunDriver");
        this.scoreAggregator = Objects.requireNonNull(scoreAggregator, "scoreAggregator");
    }

    public MiniWoBTaskResult run(MiniWoBTaskDefinition task) throws Exception {
        long startMs = System.currentTimeMillis();
        pageController.load(task.getAssetPath());
        pageController.clearStorage();
        pageController.startEpisode(task.getSeed());
        int steps = agentRunDriver.runTask(task);
        MiniWoBPageStatus status = pageController.awaitCompletion(task.getTimeoutMs());
        long latencyMs = status.getElapsedMs() > 0 ? status.getElapsedMs() : (System.currentTimeMillis() - startMs);

        if (!status.isDone()) {
            return MiniWoBTaskResult.failure(
                    task.getTaskId(),
                    task.getCategory(),
                    status.getReward(),
                    steps,
                    latencyMs,
                    "timeout");
        }

        if (status.getReward() > 0.0) {
            return MiniWoBTaskResult.success(
                    task.getTaskId(),
                    task.getCategory(),
                    status.getReward(),
                    steps,
                    latencyMs);
        }

        return MiniWoBTaskResult.failure(
                task.getTaskId(),
                task.getCategory(),
                status.getReward(),
                steps,
                latencyMs,
                "completed_without_reward");
    }

    public MiniWoBRunRecord buildRunRecord(
            String runId,
            String model,
            String provider,
            String promptVersion,
            String suiteId,
            String seedSetVersion,
            int maxSteps,
            long timeoutMs,
            String appVersion,
            List<MiniWoBTaskResult> results) {
        MiniWoBSuiteSummary summary = scoreAggregator.summarize(
                suiteId,
                runId,
                model,
                provider,
                promptVersion,
                results);
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
                results,
                summary);
    }

    public interface MiniWoBPageController {
        void load(String assetPath) throws Exception;

        void clearStorage() throws Exception;

        void startEpisode(int seed) throws Exception;

        MiniWoBPageStatus awaitCompletion(long timeoutMs) throws Exception;
    }

    public static class MiniWoBPageStatus {
        private final boolean done;
        private final double reward;
        private final double rawReward;
        private final String episodeId;
        private final long elapsedMs;

        public MiniWoBPageStatus(boolean done, double reward, double rawReward, String episodeId, long elapsedMs) {
            this.done = done;
            this.reward = reward;
            this.rawReward = rawReward;
            this.episodeId = episodeId;
            this.elapsedMs = elapsedMs;
        }

        public boolean isDone() {
            return done;
        }

        public double getReward() {
            return reward;
        }

        public double getRawReward() {
            return rawReward;
        }

        public String getEpisodeId() {
            return episodeId;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }
}
