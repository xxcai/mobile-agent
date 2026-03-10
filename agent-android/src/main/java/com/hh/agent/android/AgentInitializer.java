package com.hh.agent.android;

import android.content.Context;
import com.hh.agent.android.presenter.NativeMobileAgentApiAdapter;
import com.hh.agent.library.ToolExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 初始化器
 * 统一管理 Agent 的初始化流程
 */
public class AgentInitializer {

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

        // 3. 初始化 native agent
        NativeMobileAgentApiAdapter.loadConfigFromAssets(application);
        NativeMobileAgentApiAdapter adapter = new NativeMobileAgentApiAdapter();
        String toolsJson = toolManager.generateToolsJsonString();
        adapter.initialize(toolsJson, application);

        // 4. 回调
        callback.run();
    }
}
