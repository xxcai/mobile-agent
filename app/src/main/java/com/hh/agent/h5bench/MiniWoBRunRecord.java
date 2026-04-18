package com.hh.agent.h5bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MiniWoBRunRecord {
    private final String runId;
    private final String model;
    private final String provider;
    private final String promptVersion;
    private final String suiteId;
    private final String seedSetVersion;
    private final int maxSteps;
    private final long timeoutMs;
    private final String appVersion;
    private final List<MiniWoBTaskResult> results;
    private final MiniWoBSuiteSummary summary;

    public MiniWoBRunRecord(
            String runId,
            String model,
            String provider,
            String promptVersion,
            String suiteId,
            String seedSetVersion,
            int maxSteps,
            long timeoutMs,
            String appVersion,
            List<MiniWoBTaskResult> results,
            MiniWoBSuiteSummary summary) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.model = Objects.requireNonNull(model, "model");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion");
        this.suiteId = Objects.requireNonNull(suiteId, "suiteId");
        this.seedSetVersion = Objects.requireNonNull(seedSetVersion, "seedSetVersion");
        this.maxSteps = maxSteps;
        this.timeoutMs = timeoutMs;
        this.appVersion = Objects.requireNonNull(appVersion, "appVersion");
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    public String getRunId() {
        return runId;
    }

    public String getModel() {
        return model;
    }

    public String getProvider() {
        return provider;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getSuiteId() {
        return suiteId;
    }

    public String getSeedSetVersion() {
        return seedSetVersion;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public List<MiniWoBTaskResult> getResults() {
        return results;
    }

    public MiniWoBSuiteSummary getSummary() {
        return summary;
    }
}
