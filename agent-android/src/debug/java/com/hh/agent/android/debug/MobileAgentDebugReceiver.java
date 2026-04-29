package com.hh.agent.android.debug;

import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.floating.FloatingBallManager;
import com.hh.agent.android.log.AgentLogs;

/**
 * Debug-only adb control surface for dry-running MobileAgent floating states.
 */
public class MobileAgentDebugReceiver extends BroadcastReceiver {

    public static final String ACTION_SPINNER_START = "com.hh.agent.DEBUG_AGENT_SPINNER_START";
    public static final String ACTION_SPINNER_STOP = "com.hh.agent.DEBUG_AGENT_SPINNER_STOP";
    public static final String ACTION_SET_CLIPBOARD = "com.hh.agent.DEBUG_SET_CLIPBOARD";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TEXT_BASE64 = "text_base64";
    private static final String TAG = "MobileAgentDebugReceiver";
    private static final long SHOW_AFTER_CONTAINER_FINISH_DELAY_MS = 240L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        AgentLogs.info(TAG, "broadcast_received", "action=" + action);
        if (ACTION_SPINNER_START.equals(action)) {
            mainHandler.post(() -> startSpinnerDryRun(context.getApplicationContext()));
            return;
        }
        if (ACTION_SPINNER_STOP.equals(action)) {
            mainHandler.post(() -> stopSpinnerDryRun(context.getApplicationContext()));
            return;
        }
        if (ACTION_SET_CLIPBOARD.equals(action)) {
            mainHandler.post(() -> setClipboardText(context.getApplicationContext(), intent));
        }
    }

    private void startSpinnerDryRun(Context context) {
        boolean containerFinishRequested = ContainerActivity.finishActiveInstanceForDebug();
        FloatingBallManager manager = FloatingBallManager.getInstance(context);
        manager.initialize();
        manager.setWorking(true);

        if (containerFinishRequested) {
            mainHandler.postDelayed(manager::show, SHOW_AFTER_CONTAINER_FINISH_DELAY_MS);
        } else {
            manager.show();
        }
        AgentLogs.info(TAG, "spinner_start_dryrun",
                "container_finish_requested=" + containerFinishRequested);
    }

    private void stopSpinnerDryRun(Context context) {
        FloatingBallManager manager = FloatingBallManager.getInstance(context);
        manager.setWorking(false);
        AgentLogs.info(TAG, "spinner_stop_dryrun", null);
    }

    private void setClipboardText(Context context, Intent intent) {
        String text = readTextExtra(intent);
        if (text == null) {
            AgentLogs.warn(TAG, "clipboard_set_skipped", "reason=missing_text");
            return;
        }
        ClipboardManager clipboardManager =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            AgentLogs.warn(TAG, "clipboard_set_failed", "reason=clipboard_service_unavailable");
            return;
        }
        clipboardManager.setPrimaryClip(ClipData.newPlainText("mobile-agent-debug", text));
        AgentLogs.info(TAG, "clipboard_set_complete", "text_length=" + text.length());
    }

    private String readTextExtra(Intent intent) {
        String text = intent.getStringExtra(EXTRA_TEXT);
        if (text != null) {
            return text;
        }
        String base64Text = intent.getStringExtra(EXTRA_TEXT_BASE64);
        if (base64Text == null || base64Text.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.decode(base64Text, Base64.DEFAULT);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            AgentLogs.warn(TAG, "clipboard_base64_decode_failed", "message=" + exception.getMessage());
            return null;
        }
    }
}
