package com.hh.agent.screenvision;

import android.content.Context;
import android.os.Build;

import com.hh.agent.android.AgentInitializer;

/**
 * Installs the screen-vision based screenshot analyzer into agent-android.
 */
public final class AgentScreenVision {

    private AgentScreenVision() {
    }

    public static void install(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            AgentInitializer.setScreenSnapshotAnalyzer(null);
            return;
        }
        AgentInitializer.setScreenSnapshotAnalyzer(
                new ScreenVisionSnapshotAnalyzer(context.getApplicationContext())
        );
    }

    public static void uninstall() {
        AgentInitializer.setScreenSnapshotAnalyzer(null);
    }
}