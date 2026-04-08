package com.hh.agent.app;

import android.app.Application;
import android.util.Log;

import com.hh.agent.FloatingBallHiddenActivity;
import com.hh.agent.android.AgentInitializer;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.tool.DisplayNotificationTool;
import com.hh.agent.tool.ReadClipboardTool;
import com.hh.agent.tool.SearchContactsTool;
import com.hh.agent.tool.SendImMessageTool;
import com.hh.agent.voice.MockVoiceRecognizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class App extends Application {

    private static final String TAG = "App";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.d(TAG, "App onCreate");

        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("display_notification", new DisplayNotificationTool(this));
        tools.put("read_clipboard", new ReadClipboardTool(this));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());
        tools.putAll(RouteToolProvider.createRouteTools(this));

        AgentInitializer.initialize(
                this,
                new MockVoiceRecognizer(),
                tools,
                DefaultActivityViewContextSourcePolicy.create(),
                () -> {
                    Log.d(TAG, "Agent initialized successfully");
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