package com.hh.agent.android;

import android.app.Application;
import android.content.Context;

import com.hh.agent.android.debug.SessionDebugTranscriptStore;
import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.floating.FloatingBallLifecycleCallbacks;
import com.hh.agent.android.floating.FloatingBallManager;
import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.viewcontext.ActivityViewContextSourcePolicy;
import com.hh.agent.android.viewcontext.ScreenSnapshotAnalyzer;
import com.hh.agent.android.viewcontext.ScreenSnapshotAnalyzerHolder;
import com.hh.agent.android.viewcontext.ScreenSnapshotWarmupCapable;
import com.hh.agent.android.viewcontext.ViewContextSourcePolicyRegistry;
import com.hh.agent.android.voice.IVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.core.api.impl.NativeMobileAgentApi;
import com.hh.agent.core.shortcut.ShortcutExecutor;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * Centralized Agent bootstrapper for app, shortcut, screen-vision, and floating-ball initialization.
 */
public class AgentInitializer {

    private static final String TAG = "AgentInitializer";
    private static final String DEFAULT_NATIVE_LOG_LEVEL = "debug";
    private static String configJson = "{}";

    private AgentInitializer() {
    }

    public static void setLogger(AgentLogger logger) {
        AgentLogs.setLogger(logger);
        NativeMobileAgentApi.getInstance().setLogger(AgentLogs.getLogger());
    }

    public static AgentLogger getLogger() {
        return AgentLogs.getLogger();
    }

    public static void setScreenSnapshotAnalyzer(ScreenSnapshotAnalyzer analyzer) {
        String analyzerName = analyzer != null ? analyzer.getClass().getName() : "null";
        AgentLogs.info(TAG, "screen_snapshot_analyzer_set", "analyzer=" + analyzerName);
        ScreenSnapshotAnalyzerHolder.getInstance().setAnalyzer(analyzer);
        if (analyzer instanceof ScreenSnapshotWarmupCapable) {
            ((ScreenSnapshotWarmupCapable) analyzer).prewarmAsync();
            AgentLogs.info(TAG, "screen_snapshot_analyzer_prewarm_requested", "analyzer=" + analyzerName);
        }
    }

    public static void initialize(Context application,
                                  IVoiceRecognizer voiceRecognizer,
                                  Collection<? extends ShortcutExecutor> shortcuts,
                                  ActivityViewContextSourcePolicy viewContextSourcePolicy,
                                  Runnable callback) {
        initializeInternal(application, voiceRecognizer, shortcuts, viewContextSourcePolicy, callback);
    }

    private static void initializeInternal(Context application,
                                           IVoiceRecognizer voiceRecognizer,
                                           Collection<? extends ShortcutExecutor> shortcuts,
                                           ActivityViewContextSourcePolicy viewContextSourcePolicy,
                                           Runnable callback) {
        ensureScreenSnapshotAnalyzerInstalled(application);
        int shortcutCount = shortcuts != null ? shortcuts.size() : 0;
        boolean analyzerRegistered = ScreenSnapshotAnalyzerHolder.getInstance().getAnalyzer() != null;
        String policyName = viewContextSourcePolicy != null
                ? viewContextSourcePolicy.getClass().getName()
                : "null";
        AgentLogs.info(TAG,
                "initialize_start",
                "shortcut_count=" + shortcutCount
                        + " analyzer_registered=" + analyzerRegistered
                        + " view_context_policy=" + policyName);
        if (callback == null) {
            AgentLogs.warn(TAG, "initialize_callback_null", null);
        }

        VoiceRecognizerHolder.getInstance().setRecognizer(voiceRecognizer);
        SessionDebugTranscriptStore.initialize(application);
        ViewContextSourcePolicyRegistry.setActivePolicy(viewContextSourcePolicy);

        AndroidToolManager toolManager = new AndroidToolManager(application);
        if (shortcuts != null) {
            toolManager.registerShortcuts(shortcuts);
        }
        toolManager.initialize();

        loadConfigFromAssets(application);

        try {
            WorkspaceManager workspaceManager = new WorkspaceManager(application);
            String workspacePath = workspaceManager.initialize();
            if (!workspacePath.isEmpty()) {
                injectWorkspacePath(workspacePath);
            }
        } catch (Exception e) {
            AgentLogs.warn(TAG, "workspace_initialize_failed", "message=" + e.getMessage());
        }

        String toolsJson = toolManager.generateToolsJsonString();
        try {
            System.loadLibrary("icraw");
            NativeMobileAgentApi.getInstance().setLogger(AgentLogs.getLogger());
            NativeMobileAgentApi.getInstance().setNativeLogLevel(DEFAULT_NATIVE_LOG_LEVEL);
            NativeMobileAgentApi.getInstance().initialize(toolsJson, configJson);
            AgentLogs.info(TAG, "native_initialize_complete", "tools_json_length=" + toolsJson.length());
        } catch (UnsatisfiedLinkError e) {
            AgentLogs.error(TAG, "native_initialize_failed", "message=" + e.getMessage(), e);
            throw new RuntimeException("Failed to load native library: " + e.getMessage(), e);
        }

        if (callback != null) {
            callback.run();
        }
        AgentLogs.info(TAG, "initialize_complete", null);
    }

