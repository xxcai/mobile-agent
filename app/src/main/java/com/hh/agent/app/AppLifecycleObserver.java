package com.hh.agent.app;

import android.app.Activity;
import android.app.Application;
import android.util.Log;
import com.hh.agent.MainActivity;
import com.hh.agent.android.floating.FloatingBallManager;

/**
 * 应用生命周期观察者
 * 通过 ActivityLifecycleCallbacks 监听应用可见性，直接控制悬浮球
 */
public class AppLifecycleObserver implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppLifecycleObserver";

    private int foregroundActivityCount = 0;
    private final Application application;

    public AppLifecycleObserver(Application application) {
        this.application = application;
    }

    @Override
    public void onActivityStarted(android.app.Activity activity) {
        foregroundActivityCount++;
        Log.d(TAG, "onActivityStarted: count=" + foregroundActivityCount);
    }

    @Override
    public void onActivityStopped(android.app.Activity activity) {
        foregroundActivityCount--;
        Log.d(TAG, "onActivityStopped: count=" + foregroundActivityCount);

        if (foregroundActivityCount <= 0) {
            foregroundActivityCount = 0;
            FloatingBallManager.getInstance(application).hide();
            Log.d(TAG, "onActivityStopped: app background, hide floating ball");
        }
    }

    @Override
    public void onActivityCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {
        // not used
    }

    @Override
    public void onActivityDestroyed(android.app.Activity activity) {
        // not used
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity
                && FloatingBallManager.getInstance(application).checkOverlayPermission()) {
            FloatingBallManager.getInstance(application).show();
            Log.d(TAG, "onActivityResumed: MainActivity, show floating ball");
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        // MainActivity 暂停时隐藏悬浮球
        if (activity instanceof MainActivity) {
            FloatingBallManager.getInstance(application).hide();
            Log.d(TAG, "onActivityPaused: MainActivity, hide floating ball");
        }
    }

    @Override
    public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {
        // not used
    }
}
