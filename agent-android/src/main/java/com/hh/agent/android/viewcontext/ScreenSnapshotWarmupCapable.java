package com.hh.agent.android.viewcontext;

/**
 * Optional marker for analyzers that can warm their runtime resources ahead of the first screenshot.
 */
public interface ScreenSnapshotWarmupCapable {
    void prewarmAsync();
}

