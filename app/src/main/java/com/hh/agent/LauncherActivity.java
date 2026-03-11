package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.hh.agent.android.AgentActivity;
import com.hh.agent.android.AgentInitializer;
import com.hh.agent.voice.MockVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
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
 * 启动界面 - 初始化 Agent 并进入 AgentActivity
 */
public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
            startActivity(new Intent(this, AgentActivity.class));
            finish();
        });
    }
}
