package com.hh.agent.android.viewcontext;

import androidx.annotation.Nullable;

import com.hh.agent.android.log.AgentLogs;

/**
 * Singleton holder for an optional screenshot analyzer implementation.
 */
public final class ScreenSnapshotAnalyzerHolder {

    private static final String TAG = "ScreenSnapshotAnalyzerHolder";

    private static volatile ScreenSnapshotAnalyzerHolder instance;

    @Nullable
    private ScreenSnapshotAnalyzer analyzer;

    private ScreenSnapshotAnalyzerHolder() {
    }

    public static ScreenSnapshotAnalyzerHolder getInstance() {
        if (instance == null) {
            synchronized (ScreenSnapshotAnalyzerHolder.class) {
                if (instance == null) {
                    instance = new ScreenSnapshotAnalyzerHolder();
                }
            }
        }
        return instance;
    }

    public synchronized void setAnalyzer(@Nullable ScreenSnapshotAnalyzer analyzer) {
        ScreenSnapshotAnalyzer previous = this.analyzer;
        this.analyzer = analyzer;
        if (previous != null && previous != analyzer && previous instanceof AutoCloseable) {
            try {
                ((AutoCloseable) previous).close();
            } catch (Exception closeError) {
                AgentLogs.warn(TAG, "analyzer_close_failed", "message=" + closeError.getMessage());
            }
        }
        String analyzerName = analyzer != null ? analyzer.getClass().getSimpleName() : "null";
        AgentLogs.info(TAG, "analyzer_set", "analyzer_name=" + analyzerName);
    }

    @Nullable
    public synchronized ScreenSnapshotAnalyzer getAnalyzer() {
        return analyzer;
    }
}
