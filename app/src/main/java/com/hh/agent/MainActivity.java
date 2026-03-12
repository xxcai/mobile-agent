package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.hh.agent.android.AgentInitializer;
import com.hh.agent.android.voice.IVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.voice.MockVoiceRecognizer;
import com.hh.agent.floating.FloatingBallManager;
import com.hh.agent.library.ToolExecutor;
import com.hh.agent.tool.DisplayNotificationTool;
import com.hh.agent.tool.ReadClipboardTool;
import com.hh.agent.tool.SearchContactsTool;
import com.hh.agent.tool.SendImMessageTool;
import com.hh.agent.tool.ShowToastTool;
import com.hh.agent.tool.TakeScreenshotTool;

import java.util.HashMap;
import java.util.Map;

/**
 * MainActivity - 主界面启动页
 * 作为空白容器，悬浮球浮在此界面上
 * Agent 功能通过悬浮球点击触发 ContainerActivity 加载
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 空白容器，不需要 setContentView

        // 准备工具 Map
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("show_toast", new ShowToastTool(this));
        tools.put("display_notification", new DisplayNotificationTool(this));
        tools.put("read_clipboard", new ReadClipboardTool(this));
        tools.put("take_screenshot", new TakeScreenshotTool(this));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());

        // 注入 Mock 语音识别器（开发测试用）
        VoiceRecognizerHolder.getInstance().setRecognizer(new MockVoiceRecognizer());

        // 初始化 Agent
        AgentInitializer.initialize(getApplication(), tools, () -> {
            // Agent 初始化完成后不做任何操作，保持在 MainActivity
            // 用户通过悬浮球进入 Agent 界面
        });

        // 注册生命周期回调，控制悬浮球显示
        getApplication().registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(android.app.Activity activity) {
                if (activity == MainActivity.this) {
                    FloatingBallManager.getInstance(MainActivity.this).show();
                }
            }

            @Override
            public void onActivityPaused(android.app.Activity activity) {
                if (activity == MainActivity.this) {
                    FloatingBallManager.getInstance(MainActivity.this).hide();
                }
            }

            // 其他回调方法保持空实现
            @Override
            public void onActivityCreated(android.app.Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(android.app.Activity activity) {}

            @Override
            public void onActivityStopped(android.app.Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(android.app.Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }
}
