package com.hh.agent.h5bench;

import java.util.Objects;

public class MiniWoBTaskResult {
    private final String taskId;
    private final String category;
    private final boolean success;
    private final double reward;
    private final int steps;
    private final long latencyMs;
    private final String finishReason;

    public MiniWoBTaskResult(String taskId, String category, boolean success, double reward, int steps, long latencyMs, String finishReason) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.category = Objects.requireNonNull(category, "category");
        this.success = success;
        this.reward = reward;
        this.steps = steps;
        this.latencyMs = latencyMs;
        this.finishReason = Objects.requireNonNull(finishReason, "finishReason");
    }

    public static MiniWoBTaskResult success(String taskId, String category, double reward, int steps, long latencyMs) {
        return new MiniWoBTaskResult(taskId, category, true, reward, steps, latencyMs, "success");
    }

    public static MiniWoBTaskResult failure(String taskId, String category, double reward, int steps, long latencyMs, String finishReason) {
        return new MiniWoBTaskResult(taskId, category, false, reward, steps, latencyMs, finishReason);
    }

    public String getTaskId() {
        return taskId;
    }

    public String getCategory() {
        return category;
    }

    public boolean isSuccess() {
        return success;
    }

    public double getReward() {
        return reward;
    }

    public int getSteps() {
        return steps;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getFinishReason() {
        return finishReason;
    }
}
