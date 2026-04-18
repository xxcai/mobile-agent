package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.h5bench.H5BenchmarkHost;
import com.hh.agent.h5bench.H5BenchmarkManifest;
import com.hh.agent.h5bench.H5BenchmarkManifestRepository;
import com.hh.agent.h5bench.H5BenchmarkRunState;
import com.hh.agent.h5bench.MiniWoBComparisonSummary;
import com.hh.agent.h5bench.MiniWoBRunOrchestrator;
import com.hh.agent.h5bench.MiniWoBRunRecord;
import com.hh.agent.h5bench.MiniWoBTaskDefinition;
import com.hh.agent.h5bench.MiniWoBSuiteSummary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class H5BenchmarkActivity extends AppCompatActivity {
    private TextView currentSuiteView;
    private TextView runSelectorView;
    private TextView summaryJsonView;
    private TextView modelComparisonView;
    private TextView categoryComparisonView;
    private TextView taskDiffView;

    private H5BenchmarkManifest manifest;
    private H5BenchmarkHost benchmarkHost;
    private String publishedBenchmarkStatusJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_h5_benchmark);

        manifest = loadManifest();
        benchmarkHost = new H5BenchmarkHost(this::startBenchmarkRun);

        Button startBenchmarkButton = findViewById(R.id.startH5BenchmarkButton);
        currentSuiteView = findViewById(R.id.currentSuiteView);
        runSelectorView = findViewById(R.id.runSelectorView);
        summaryJsonView = findViewById(R.id.summaryJsonView);
        modelComparisonView = findViewById(R.id.modelComparisonView);
        categoryComparisonView = findViewById(R.id.categoryComparisonView);
        taskDiffView = findViewById(R.id.taskDiffView);

        currentSuiteView.setText("当前 Suite: " + getSuiteDisplayName() + " (" + manifest.getSuiteId() + ")");
        runSelectorView.setText("Run 选择: 还没有历史 run，开始后会在这里展示。");
        renderSummary(null);
        renderComparisonRows(null);
        renderCategoryComparison(null);
        renderTaskDiffs(null);

        startBenchmarkButton.setOnClickListener(v -> benchmarkHost.start());
    }

    public H5BenchmarkHost getBenchmarkHost() {
        return benchmarkHost;
    }

    private H5BenchmarkManifest loadManifest() {
        try {
            return new H5BenchmarkManifestRepository(this).loadBaseline20();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load H5 benchmark manifest", exception);
        }
    }

    private void startBenchmarkRun(H5BenchmarkHost host) {
        publishBenchmarkStatus(host.getState(), null, null);
        MiniWoBRunOrchestrator orchestrator = resolveRunOrchestrator();
        if (orchestrator == null) {
            if (launchFirstTaskPreview()) {
                host.markCompleted();
                publishBenchmarkStatus(host.getState(), null, null);
            } else {
                host.markFailed();
            }
            return;
        }
        resolveRunExecutor().execute(() -> {
            host.markRunning();
            runOnUiThread(() -> publishBenchmarkStatus(host.getState(), null, null));
            try {
                List<MiniWoBRunRecord> runRecords = orchestrator.runBenchmarks();
                runOnUiThread(() -> {
                    host.markCompleted();
                    renderRunRecords(runRecords);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    host.markFailed();
                    publishBenchmarkStatus(host.getState(), null, exception.getMessage());
                });
            }
        });
    }

    private MiniWoBRunOrchestrator resolveRunOrchestrator() {
        if (getApplication() instanceof MiniWoBRunOrchestrator.Provider) {
            return ((MiniWoBRunOrchestrator.Provider) getApplication()).getMiniWoBRunOrchestrator();
        }
        return null;
    }

    private Executor resolveRunExecutor() {
        if (getApplication() instanceof MiniWoBRunOrchestrator.ExecutorProvider) {
            return ((MiniWoBRunOrchestrator.ExecutorProvider) getApplication()).getMiniWoBRunExecutor();
        }
        return Runnable::run;
    }

    void renderSummary(MiniWoBSuiteSummary summary) {
        publishBenchmarkStatus(benchmarkHost.getState(), summary, null);
    }

    void renderComparisonRows(List<MiniWoBComparisonSummary.ModelSummary> modelSummaries) {
        if (modelSummaries == null || modelSummaries.isEmpty()) {
            modelComparisonView.setText("模型对比: 暂无数据");
            return;
        }
        StringBuilder builder = new StringBuilder("模型对比:\n");
        for (MiniWoBComparisonSummary.ModelSummary summary : modelSummaries) {
            builder.append(summary.getModel())
                    .append(" | successRate=").append(summary.getSuccessRate())
                    .append(" | avgSuccessSteps=").append(summary.getAvgSuccessSteps())
                    .append(" | avgSuccessLatencyMs=").append(summary.getAvgSuccessLatencyMs())
                    .append(" | timeoutRate=").append(summary.getTimeoutRate())
                    .append('\n');
        }
        modelComparisonView.setText(builder.toString().trim());
    }

    void renderTaskDiffs(List<MiniWoBComparisonSummary.TaskDiff> taskDiffs) {
        if (taskDiffs == null || taskDiffs.isEmpty()) {
            taskDiffView.setText("任务 Diff: 暂无数据");
            return;
        }
        StringBuilder builder = new StringBuilder("任务 Diff:\n");
        for (MiniWoBComparisonSummary.TaskDiff diff : taskDiffs) {
            builder.append(diff.getTaskId())
                    .append(" | category=").append(diff.getCategory())
                    .append(" | success=").append(diff.getSuccessByModel())
                    .append('\n');
        }
        taskDiffView.setText(builder.toString().trim());
    }

    void renderRunRecords(List<MiniWoBRunRecord> runRecords) {
        if (runRecords == null || runRecords.isEmpty()) {
            runSelectorView.setText("Run 选择: 暂无数据");
            renderSummary(null);
            renderComparisonRows(null);
            renderCategoryComparison(null);
            renderTaskDiffs(null);
            return;
        }

        StringBuilder runSelectorBuilder = new StringBuilder("Run 选择:\n");
        for (MiniWoBRunRecord runRecord : runRecords) {
            runSelectorBuilder.append(runRecord.getRunId())
                    .append(" | model=").append(runRecord.getModel())
                    .append('\n');
        }
        runSelectorView.setText(runSelectorBuilder.toString().trim());

        MiniWoBRunRecord latestRun = runRecords.get(runRecords.size() - 1);
        renderSummary(latestRun.getSummary());

        MiniWoBComparisonSummary comparisonSummary = MiniWoBComparisonSummary.compareByModel(
                manifest.getSuiteId(), runRecords);
        renderComparisonRows(comparisonSummary.getModelSummaries());
        renderCategoryComparison(runRecords);
        renderTaskDiffs(comparisonSummary.getTaskDiffs());
    }

    void renderCategoryComparison(List<MiniWoBRunRecord> runRecords) {
        if (runRecords == null || runRecords.isEmpty()) {
            categoryComparisonView.setText("分类对比: 暂无数据");
            return;
        }

        Set<String> categories = new LinkedHashSet<>();
        for (MiniWoBRunRecord runRecord : runRecords) {
            categories.addAll(runRecord.getSummary().getCategoryBreakdown().keySet());
        }

        StringBuilder builder = new StringBuilder("分类对比:\n");
        for (String category : categories) {
            builder.append(category).append(": ");
            List<String> modelRates = new ArrayList<>();
            for (MiniWoBRunRecord runRecord : runRecords) {
                Map<String, MiniWoBSuiteSummary.CategoryBreakdown> breakdown = runRecord.getSummary().getCategoryBreakdown();
                MiniWoBSuiteSummary.CategoryBreakdown categoryBreakdown = breakdown.get(category);
                if (categoryBreakdown != null) {
                    modelRates.add(runRecord.getModel() + "=" + categoryBreakdown.getSuccessRate());
                }
            }
            builder.append(modelRates).append('\n');
        }
        categoryComparisonView.setText(builder.toString().trim());
    }

    private boolean launchFirstTaskPreview() {
        try {
            List<MiniWoBTaskDefinition> tasks = manifest.getTasks();
            if (tasks.isEmpty()) {
                publishBenchmarkStatus(H5BenchmarkRunState.FAILED, null, "empty", null);
                return false;
            }
            MiniWoBTaskDefinition firstTask = tasks.get(0);
            Intent intent = new Intent(this, BusinessWebActivity.class);
            intent.putExtra(BusinessWebActivity.EXTRA_TITLE, getSuiteDisplayName() + " · " + firstTask.getTaskId());
            intent.putExtra(BusinessWebActivity.EXTRA_BENCHMARK_MODE_ENABLED, true);
            intent.putExtra(BusinessWebActivity.EXTRA_BENCHMARK_ASSET_PATH, firstTask.getAssetPath());
            startActivity(intent);
            return true;
        } catch (Exception exception) {
            publishBenchmarkStatus(H5BenchmarkRunState.FAILED, null, "error", exception.getMessage());
            return false;
        }
    }

    public String getPublishedBenchmarkStatusJson() {
        return publishedBenchmarkStatusJson;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private void publishBenchmarkStatus(
            H5BenchmarkRunState state,
            MiniWoBSuiteSummary summary,
            String message) {
        publishBenchmarkStatus(state, summary, null, message);
    }

    private void publishBenchmarkStatus(
            H5BenchmarkRunState state,
            MiniWoBSuiteSummary summary,
            String status,
            String message) {
        String resolvedStatus = status == null || status.trim().isEmpty()
                ? state.name().toLowerCase()
                : status;
        StringBuilder builder = new StringBuilder("{\n")
                .append("  \"suiteId\": \"").append(safe(manifest.getSuiteId())).append("\",\n")
                .append("  \"suiteAssetPath\": \"").append(safe(resolveSuiteAssetPath())).append("\",\n")
                .append("  \"state\": \"").append(state.name()).append("\",\n")
                .append("  \"status\": \"").append(safe(resolvedStatus)).append("\"");
        if (summary != null) {
            builder.append(",\n")
                    .append("  \"runId\": \"").append(safe(summary.getRunId())).append("\",\n")
                    .append("  \"model\": \"").append(safe(summary.getModel())).append("\",\n")
                    .append("  \"provider\": \"").append(safe(summary.getProvider())).append("\",\n")
                    .append("  \"promptVersion\": \"").append(safe(summary.getPromptVersion())).append("\",\n")
                    .append("  \"successRate\": ").append(summary.getSuccessRate()).append(",\n")
                    .append("  \"avgSuccessSteps\": ").append(summary.getAvgSuccessSteps()).append(",\n")
                    .append("  \"avgSuccessLatencyMs\": ").append(summary.getAvgSuccessLatencyMs()).append(",\n")
                    .append("  \"timeoutRate\": ").append(summary.getTimeoutRate());
        }
        if (message != null && !message.trim().isEmpty()) {
            builder.append(",\n")
                    .append("  \"message\": \"").append(safe(message)).append("\"");
        }
        builder.append("\n}");
        publishedBenchmarkStatusJson = builder.toString();
        summaryJsonView.setText(publishedBenchmarkStatusJson);
    }

    private String getSuiteDisplayName() {
        String displayName = manifest.getDisplayName();
        return displayName == null || displayName.trim().isEmpty()
                ? manifest.getSuiteId()
                : displayName;
    }

    private String resolveSuiteAssetPath() {
        String taskListAssetPath = manifest.getTaskListAssetPath();
        return taskListAssetPath == null || taskListAssetPath.trim().isEmpty()
                ? H5BenchmarkManifestRepository.BASELINE_20_ASSET_PATH
                : taskListAssetPath;
    }
}
