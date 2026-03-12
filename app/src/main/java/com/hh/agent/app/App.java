package com.hh.agent.app;

import android.app.Application;
import android.util.Log;
import com.hh.agent.floating.FloatingBallManager;

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

        // 初始化悬浮球
        floatingBallManager = FloatingBallManager.getInstance(this);
        floatingBallManager.initialize();

        // 如果有悬浮窗权限则显示悬浮球
        if (floatingBallManager.checkOverlayPermission()) {
            floatingBallManager.show();
        }

        // 设置悬浮球点击事件（待Phase 2实现容器Activity）
        floatingBallManager.setOnClickListener(v -> {
            Log.d(TAG, "Floating ball clicked - will launch container activity in Phase 2");
            // TODO Phase 2: 启动容器Activity
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
