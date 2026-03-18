package com.hh.agent.app;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.hh.agent.android.AgentInitializer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.floating.ContainerActivity;
import com.hh.agent.floating.FloatingBallManager;
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

        // 初始化语音识别器（Mock 实现）
        VoiceRecognizerHolder.getInstance().setRecognizer(new MockVoiceRecognizer());

        // 准备工具 Map
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("display_notification", new DisplayNotificationTool(this));
        tools.put("read_clipboard", new ReadClipboardTool(this));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());

        // 初始化 Agent（必须在悬浮球之前完成）
        AgentInitializer.initialize(this, tools, () -> {
            Log.d(TAG, "Agent initialized successfully");
        });

        // 初始化悬浮球
        floatingBallManager = FloatingBallManager.getInstance(this);
        floatingBallManager.initialize();

        // 检查权限并尝试显示悬浮球
        if (floatingBallManager.checkOverlayPermission()) {
            floatingBallManager.show();
        } else {
            floatingBallManager.showPermissionTip();
        }

        // 设置悬浮球点击事件（启动容器Activity）
        floatingBallManager.setOnClickListener(v -> {
            Log.d(TAG, "Floating ball clicked - launching container activity");
            // 隐藏悬浮球
            floatingBallManager.hide();
            // 启动容器Activity
            Intent intent = new Intent(this, ContainerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        // 注册生命周期观察者
        lifecycleObserver = new AppLifecycleObserver(this);
        registerActivityLifecycleCallbacks(lifecycleObserver);
    }

    public static App getInstance() {
        return instance;
    }

    public FloatingBallManager getFloatingBallManager() {
        return floatingBallManager;
    }
}
