package com.hh.agent.android.debug;

public final class BenchmarkTaskContext {

    private final String runId;
    private final String taskId;

    public BenchmarkTaskContext(String runId, String taskId) {
        this.runId = runId == null ? "" : runId.trim();
        this.taskId = taskId == null ? "" : taskId.trim();
    }

    public String getRunId() {
        return runId;
    }

    public String getTaskId() {
        return taskId;
    }
}
