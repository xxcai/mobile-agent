package com.hh.agent.h5bench;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class MiniWoBScoreAggregatorTest {
    @Test
    public void computesSuccessRateAndSuccessOnlyAverages() {
        MiniWoBTaskResult successA = MiniWoBTaskResult.success("click-test-2", "click", 1.0, 4, 3200);
        MiniWoBTaskResult successB = MiniWoBTaskResult.success("enter-text", "input", 1.0, 6, 5100);
        MiniWoBTaskResult timeout = MiniWoBTaskResult.failure("scroll-text", "scroll", 0.0, 15, 30000, "timeout");

        MiniWoBSuiteSummary summary = new MiniWoBScoreAggregator().summarize(
                "miniwob-v0-baseline-20",
                Arrays.asList(successA, successB, timeout));

        assertEquals(66.67, summary.getSuccessRate(), 0.01);
        assertEquals(5.0, summary.getAvgSuccessSteps(), 0.01);
        assertEquals(4150.0, summary.getAvgSuccessLatencyMs(), 0.01);
        assertEquals(33.33, summary.getTimeoutRate(), 0.01);
        assertEquals(0.67, summary.getAvgReward(), 0.01);
        assertEquals(100.0, summary.getCategoryBreakdown().get("click").getSuccessRate(), 0.01);
        assertEquals(0.0, summary.getCategoryBreakdown().get("scroll").getSuccessRate(), 0.01);
    }
}
