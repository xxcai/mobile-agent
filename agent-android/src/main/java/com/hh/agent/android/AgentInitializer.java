package com.hh.agent.android;

import android.content.Context;
import com.hh.agent.android.WorkspaceManager;
import com.hh.agent.library.ToolExecutor;
import com.hh.agent.library.api.NativeMobileAgentApi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
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
     * @param tools 要注册的工具 Map
     * @param callback 初始化完成回调
     */
    public static void initialize(Context application,
                                  Map<String, ToolExecutor> tools,
                                  Runnable callback) {
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
