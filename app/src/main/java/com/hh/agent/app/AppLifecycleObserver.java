package com.hh.agent.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.hh.agent.MainActivity;
import com.hh.agent.floating.FloatingBallManager;

/**
 * 应用生命周期观察者
 * 通过 ActivityLifecycleCallbacks 监听应用可见性，发送广播控制悬浮球
 */
public class AppLifecycleObserver implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppLifecycleObserver";

    public static final String ACTION_APP_FOREGROUND = "com.hh.agent.action.APP_FOREGROUND";
    public static final String ACTION_APP_BACKGROUND = "com.hh.agent.action.APP_BACKGROUND";

    private int foregroundActivityCount = 0;
    private final Application application;

    public AppLifecycleObserver(Application application) {
        this.application = application;
    }

    @Override
    public void onActivityStarted(android.app.Activity activity) {
        foregroundActivityCount++;
        Log.d(TAG, "onActivityStarted: count=" + foregroundActivityCount);

        // 从后台进入前台
        if (foregroundActivityCount == 1) {
            sendBroadcast(ACTION_APP_FOREGROUND);
        }
    }

    @Override
    public void onActivityStopped(android.app.Activity activity) {
        foregroundActivityCount--;
        Log.d(TAG, "onActivityStopped: count=" + foregroundActivityCount);

        // 从前台进入后台
        if (foregroundActivityCount == 0) {
            sendBroadcast(ACTION_APP_BACKGROUND);
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
        // MainActivity 恢复时显示悬浮球
        if (activity instanceof MainActivity) {
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

    private void sendBroadcast(String action) {
        Intent intent = new Intent(action);
        // 指定包名，确保广播能传递给同应用的 BroadcastReceiver
        intent.setPackage(application.getPackageName());
        application.sendBroadcast(intent);
        Log.d(TAG, "sendBroadcast: " + action);
    }
}
