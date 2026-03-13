package com.hh.agent.app;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.floating.ContainerActivity;
import com.hh.agent.floating.FloatingBallManager;
import com.hh.agent.library.api.NativeMobileAgentApi;
import com.hh.agent.voice.MockVoiceRecognizer;

/**
 * Application类
 * 初始化悬浮球和生命周期观察者
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

        // 初始化会话持久化（当前为 Mock，后续 C++ 实现）
        NativeMobileAgentApi.getInstance().initializeContext(this);
        NativeMobileAgentApi.getInstance().loadAllSessions();

        // 初始化语音识别器（Mock 实现）
        VoiceRecognizerHolder.getInstance().setRecognizer(new MockVoiceRecognizer());

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
