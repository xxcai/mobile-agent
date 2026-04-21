package com.hh.agent.h5bench;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MiniWoBScoreAggregator {
    public MiniWoBSuiteSummary summarize(String suiteId, List<MiniWoBTaskResult> results) {
        return summarize(suiteId, null, null, null, null, results);
    }

    public MiniWoBSuiteSummary summarize(
            String suiteId,
            String runId,
            String model,
            String provider,
            String promptVersion,
            List<MiniWoBTaskResult> results) {
        int total = results.size();
        int successCount = 0;
        int timeoutCount = 0;
        double totalReward = 0.0;
        long successStepsTotal = 0L;
        long successLatencyTotal = 0L;
        Map<String, int[]> categoryCounts = new LinkedHashMap<>();

        for (MiniWoBTaskResult result : results) {
            int[] counts = categoryCounts.computeIfAbsent(result.getCategory(), ignored -> new int[2]);
            counts[0]++;
            totalReward += result.getReward();
            if (result.isSuccess()) {
                successCount++;
                counts[1]++;
                successStepsTotal += result.getSteps();
                successLatencyTotal += result.getLatencyMs();
            }
            if ("timeout".equals(result.getFinishReason())) {
                timeoutCount++;
            }
        }

        Map<String, MiniWoBSuiteSummary.CategoryBreakdown> breakdown = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : categoryCounts.entrySet()) {
            int totalCount = entry.getValue()[0];
            int categorySuccessCount = entry.getValue()[1];
            breakdown.put(entry.getKey(), new MiniWoBSuiteSummary.CategoryBreakdown(
                    totalCount,
                    categorySuccessCount,
                    percentage(categorySuccessCount, totalCount)));
        }

        double successRate = percentage(successCount, total);
        double avgSuccessSteps = successCount == 0 ? 0.0 : round2((double) successStepsTotal / successCount);
        double avgSuccessLatencyMs = successCount == 0 ? 0.0 : round2((double) successLatencyTotal / successCount);
        double timeoutRate = percentage(timeoutCount, total);
        double avgReward = total == 0 ? 0.0 : round2(totalReward / total);
        return new MiniWoBSuiteSummary(
                suiteId,
                runId,
                model,
                provider,
                promptVersion,
                successRate,
                avgSuccessSteps,
                avgSuccessLatencyMs,
                timeoutRate,
                avgReward,
                breakdown);
    }

    private static double percentage(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return round2((numerator * 100.0) / denominator);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
