package com.hh.agent.app;

import android.app.Application;
import android.util.Log;
import com.hh.agent.android.AgentInitializer;
import com.hh.agent.android.floating.FloatingBallManager;
import com.hh.agent.library.ToolExecutor;
import com.hh.agent.tool.DisplayNotificationTool;
import com.hh.agent.tool.ReadClipboardTool;
import com.hh.agent.tool.SearchContactsTool;
import com.hh.agent.tool.SendImMessageTool;
import com.hh.agent.voice.MockVoiceRecognizer;

import java.util.HashMap;
import java.util.Map;

/**
 * Application类
 * 初始化悬浮球、Agent 和生命周期观察者
 */
public class App extends Application {

    private static final String TAG = "App";
    private static App instance;
    private AppLifecycleObserver lifecycleObserver;
    private FloatingBallManager floatingBallManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.d(TAG, "App onCreate");

        // 准备工具 Map
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("display_notification", new DisplayNotificationTool(this));
        tools.put("read_clipboard", new ReadClipboardTool(this));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());

        // 初始化 Agent（语音识别器通过注入方式在 AgentInitializer 内部设置）
        AgentInitializer.initialize(this, new MockVoiceRecognizer(), tools, () -> {
            Log.d(TAG, "Agent initialized successfully");

            // 初始化悬浮球
            AgentInitializer.initializeFloatingBall(App.this);

            // 注册生命周期观察者
            lifecycleObserver = new AppLifecycleObserver(this);
            registerActivityLifecycleCallbacks(lifecycleObserver);
        });
    }

    public static App getInstance() {
        return instance;
    }

    public FloatingBallManager getFloatingBallManager() {
        return floatingBallManager;
    }
}
