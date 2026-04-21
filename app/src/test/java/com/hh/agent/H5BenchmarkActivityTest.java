package com.hh.agent;

import android.app.Application;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import com.hh.agent.h5bench.MiniWoBComparisonSummary;
import com.hh.agent.h5bench.H5BenchmarkRunState;
import com.hh.agent.h5bench.MiniWoBRunOrchestrator;
import com.hh.agent.h5bench.MiniWoBRunRecord;
import com.hh.agent.h5bench.MiniWoBScoreAggregator;
import com.hh.agent.h5bench.MiniWoBTaskResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = H5BenchmarkActivityTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class H5BenchmarkActivityTest {
    public static final class TestApplication extends Application implements MiniWoBRunOrchestrator.Provider, MiniWoBRunOrchestrator.ExecutorProvider {
        static List<MiniWoBRunRecord> nextRunRecords;
        static Executor executor = Runnable::run;

        @Override
        public MiniWoBRunOrchestrator getMiniWoBRunOrchestrator() {
            return () -> nextRunRecords;
        }

        @Override
        public Executor getMiniWoBRunExecutor() {
            return executor;
        }
    }

    @Test
    public void mainActivityDoesNotRenderLegacyTopBarBenchmarkButton() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertEquals(0, activity.getResources().getIdentifier(
                "openH5BenchmarkButton", "id", activity.getPackageName()));
    }

    @Test
    public void rendersModelComparisonRowsWhenMultipleRunsExist() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        activity.renderComparisonRows(Arrays.asList(
                new MiniWoBComparisonSummary.ModelSummary("gpt-4.1", 42.0, 6.8, 4900, 18.0, 0.5),
                new MiniWoBComparisonSummary.ModelSummary("claude-sonnet-4.5", 51.0, 6.1, 5300, 12.0, 0.7)
        ));

        TextView view = activity.findViewById(R.id.modelComparisonView);
        assertTrue(view.getText().toString().contains("gpt-4.1"));
        assertTrue(view.getText().toString().contains("claude-sonnet-4.5"));
    }

    @Test
    public void renderRunRecordsUpdatesSummarySelectorAndTaskDiffs() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        MiniWoBRunRecord runA = buildRunRecord(
                "run-a",
                "gpt-4.1",
                MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200),
                MiniWoBTaskResult.failure("scroll-text", "scroll", 0.0, 15, 30000, "timeout"));
        MiniWoBRunRecord runB = buildRunRecord(
                "run-b",
                "claude-sonnet-4.5",
                MiniWoBTaskResult.success("click-test-2", "click", 1.0, 3, 2800),
                MiniWoBTaskResult.success("scroll-text", "scroll", 1.0, 8, 9000));

        activity.renderRunRecords(Arrays.asList(runA, runB));

        assertTrue(((TextView) activity.findViewById(R.id.runSelectorView)).getText().toString().contains("run-a"));
        assertTrue(((TextView) activity.findViewById(R.id.runSelectorView)).getText().toString().contains("run-b"));
        assertTrue(((TextView) activity.findViewById(R.id.summaryJsonView)).getText().toString().contains("\"runId\": \"run-b\""));
        assertTrue(((TextView) activity.findViewById(R.id.modelComparisonView)).getText().toString().contains("claude-sonnet-4.5"));
        assertTrue(((TextView) activity.findViewById(R.id.categoryComparisonView)).getText().toString().contains("click"));
        assertTrue(((TextView) activity.findViewById(R.id.categoryComparisonView)).getText().toString().contains("scroll"));
        assertTrue(((TextView) activity.findViewById(R.id.taskDiffView)).getText().toString().contains("scroll-text"));
    }

    @Test
    public void startButtonUsesInjectedRunOrchestratorWhenAvailable() {
        TestApplication.executor = Runnable::run;
        TestApplication.nextRunRecords = Arrays.asList(
                buildRunRecord(
                        "run-a",
                        "gpt-4.1",
                        MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200),
                        MiniWoBTaskResult.failure("scroll-text", "scroll", 0.0, 15, 30000, "timeout")),
                buildRunRecord(
                        "run-b",
                        "claude-sonnet-4.5",
                        MiniWoBTaskResult.success("click-test-2", "click", 1.0, 3, 2800),
                        MiniWoBTaskResult.success("scroll-text", "scroll", 1.0, 8, 9000))
        );
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        ((Button) activity.findViewById(R.id.startH5BenchmarkButton)).performClick();

        assertTrue(((TextView) activity.findViewById(R.id.summaryJsonView)).getText().toString().contains("\"runId\": \"run-b\""));
        assertTrue(((TextView) activity.findViewById(R.id.modelComparisonView)).getText().toString().contains("claude-sonnet-4.5"));
    }

    @Test
    public void startButtonUsesInjectedExecutorWhenAvailable() {
        final boolean[] executed = {false};
        TestApplication.executor = command -> {
            executed[0] = true;
            command.run();
        };
        TestApplication.nextRunRecords = Collections.singletonList(
                buildRunRecord(
                        "run-a",
                        "gpt-4.1",
                        MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200))
        );
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        ((Button) activity.findViewById(R.id.startH5BenchmarkButton)).performClick();

        assertTrue(executed[0]);
        assertTrue(((TextView) activity.findViewById(R.id.summaryJsonView)).getText().toString().contains("\"runId\": \"run-a\""));
    }

    @Test
    public void suiteAssetPathUsesWorkspaceSkillManifest() throws Exception {
        Field field = H5BenchmarkActivity.class.getDeclaredField("SUITE_ASSET_PATH");
        field.setAccessible(true);

        assertEquals(
                "workspace/skills/h5_benchmark_runner/baseline-20.json",
                field.get(null));
    }

    @Test
    public void createsBenchmarkHostWithIdleStateOnCreate() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        assertEquals(H5BenchmarkRunState.IDLE, activity.getBenchmarkHost().getState());
    }

    private MiniWoBRunRecord buildRunRecord(String runId, String model, MiniWoBTaskResult... results) {
        MiniWoBScoreAggregator aggregator = new MiniWoBScoreAggregator();
        return new MiniWoBRunRecord(
                runId,
                model,
                "openai",
                "prompt-v1",
                H5BenchmarkActivity.SUITE_ID,
                "seed-set-v1",
                15,
                30000,
                "1.0.0",
                Arrays.asList(results),
                aggregator.summarize(H5BenchmarkActivity.SUITE_ID, runId, model, "openai", "prompt-v1", Arrays.asList(results)));
    }
}
