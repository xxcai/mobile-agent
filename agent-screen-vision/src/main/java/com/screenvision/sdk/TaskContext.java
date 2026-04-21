package com.screenvision.sdk;

public final class TaskContext {
    private final String goalText;

    public TaskContext(String goalText) {
        this.goalText = goalText == null ? "" : goalText.trim();
    }

    public static TaskContext empty() {
        return new TaskContext("");
    }

    public String getGoalText() {
        return goalText;
    }

    public boolean hasGoalText() {
        return !goalText.isEmpty();
    }
}