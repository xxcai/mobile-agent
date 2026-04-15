package com.hh.agent.android;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.debug.SessionDebugTranscriptStore;
import com.hh.agent.android.viewcontext.ActivityViewContextSourcePolicy;
import com.hh.agent.android.viewcontext.ViewContextSourcePolicyRegistry;
import com.hh.agent.android.voice.IVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.android.floating.FloatingBallManager;
import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.floating.FloatingBallLifecycleCallbacks;
import com.hh.agent.android.WorkspaceManager;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.api.impl.NativeMobileAgentApi;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/**
 * Agent 初始化器
 * 统一管理 Agent 的初始化流程
 */
public class AgentInitializer {

    private static final String TAG = "AgentInitializer";
    private static final String DEFAULT_NATIVE_LOG_LEVEL = "debug";
    private static String configJson = "";

    /**
     * 设置 Agent logger。
     * 本方法只建立注入入口；未设置时仍使用默认 Android logger。
     * 传入 null 时恢复默认实现。
     */
    public static void setLogger(AgentLogger logger) {
        AgentLogs.setLogger(logger);
        NativeMobileAgentApi.getInstance().setLogger(AgentLogs.getLogger());
    }

    /**
     * 获取当前生效的 Agent logger。
     */
    public static AgentLogger getLogger() {
        return AgentLogs.getLogger();
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
        int shortcutCount = shortcuts != null ? shortcuts.size() : 0;
        AgentLogs.info(TAG, "initialize_start",
                "shortcut_count=" + shortcutCount);
        if (callback == null) {
            AgentLogs.warn(TAG, "initialize_callback_null", null);
        }

        // 0. 设置语音识别器
        VoiceRecognizerHolder.getInstance().setRecognizer(voiceRecognizer);
        SessionDebugTranscriptStore.initialize(application);
        ViewContextSourcePolicyRegistry.setActivePolicy(viewContextSourcePolicy);

        // 1. 创建 AndroidToolManager
        AndroidToolManager toolManager = new AndroidToolManager(application);

        // 2. 批量注册 shortcuts
        if (shortcuts != null) {
            toolManager.registerShortcuts(shortcuts);
        }
        toolManager.initialize();

        // 3. 读取配置文件
        loadConfigFromAssets(application);

        // 4. 初始化 workspace
        try {
            WorkspaceManager workspaceManager = new WorkspaceManager(application);
            String workspacePath = workspaceManager.initialize();
            if (!workspacePath.isEmpty()) {
                int lastBrace = configJson.lastIndexOf('}');
                if (lastBrace > 0) {
                    String newField = ",\"workspacePath\":\"" + workspacePath + "\"";
                    configJson = configJson.substring(0, lastBrace) + newField + configJson.substring(lastBrace);
                    AgentLogs.info(TAG, "workspace_path_injected", "path=" + workspacePath);
                }
            }
        } catch (Exception e) {
            AgentLogs.warn(TAG, "workspace_initialize_failed", "message=" + e.getMessage());
        }

        // 5. 初始化 native agent
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

        // 6. 回调
        if (callback != null) {
            callback.run();
        }
        AgentLogs.info(TAG, "initialize_complete", null);
    }

    /**
     * 初始化悬浮球和生命周期控制。
     * 必须在 Agent 核心初始化完成后调用
     * 调用方应传入宿主 Application。
     * hiddenActivityClassNames 传 null 时使用默认规则：
     * 1. ContainerActivity 显示时隐藏悬浮球
     * 2. 宿主 App 在前台时显示，退到后台时隐藏
     * 如需追加隐藏页面，可传入不展示悬浮球的 Activity 完整类名列表。
     * @param application 宿主 Application
     * @param hiddenActivityClassNames 需要隐藏悬浮球的 Activity 完整类名列表，可为 null
     */
    public static void initializeFloatingBall(Application application,
                                              List<String> hiddenActivityClassNames) {
        int hiddenCount = hiddenActivityClassNames != null ? hiddenActivityClassNames.size() : 0;
        AgentLogs.info(TAG, "floating_ball_initialize_start", "hidden_activity_count=" + hiddenCount);

        // 初始化悬浮球
        FloatingBallManager floatingBallManager = FloatingBallManager.getInstance(application);
        floatingBallManager.initialize();

        // 检查权限并尝试显示悬浮球
        if (floatingBallManager.checkOverlayPermission()) {
            floatingBallManager.show();
        } else {
            AgentLogs.warn(TAG, "overlay_permission_missing", "component=floating_ball_initializer");
            floatingBallManager.showPermissionTip();
        }

        // 设置悬浮球点击事件（启动容器Activity）
        floatingBallManager.setOnClickListener(v -> {
            // 隐藏悬浮球
            floatingBallManager.hide();
            // 启动容器Activity
            Intent intent = new Intent(application, ContainerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            application.startActivity(intent);
        });

        application.registerActivityLifecycleCallbacks(
                new FloatingBallLifecycleCallbacks(application, hiddenActivityClassNames)
        );
        AgentLogs.info(TAG, "floating_ball_initialize_complete", null);
    }

    /**
     * 从 assets 读取配置文件
     */
    private static void loadConfigFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("config.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            configJson = new String(buffer, "UTF-8");
            is.close();
            AgentLogs.info(TAG, "config_loaded", "content_length=" + configJson.length());
        } catch (Exception e) {
            configJson = "";
            AgentLogs.warn(TAG, "config_load_failed", "message=" + e.getMessage());
        }
    }
}