    private static void ensureScreenSnapshotAnalyzerInstalled(Context application) {
        if (ScreenSnapshotAnalyzerHolder.getInstance().getAnalyzer() != null) {
            AgentLogs.debug(TAG, "screen_snapshot_auto_install_skipped", "reason=analyzer_already_registered");
            return;
        }
        try {
            Class<?> installerClass = Class.forName("com.hh.agent.screenvision.AgentScreenVision");
            installerClass.getMethod("install", Context.class).invoke(null, application);
            boolean installed = ScreenSnapshotAnalyzerHolder.getInstance().getAnalyzer() != null;
            AgentLogs.info(TAG, "screen_snapshot_auto_install_complete", "installed=" + installed);
        } catch (ClassNotFoundException notFound) {
            AgentLogs.debug(TAG, "screen_snapshot_auto_install_unavailable", "reason=module_not_on_classpath");
        } catch (Exception e) {
            AgentLogs.warn(TAG, "screen_snapshot_auto_install_failed", "message=" + e.getMessage());
        }
    }

    public static void initializeFloatingBall(Application application,
                                              List<String> hiddenActivityClassNames) {
        int hiddenCount = hiddenActivityClassNames != null ? hiddenActivityClassNames.size() : 0;
        AgentLogs.info(TAG, "floating_ball_initialize_start", "hidden_activity_count=" + hiddenCount);

        FloatingBallManager floatingBallManager = FloatingBallManager.getInstance(application);
        floatingBallManager.initialize();

        if (floatingBallManager.checkOverlayPermission()) {
            floatingBallManager.show();
        } else {
            AgentLogs.warn(TAG, "overlay_permission_missing", "component=floating_ball_initializer");
            floatingBallManager.showPermissionTip();
        }

        floatingBallManager.setOnClickListener(v -> {
            floatingBallManager.hide();
            android.content.Intent intent = new android.content.Intent(application, ContainerActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            application.startActivity(intent);
        });

        application.registerActivityLifecycleCallbacks(
                new FloatingBallLifecycleCallbacks(application, hiddenActivityClassNames)
        );
        AgentLogs.info(TAG, "floating_ball_initialize_complete", null);
    }

    private static void loadConfigFromAssets(Context context) {
        try (InputStream is = context.getAssets().open("config.json")) {
            byte[] buffer = new byte[is.available()];
            int bytesRead = is.read(buffer);
            if (bytesRead <= 0) {
                configJson = "{}";
                AgentLogs.warn(TAG, "config_load_empty", "fallback=empty_json");
                return;
            }
            configJson = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            AgentLogs.info(TAG, "config_loaded", "content_length=" + configJson.length());
        } catch (Exception e) {
            configJson = "{}";
            AgentLogs.warn(TAG, "config_load_failed", "message=" + e.getMessage() + " fallback=empty_json");
        }
    }

    private static void injectWorkspacePath(String workspacePath) {
        try {
            String current = configJson != null ? configJson.trim() : "";
            JSONObject configObject = current.isEmpty() ? new JSONObject() : new JSONObject(current);
            configObject.put("workspacePath", workspacePath);
            configJson = configObject.toString();
            AgentLogs.info(TAG, "workspace_path_injected", "path=" + workspacePath);
        } catch (Exception e) {
            AgentLogs.warn(TAG, "workspace_path_inject_failed", "message=" + e.getMessage());
        }
    }
}