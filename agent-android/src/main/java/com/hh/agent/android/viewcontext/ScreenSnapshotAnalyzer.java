package com.hh.agent.android.viewcontext;

import android.app.Activity;

import androidx.annotation.Nullable;

/**
 * Optional analyzer used to turn a foreground activity screenshot into structured view context.
 */
public interface ScreenSnapshotAnalyzer {

    ScreenSnapshotAnalysis analyze(Activity activity, @Nullable String targetHint) throws Exception;
}
