package com.hh.agent.h5bench;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class MiniWoBSuiteSummary {
    private final String suiteId;
    private final String runId;
    private final String model;
    private final String provider;
    private final String promptVersion;
    private final double successRate;
    private final double avgSuccessSteps;
    private final double avgSuccessLatencyMs;
    private final double timeoutRate;
    private final double avgReward;
    private final Map<String, CategoryBreakdown> categoryBreakdown;

    public MiniWoBSuiteSummary(
            String suiteId,
            String runId,
            String model,
            String provider,
            String promptVersion,
            double successRate,
            double avgSuccessSteps,
            double avgSuccessLatencyMs,
            double timeoutRate,
            double avgReward,
            Map<String, CategoryBreakdown> categoryBreakdown) {
        this.suiteId = Objects.requireNonNull(suiteId, "suiteId");
        this.runId = runId;
        this.model = model;
        this.provider = provider;
        this.promptVersion = promptVersion;
        this.successRate = successRate;
        this.avgSuccessSteps = avgSuccessSteps;
        this.avgSuccessLatencyMs = avgSuccessLatencyMs;
        this.timeoutRate = timeoutRate;
        this.avgReward = avgReward;
        this.categoryBreakdown = Collections.unmodifiableMap(new LinkedHashMap<>(categoryBreakdown));
    }

    public String getSuiteId() {
        return suiteId;
    }

    public String getRunId() {
        return runId;
    }

    public String getModel() {
        return model;
    }

    public String getProvider() {
        return provider;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public double getAvgSuccessSteps() {
        return avgSuccessSteps;
    }

    public double getAvgSuccessLatencyMs() {
        return avgSuccessLatencyMs;
    }

    public double getTimeoutRate() {
        return timeoutRate;
    }

    public double getAvgReward() {
        return avgReward;
    }

    public Map<String, CategoryBreakdown> getCategoryBreakdown() {
        return categoryBreakdown;
    }

    public static class CategoryBreakdown {
        private final int totalCount;
        private final int successCount;
        private final double successRate;

        public CategoryBreakdown(int totalCount, int successCount, double successRate) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.successRate = successRate;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public double getSuccessRate() {
            return successRate;
        }
    }
}
