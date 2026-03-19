package com.hh.agent.android;

import android.content.Context;
import com.hh.agent.android.voice.IVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.android.floating.FloatingBallManager;
import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.WorkspaceManager;
import com.hh.agent.core.ToolExecutor;
import com.hh.agent.core.api.NativeMobileAgentApi;

import java.io.InputStream;
import java.util.Map;

/**
 * Agent 初始化器
 * 统一管理 Agent 的初始化流程
 */
public class AgentInitializer {

    private static String configJson = "";

    /**
     * 初始化 Agent
     * @param application Application Context
     * @param voiceRecognizer 语音识别器实现（可传入 null 使用默认实现）
     * @param tools 要注册的工具 Map
     * @param callback 初始化完成回调（Agent 核心初始化完成后调用）
     */
    public static void initialize(Context application,
                                  IVoiceRecognizer voiceRecognizer,
                                  Map<String, ToolExecutor> tools,
                                  Runnable callback) {
        // 0. 设置语音识别器
        VoiceRecognizerHolder.getInstance().setRecognizer(voiceRecognizer);

        // 1. 创建 AndroidToolManager
        AndroidToolManager toolManager = new AndroidToolManager(application);

        // 2. 批量注册工具
        toolManager.registerTools(tools);
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
                }
            }
        } catch (Exception e) {
            // Ignore workspace errors
        }

        // 5. 初始化 native agent
        String toolsJson = toolManager.generateToolsJsonString();
        try {
            System.loadLibrary("icraw");
            NativeMobileAgentApi.getInstance().initialize(toolsJson, configJson);
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException("Failed to load native library: " + e.getMessage(), e);
        }

        // 6. 回调
        callback.run();
    }

    /**
     * 初始化悬浮球
     * 必须在 Agent 核心初始化完成后调用
     * @param application Application Context
     */
    public static void initializeFloatingBall(Context application) {
        // 初始化悬浮球
        FloatingBallManager floatingBallManager = FloatingBallManager.getInstance(application);
        floatingBallManager.initialize();

        // 检查权限并尝试显示悬浮球
        if (floatingBallManager.checkOverlayPermission()) {
            floatingBallManager.show();
        } else {
            floatingBallManager.showPermissionTip();
        }

        // 设置悬浮球点击事件（启动容器Activity）
        floatingBallManager.setOnClickListener(v -> {
            // 隐藏悬浮球
            floatingBallManager.hide();
            // 启动容器Activity
            android.content.Intent intent = new android.content.Intent(application, ContainerActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            application.startActivity(intent);
        });
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
        } catch (Exception e) {
            configJson = "";
        }
    }
}
