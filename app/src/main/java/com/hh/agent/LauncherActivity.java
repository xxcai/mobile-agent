package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.hh.agent.android.AgentActivity;
import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.android.tool.ShowToastTool;
import com.hh.agent.android.tool.DisplayNotificationTool;
import com.hh.agent.android.tool.ReadClipboardTool;
import com.hh.agent.android.tool.TakeScreenshotTool;
import com.hh.agent.tool.SearchContactsTool;
import com.hh.agent.tool.SendImMessageTool;

/**
 * 启动界面 - 初始化 Tool 管理器并进入 AgentActivity
 */
public class LauncherActivity extends AppCompatActivity {

    private static AndroidToolManager toolManager;

    /**
     * 获取 AndroidToolManager 单例实例
     * @return AndroidToolManager 实例
     */
    public static AndroidToolManager getToolManager() {
        return toolManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化 AndroidToolManager 并注册内置 Tool
        initializeToolManager();

        // 跳转到 agent-android 的 AgentActivity
        Intent intent = new Intent(this, AgentActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * 初始化 AndroidToolManager 并注册 6 个内置 Tool
     */
    private void initializeToolManager() {
        // 创建 AndroidToolManager 实例
        toolManager = new AndroidToolManager(this);

        // 注册 6 个内置 Tool
        toolManager.registerTool(new ShowToastTool(this));
        toolManager.registerTool(new DisplayNotificationTool(this));
        toolManager.registerTool(new ReadClipboardTool(this));
        toolManager.registerTool(new TakeScreenshotTool(this));
        toolManager.registerTool(new SearchContactsTool());
        toolManager.registerTool(new SendImMessageTool());

        // 初始化 AndroidToolManager
        toolManager.initialize();
    }
}
