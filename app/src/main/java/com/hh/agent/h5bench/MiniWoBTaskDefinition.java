package com.hh.agent.h5bench;

import java.util.Objects;

public class MiniWoBTaskDefinition {
    private final String taskId;
    private final String assetPath;
    private final String instruction;
    private final String category;
    private final int seed;
    private final int maxSteps;
    private final int timeoutMs;

    public MiniWoBTaskDefinition(String taskId, String assetPath, String instruction, String category, int seed, int maxSteps, int timeoutMs) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.assetPath = Objects.requireNonNull(assetPath, "assetPath");
        this.instruction = Objects.requireNonNull(instruction, "instruction");
        this.category = Objects.requireNonNull(category, "category");
        this.seed = seed;
        this.maxSteps = maxSteps;
        this.timeoutMs = timeoutMs;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAssetPath() {
        return assetPath;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getCategory() {
        return category;
    }

    public int getSeed() {
        return seed;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }
}
