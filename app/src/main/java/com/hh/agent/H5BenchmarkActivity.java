package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.h5bench.MiniWoBComparisonSummary;
import com.hh.agent.h5bench.H5BenchmarkHost;
import com.hh.agent.h5bench.H5BenchmarkManifest;
import com.hh.agent.h5bench.H5BenchmarkManifestRepository;
import com.hh.agent.h5bench.H5BenchmarkRunState;
import com.hh.agent.h5bench.MiniWoBRunOrchestrator;
import com.hh.agent.h5bench.MiniWoBRunRecord;
import com.hh.agent.h5bench.MiniWoBTaskDefinition;
import com.hh.agent.h5bench.MiniWoBTaskRegistry;
import com.hh.agent.h5bench.MiniWoBSuiteSummary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class H5BenchmarkActivity extends AppCompatActivity {
    static final String SUITE_ID = "miniwob-v0-baseline-20";
    private static final String SUITE_ASSET_PATH = H5BenchmarkManifestRepository.BASELINE_20_ASSET_PATH;

    private TextView currentSuiteView;
    private TextView runSelectorView;
    private TextView summaryJsonView;
    private TextView modelComparisonView;
    private TextView categoryComparisonView;
    private TextView taskDiffView;
    private H5BenchmarkHost benchmarkHost;
    private H5BenchmarkManifest manifest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_h5_benchmark);

        Button startBenchmarkButton = findViewById(R.id.startH5BenchmarkButton);
        currentSuiteView = findViewById(R.id.currentSuiteView);
        runSelectorView = findViewById(R.id.runSelectorView);
        summaryJsonView = findViewById(R.id.summaryJsonView);
        modelComparisonView = findViewById(R.id.modelComparisonView);
        categoryComparisonView = findViewById(R.id.categoryComparisonView);
        taskDiffView = findViewById(R.id.taskDiffView);
        benchmarkHost = new H5BenchmarkHost(this::startBenchmarkFlow);
        manifest = loadManifest();

        currentSuiteView.setText("当前 Suite: " + manifest.getDisplayName());
        runSelectorView.setText("Run 选择: 还没有历史 run，开始后会在这里展示。");
        renderState(H5BenchmarkRunState.IDLE, null);
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
            summaryJsonView.setText("{\n  \"suiteId\": \"" + SUITE_ID + "\",\n  \"status\": \"failed\",\n  \"message\": \""
                    + safe(exception.getMessage()) + "\"\n}");
            return H5BenchmarkManifest.empty(SUITE_ID, "H5基准测试");
        }
    }

    private void startBenchmarkFlow(H5BenchmarkHost host) {
        runOnUiThread(() -> renderState(H5BenchmarkRunState.STARTING, null));
        MiniWoBRunOrchestrator orchestrator = resolveRunOrchestrator();
        if (orchestrator == null) {
            host.markRunning();
            runOnUiThread(() -> renderState(H5BenchmarkRunState.RUNNING, null));
            launchFirstTaskPreview();
            return;
        }
        resolveRunExecutor().execute(() -> {
            try {
                host.markRunning();
                runOnUiThread(() -> renderState(H5BenchmarkRunState.RUNNING, null));
                List<MiniWoBRunRecord> runRecords = orchestrator.runBenchmarks();
                host.markCompleted();
                runOnUiThread(() -> {
                    renderRunRecords(runRecords);
                    renderState(H5BenchmarkRunState.COMPLETED, null);
                });
            } catch (Exception exception) {
                host.markFailed();
                runOnUiThread(() -> renderState(H5BenchmarkRunState.FAILED, safe(exception.getMessage())));
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
        if (summary == null) {
            summaryJsonView.setText("{\n  \"suiteId\": \"" + SUITE_ID + "\",\n  \"status\": \"idle\"\n}");
            return;
        }
        summaryJsonView.setText("{\n"
                + "  \"runId\": \"" + safe(summary.getRunId()) + "\",\n"
                + "  \"model\": \"" + safe(summary.getModel()) + "\",\n"
                + "  \"provider\": \"" + safe(summary.getProvider()) + "\",\n"
                + "  \"promptVersion\": \"" + safe(summary.getPromptVersion()) + "\",\n"
                + "  \"suiteId\": \"" + summary.getSuiteId() + "\",\n"
                + "  \"successRate\": " + summary.getSuccessRate() + ",\n"
                + "  \"avgSuccessSteps\": " + summary.getAvgSuccessSteps() + ",\n"
                + "  \"avgSuccessLatencyMs\": " + summary.getAvgSuccessLatencyMs() + ",\n"
                + "  \"timeoutRate\": " + summary.getTimeoutRate() + "\n"
                + "}");
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

        MiniWoBComparisonSummary comparisonSummary = MiniWoBComparisonSummary.compareByModel(SUITE_ID, runRecords);
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

    private void renderState(H5BenchmarkRunState state, String message) {
        if (state == H5BenchmarkRunState.COMPLETED || state == null) {
            return;
        }
        StringBuilder builder = new StringBuilder("{\n")
                .append("  \"suiteId\": \"").append(SUITE_ID).append("\",\n")
                .append("  \"status\": \"").append(state.name().toLowerCase()).append("\"");
        if (message != null && !message.trim().isEmpty()) {
            builder.append(",\n  \"message\": \"").append(safe(message)).append("\"");
        }
        builder.append("\n}");
        summaryJsonView.setText(builder.toString());
    }

    private void launchFirstTaskPreview() {
        try {
            List<MiniWoBTaskDefinition> tasks = manifest.getTasks();
            if (tasks.isEmpty()) {
                tasks = new MiniWoBTaskRegistry(this).loadSuite(SUITE_ASSET_PATH);
            }
            if (tasks.isEmpty()) {
                summaryJsonView.setText("{\n  \"suiteId\": \"" + SUITE_ID + "\",\n  \"status\": \"empty\"\n}");
                return;
            }
            MiniWoBTaskDefinition firstTask = tasks.get(0);
            Intent intent = new Intent(this, BusinessWebActivity.class);
            intent.putExtra(BusinessWebActivity.EXTRA_TITLE, "H5基准测试 · " + firstTask.getTaskId());
            intent.putExtra(BusinessWebActivity.EXTRA_BENCHMARK_MODE_ENABLED, true);
            intent.putExtra(BusinessWebActivity.EXTRA_BENCHMARK_ASSET_PATH, firstTask.getAssetPath());
            startActivity(intent);
        } catch (Exception exception) {
            summaryJsonView.setText("{\n  \"suiteId\": \"" + SUITE_ID + "\",\n  \"status\": \"error\",\n  \"message\": \""
                    + safe(exception.getMessage()) + "\"\n}");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
