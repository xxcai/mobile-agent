package com.hh.agent.screenvision;

import android.content.Context;
import android.os.Build;

import com.hh.agent.android.AgentInitializer;
import com.hh.agent.android.log.AgentLogs;

/**
 * Installs the screen-vision based screenshot analyzer into agent-android.
 */
public final class AgentScreenVision {

    private static final String TAG = "AgentScreenVision";

    private AgentScreenVision() {
    }

    public static void install(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        AgentLogs.info(TAG, "install_start", "sdk_int=" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            AgentInitializer.setScreenSnapshotAnalyzer(null);
            AgentLogs.warn(TAG, "install_skipped", "reason=sdk_below_26");
            return;
        }
        AgentInitializer.setScreenSnapshotAnalyzer(
                new ScreenVisionSnapshotAnalyzer(context.getApplicationContext())
        );
        AgentLogs.info(TAG, "install_complete", "analyzer=ScreenVisionSnapshotAnalyzer");
    }

    public static void uninstall() {
        AgentInitializer.setScreenSnapshotAnalyzer(null);
        AgentLogs.info(TAG, "uninstall_complete", null);
    }
}
