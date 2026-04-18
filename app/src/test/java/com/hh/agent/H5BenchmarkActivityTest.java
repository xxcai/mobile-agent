package com.hh.agent;

import android.app.Application;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import com.hh.agent.h5bench.H5BenchmarkManifestRepository;
import com.hh.agent.h5bench.H5BenchmarkRunState;
import com.hh.agent.h5bench.H5BenchmarkManifest;
import com.hh.agent.h5bench.MiniWoBComparisonSummary;
import com.hh.agent.h5bench.MiniWoBRunOrchestrator;
import com.hh.agent.h5bench.MiniWoBRunRecord;
import com.hh.agent.h5bench.MiniWoBScoreAggregator;
import com.hh.agent.h5bench.MiniWoBTaskDefinition;
import com.hh.agent.h5bench.MiniWoBTaskResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = H5BenchmarkActivityTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class H5BenchmarkActivityTest {
    private static final String SUITE_ID = "miniwob-v0-baseline-20";

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
    public void mainActivity_doesNotRenderLegacyTopBarBenchmarkButton() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        int buttonId = activity.getResources().getIdentifier(
                "openH5BenchmarkButton", "id", activity.getPackageName());
        
        // Resource ID should not exist since button was removed from layout
        assertEquals(0, buttonId);
    }

    @Test
    public void benchmarkHost_startsRunAndMovesStateToRunning() {
        TestApplication.executor = command -> { };
        TestApplication.nextRunRecords = Collections.emptyList();

        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        assertEquals("idle", activity.getBenchmarkHost().getState().name().toLowerCase());
        activity.getBenchmarkHost().start();
        assertEquals("starting", activity.getBenchmarkHost().getState().name().toLowerCase());
    }

    @Test
    public void currentSuiteUsesManifestDisplayName() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        String text = ((TextView) activity.findViewById(R.id.currentSuiteView)).getText().toString();
        assertTrue(text.contains("H5基准测试"));
        assertTrue(text.contains(SUITE_ID));
    }

    @Test
    public void publishedBenchmarkStatusStartsWithManifestDrivenSuiteMetadata() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        String statusJson = activity.getPublishedBenchmarkStatusJson();

        assertTrue(statusJson.contains("\"suiteId\": \"" + SUITE_ID + "\""));
        assertTrue(statusJson.contains("\"suiteAssetPath\": \"" + H5BenchmarkManifestRepository.BASELINE_20_ASSET_PATH + "\""));
        assertTrue(statusJson.contains("\"state\": \"" + H5BenchmarkRunState.IDLE.name() + "\""));
    }

    @Test
    public void benchmarkHost_rejectsDuplicateStartWhileRunning() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();
        activity.getBenchmarkHost().markRunning();

        assertEquals(false, activity.getBenchmarkHost().start());
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
    @Config(sdk = 34, application = Application.class)
    public void startButtonPreviewUsesManifestDisplayNameInTitle() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();
        replaceManifest(activity, "自定义基准测试", Collections.singletonList(
                new MiniWoBTaskDefinition(
                        "click-test-2",
                        "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                        "Click the highlighted area.",
                        "click",
                        1,
                        1,
                        10000)));

        activity.getBenchmarkHost().start();

        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("自定义基准测试 · click-test-2",
                intent.getStringExtra(BusinessWebActivity.EXTRA_TITLE));
        assertEquals("completed", activity.getBenchmarkHost().getState().name().toLowerCase());
    }

    @Test
    @Config(sdk = 34, application = Application.class)
    public void startButtonPreviewDoesNotCompleteWhenManifestHasNoTasks() {
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();
        replaceManifest(activity, "自定义基准测试", Collections.emptyList());

        activity.getBenchmarkHost().start();

        assertEquals("failed", activity.getBenchmarkHost().getState().name().toLowerCase());
        assertTrue(((TextView) activity.findViewById(R.id.summaryJsonView)).getText().toString().contains("\"status\": \"empty\""));
    }

    @Test
    @Config(sdk = 34, application = Application.class)
    public void startButtonPreviewDoesNotCompleteWhenPreviewLaunchFails() {
        FailingPreviewActivity activity = Robolectric.buildActivity(FailingPreviewActivity.class).setup().get();
        replaceManifest(activity, "自定义基准测试", Collections.singletonList(
                new MiniWoBTaskDefinition(
                        "click-test-2",
                        "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                        "Click the highlighted area.",
                        "click",
                        1,
                        1,
                        10000)));

        activity.getBenchmarkHost().start();

        assertEquals("failed", activity.getBenchmarkHost().getState().name().toLowerCase());
        assertTrue(((TextView) activity.findViewById(R.id.summaryJsonView)).getText().toString().contains("\"status\": \"error\""));
    }

    public static class FailingPreviewActivity extends H5BenchmarkActivity {
        @Override
        public void startActivity(Intent intent) {
            throw new IllegalStateException("preview launch failed");
        }
    }

    private void replaceManifest(H5BenchmarkActivity activity, String displayName, List<MiniWoBTaskDefinition> tasks) {
        H5BenchmarkManifest manifest = new H5BenchmarkManifest();
        setField(manifest, "suiteId", SUITE_ID);
        setField(manifest, "displayName", displayName);
        setField(manifest, "tasks", tasks);
        setField(activity, "manifest", manifest);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private java.lang.reflect.Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private MiniWoBRunRecord buildRunRecord(String runId, String model, MiniWoBTaskResult... results) {
        MiniWoBScoreAggregator aggregator = new MiniWoBScoreAggregator();
        return new MiniWoBRunRecord(
                runId,
                model,
                "openai",
                "prompt-v1",
                SUITE_ID,
                "seed-set-v1",
                15,
                30000,
                "1.0.0",
                Arrays.asList(results),
                aggregator.summarize(SUITE_ID, runId, model, "openai", "prompt-v1", Arrays.asList(results)));
    }
}
