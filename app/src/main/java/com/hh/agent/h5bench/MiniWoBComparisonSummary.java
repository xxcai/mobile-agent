package com.hh.agent.h5bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MiniWoBComparisonSummary {
    private final String suiteId;
    private final String comparisonDimension;
    private final List<ModelSummary> modelSummaries;
    private final List<TaskDiff> taskDiffs;

    public MiniWoBComparisonSummary(String suiteId, String comparisonDimension, List<ModelSummary> modelSummaries, List<TaskDiff> taskDiffs) {
        this.suiteId = Objects.requireNonNull(suiteId, "suiteId");
        this.comparisonDimension = Objects.requireNonNull(comparisonDimension, "comparisonDimension");
        this.modelSummaries = Collections.unmodifiableList(new ArrayList<>(modelSummaries));
        this.taskDiffs = Collections.unmodifiableList(new ArrayList<>(taskDiffs));
    }

    public static MiniWoBComparisonSummary compareByModel(String suiteId, List<MiniWoBRunRecord> runRecords) {
        List<ModelSummary> models = new ArrayList<>();
        Map<String, TaskDiffBuilder> taskDiffs = new LinkedHashMap<>();

        for (MiniWoBRunRecord runRecord : runRecords) {
            MiniWoBSuiteSummary summary = runRecord.getSummary();
            models.add(new ModelSummary(
                    runRecord.getModel(),
                    summary.getSuccessRate(),
                    summary.getAvgSuccessSteps(),
                    summary.getAvgSuccessLatencyMs(),
                    summary.getTimeoutRate(),
                    summary.getAvgReward()));

            for (MiniWoBTaskResult result : runRecord.getResults()) {
                TaskDiffBuilder builder = taskDiffs.computeIfAbsent(
                        result.getTaskId(),
                        ignored -> new TaskDiffBuilder(result.getTaskId(), result.getCategory()));
                builder.add(runRecord.getModel(), result.isSuccess(), result.getReward());
            }
        }

        List<TaskDiff> diffs = new ArrayList<>();
        for (TaskDiffBuilder builder : taskDiffs.values()) {
            diffs.add(builder.build());
        }

        return new MiniWoBComparisonSummary(suiteId, "model", models, diffs);
    }

    public String getSuiteId() {
        return suiteId;
    }

    public String getComparisonDimension() {
        return comparisonDimension;
    }

    public List<ModelSummary> getModelSummaries() {
        return modelSummaries;
    }

    public List<TaskDiff> getTaskDiffs() {
        return taskDiffs;
    }

    public static class ModelSummary {
        private final String model;
        private final double successRate;
        private final double avgSuccessSteps;
        private final double avgSuccessLatencyMs;
        private final double timeoutRate;
        private final double avgReward;

        public ModelSummary(String model, double successRate, double avgSuccessSteps, double avgSuccessLatencyMs, double timeoutRate, double avgReward) {
            this.model = model;
            this.successRate = successRate;
            this.avgSuccessSteps = avgSuccessSteps;
            this.avgSuccessLatencyMs = avgSuccessLatencyMs;
            this.timeoutRate = timeoutRate;
            this.avgReward = avgReward;
        }

        public String getModel() {
            return model;
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
    }

    public static class TaskDiff {
        private final String taskId;
        private final String category;
        private final Map<String, Boolean> successByModel;
        private final Map<String, Double> rewardByModel;

        public TaskDiff(String taskId, String category, Map<String, Boolean> successByModel, Map<String, Double> rewardByModel) {
            this.taskId = taskId;
            this.category = category;
            this.successByModel = Collections.unmodifiableMap(new LinkedHashMap<>(successByModel));
            this.rewardByModel = Collections.unmodifiableMap(new LinkedHashMap<>(rewardByModel));
        }

        public String getTaskId() {
            return taskId;
        }

        public String getCategory() {
            return category;
        }

        public Map<String, Boolean> getSuccessByModel() {
            return successByModel;
        }

        public Map<String, Double> getRewardByModel() {
            return rewardByModel;
        }
    }

    private static class TaskDiffBuilder {
        private final String taskId;
        private final String category;
        private final Map<String, Boolean> successByModel = new LinkedHashMap<>();
        private final Map<String, Double> rewardByModel = new LinkedHashMap<>();

        private TaskDiffBuilder(String taskId, String category) {
            this.taskId = taskId;
            this.category = category;
        }

        private void add(String model, boolean success, double reward) {
            successByModel.put(model, success);
            rewardByModel.put(model, reward);
        }

        private TaskDiff build() {
            return new TaskDiff(taskId, category, successByModel, rewardByModel);
        }
    }
}
