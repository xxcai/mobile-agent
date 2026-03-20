package com.hh.agent;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.core.ToolExecutor;
import com.hh.agent.tool.DisplayNotificationTool;
import com.hh.agent.tool.ReadClipboardTool;
import com.hh.agent.tool.SearchContactsTool;
import com.hh.agent.tool.SendImMessageTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * MainActivity - app-level test entry.
 * Used to validate tool channel behavior without adding test hooks into agent-core/agent-android.
 */
public class MainActivity extends AppCompatActivity {

    private TextView outputView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Tool Channel Tests");
        setContentView(createContentView());
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);

        Button runButton = new Button(this);
        runButton.setText("Run Tool Channel Tests");
        runButton.setOnClickListener(v -> runToolChannelTests());
        container.addView(runButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        outputView = new TextView(this);
        outputView.setTextIsSelectable(true);
        outputView.setMovementMethod(new ScrollingMovementMethod());
        outputView.setPadding(0, dp(16), 0, 0);
        container.addView(outputView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        return scrollView;
    }

    private void runToolChannelTests() {
        StringBuilder report = new StringBuilder();

        try {
            AndroidToolManager manager = buildTestToolManager();
            appendChannelSummary(report, manager);

            runCase(
                    report,
                    manager,
                    "Case 1: legacy call_android_tool success",
                    "call_android_tool",
                    new JSONObject()
                            .put("function", "search_contacts")
                            .put("args", new JSONObject().put("query", "李四")),
                    "success");

            runCase(
                    report,
                    manager,
                    "Case 2: legacy call_android_tool missing function",
                    "call_android_tool",
                    new JSONObject().put("args", new JSONObject()),
                    "invalid_args");

            runCase(
                    report,
                    manager,
                    "Case 3: legacy call_android_tool inner tool not found",
                    "call_android_tool",
                    new JSONObject()
                            .put("function", "not_exists")
                            .put("args", new JSONObject()),
                    "tool_not_found");

            runCase(
                    report,
                    manager,
                    "Case 4: unsupported outer channel",
                    "android_gesture_tool",
                    new JSONObject()
                            .put("action", "tap")
                            .put("x", 100)
                            .put("y", 200),
                    "unsupported_tool_channel");
        } catch (Exception e) {
            report.append("Unexpected test failure: ").append(e.getMessage()).append('\n');
        }

        outputView.setText(report.toString());
    }

    private AndroidToolManager buildTestToolManager() {
        AndroidToolManager manager = new AndroidToolManager(this);
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("display_notification", new DisplayNotificationTool(this));
        tools.put("read_clipboard", new ReadClipboardTool(this));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());
        manager.registerTools(tools);
        manager.initialize();
        return manager;
    }

    private void runCase(StringBuilder report,
                         AndroidToolManager manager,
                         String title,
                         String outerToolName,
                         JSONObject params,
                         String expectedMarker) throws Exception {
        String raw = manager.callTool(outerToolName, params.toString());
        JSONObject result = new JSONObject(raw);
        boolean matched = "success".equals(expectedMarker)
                ? result.optBoolean("success", false)
                : expectedMarker.equals(result.optString("error"));

        report.append(title).append('\n');
        report.append("request: ").append(outerToolName).append(' ').append(params).append('\n');
        report.append("result: ").append(result).append('\n');
        report.append("expected: ").append(expectedMarker).append('\n');
        report.append("status: ").append(matched ? "PASS" : "FAIL").append("\n\n");
    }

    private void appendChannelSummary(StringBuilder report, AndroidToolManager manager) throws Exception {
        report.append("Registered channels: ").append(manager.getRegisteredChannels().keySet()).append('\n');

        JSONObject schema = new JSONObject(manager.generateToolsJsonString());
        JSONArray tools = schema.getJSONArray("tools");
        boolean containsLegacyChannel = false;
        for (int i = 0; i < tools.length(); i++) {
            JSONObject function = tools.getJSONObject(i).getJSONObject("function");
            if ("call_android_tool".equals(function.optString("name"))) {
                containsLegacyChannel = true;
                break;
            }
        }

        report.append("Schema contains call_android_tool: ")
                .append(containsLegacyChannel ? "PASS" : "FAIL")
                .append("\n\n");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
