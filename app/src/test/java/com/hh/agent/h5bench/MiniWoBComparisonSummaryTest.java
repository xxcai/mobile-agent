package com.hh.agent.h5bench;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiniWoBComparisonSummaryTest {
    @Test
    public void buildsModelLevelAndTaskLevelDiffs() {
        List<MiniWoBTaskResult> modelAResults = Arrays.asList(
                MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200),
                MiniWoBTaskResult.failure("scroll-text", "scroll", 0.0, 15, 30000, "timeout"));
        List<MiniWoBTaskResult> modelBResults = Arrays.asList(
                MiniWoBTaskResult.success("click-test-2", "click", 1.0, 3, 2800),
                MiniWoBTaskResult.success("scroll-text", "scroll", 1.0, 8, 9000));

        MiniWoBRunRecord modelARun = new MiniWoBRunRecord(
                "run-a",
                "gpt-4.1",
                "openai",
                "prompt-v1",
                "miniwob-v0-baseline-20",
                "seed-set-v1",
                15,
                30000,
                "1.0.0",
                modelAResults,
                new MiniWoBScoreAggregator().summarize("miniwob-v0-baseline-20", "run-a", "gpt-4.1", "openai", "prompt-v1", modelAResults));
        MiniWoBRunRecord modelBRun = new MiniWoBRunRecord(
                "run-b",
                "gpt-5.4",
                "openai",
                "prompt-v1",
                "miniwob-v0-baseline-20",
                "seed-set-v1",
                15,
                30000,
                "1.0.0",
                modelBResults,
                new MiniWoBScoreAggregator().summarize("miniwob-v0-baseline-20", "run-b", "gpt-5.4", "openai", "prompt-v1", modelBResults));

        MiniWoBComparisonSummary summary = MiniWoBComparisonSummary.compareByModel(
                "miniwob-v0-baseline-20",
                Arrays.asList(modelARun, modelBRun));

        assertEquals("miniwob-v0-baseline-20", summary.getSuiteId());
        assertEquals("model", summary.getComparisonDimension());
        assertEquals(2, summary.getModelSummaries().size());
        assertEquals("gpt-4.1", summary.getModelSummaries().get(0).getModel());
        assertEquals("gpt-5.4", summary.getModelSummaries().get(1).getModel());
        assertEquals(2, summary.getTaskDiffs().size());

        MiniWoBComparisonSummary.TaskDiff scrollDiff = summary.getTaskDiffs().get(1);
        assertEquals("scroll-text", scrollDiff.getTaskId());
        assertFalse(scrollDiff.getSuccessByModel().get("gpt-4.1"));
        assertTrue(scrollDiff.getSuccessByModel().get("gpt-5.4"));
        assertEquals(0.0, scrollDiff.getRewardByModel().get("gpt-4.1"), 0.01);
        assertEquals(1.0, scrollDiff.getRewardByModel().get("gpt-5.4"), 0.01);
    }
}
