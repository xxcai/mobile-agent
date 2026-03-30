package com.hh.agent.app;

import android.app.Application;
import android.util.Log;
import com.hh.agent.FloatingBallHiddenActivity;
import com.hh.agent.android.AgentInitializer;
import com.hh.agent.shortcut.AppShortcutProvider;
import com.hh.agent.voice.MockVoiceRecognizer;

import java.util.Collections;

/**
 * Application类
 * 初始化悬浮球、Agent 和生命周期观察者
 */
public class App extends Application {

    private static final String TAG = "App";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.d(TAG, "App onCreate");

        // 初始化 Agent（语音识别器通过注入方式在 AgentInitializer 内部设置）
        AgentInitializer.initialize(
                this,
                new MockVoiceRecognizer(),
                AppShortcutProvider.createShortcuts(),
                DefaultActivityViewContextSourcePolicy.create(),
                () -> {
                    Log.d(TAG, "Agent initialized successfully");

                    // 初始化悬浮球，并演示宿主追加隐藏页面的接入方式。
                    AgentInitializer.initializeFloatingBall(
                            App.this,
                            Collections.singletonList(FloatingBallHiddenActivity.class.getName())
                    );
                });
    }

    public static App getInstance() {
        return instance;
    }
}
