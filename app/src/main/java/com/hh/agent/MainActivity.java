package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.android.gesture.GestureExecutorRegistry;
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

    private static final class SchemaSummary {
        boolean containsLegacyChannel;
        boolean containsGestureChannel;
        String legacyDescription = "";
        String legacyFunctionDescription = "";
        String legacyArgsDescription = "";
        String gestureDescription = "";
    }

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

        addActionButton(container, "Run All", v -> runAllSections());
        addActionButton(container, "Run Channel Summary", v -> runChannelSummarySection());
        addActionButton(container, "Run Intent Mapping", v -> runIntentMappingSection());
        addActionButton(container, "Run Skill Compatibility", v -> runSkillCompatibilitySection());
        addActionButton(container, "Run Runtime Cases", v -> runRuntimeCasesSection());
        addActionButton(container, "Open Visible Activity", v ->
                startActivity(new Intent(this, FloatingBallVisibleActivity.class)));
        addActionButton(container, "Open Hidden Activity", v ->
                startActivity(new Intent(this, FloatingBallHiddenActivity.class)));

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

    private void addActionButton(LinearLayout container, String text, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        container.addView(button, params);
    }

    // 点击后执行完整回归：包含通道摘要、意图映射、skill 兼容性和运行用例。
    private void runAllSections() {
        StringBuilder report = new StringBuilder();

        try {
            AndroidToolManager manager = buildTestToolManager();
            SchemaSummary summary = buildSchemaSummary(manager);
            appendChannelSummary(report, manager, summary);
            appendIntentMappingChecks(report, summary);
            appendImSenderCompatibilityNote(report);
            appendRuntimeCases(report, manager);
        } catch (Exception e) {
            report.append("Unexpected test failure: ").append(e.getMessage()).append('\n');
        }

        outputView.setText(report.toString());
    }

    // 点击后只检查通道注册和 schema 摘要，确认当前暴露给模型的顶层工具定义。
    private void runChannelSummarySection() {
        StringBuilder report = new StringBuilder();
        try {
            AndroidToolManager manager = buildTestToolManager();
            SchemaSummary summary = buildSchemaSummary(manager);
            appendChannelSummary(report, manager, summary);
        } catch (Exception e) {
            report.append("Unexpected test failure: ").append(e.getMessage()).append('\n');
        }
        outputView.setText(report.toString());
    }

    // 点击后只展示典型用户意图与预期 channel/tool 的映射，用于人工判断提示是否清晰。
    private void runIntentMappingSection() {
        StringBuilder report = new StringBuilder();
        try {
            AndroidToolManager manager = buildTestToolManager();
            SchemaSummary summary = buildSchemaSummary(manager);
            appendIntentMappingChecks(report, summary);
        } catch (Exception e) {
            report.append("Unexpected test failure: ").append(e.getMessage()).append('\n');
        }
        outputView.setText(report.toString());
    }

    // 点击后只检查现有 skill 的兼容性说明，确认既有 skill 仍能沿旧通道工作。
    private void runSkillCompatibilitySection() {
        StringBuilder report = new StringBuilder();
        appendImSenderCompatibilityNote(report);
        outputView.setText(report.toString());
    }

    // 点击后只运行实际调用 case，验证兼容通道和手势通道的执行结果是否符合预期。
    private void runRuntimeCasesSection() {
        StringBuilder report = new StringBuilder();
        try {
            AndroidToolManager manager = buildTestToolManager();
            appendRuntimeCases(report, manager);
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

    private SchemaSummary buildSchemaSummary(AndroidToolManager manager) throws Exception {
        SchemaSummary summary = new SchemaSummary();
        JSONObject schema = new JSONObject(manager.generateToolsJsonString());
        JSONArray tools = schema.getJSONArray("tools");
        for (int i = 0; i < tools.length(); i++) {
            JSONObject function = tools.getJSONObject(i).getJSONObject("function");
            if ("call_android_tool".equals(function.optString("name"))) {
                summary.containsLegacyChannel = true;
                summary.legacyDescription = function.optString("description");
                JSONObject parameters = function.optJSONObject("parameters");
                if (parameters != null) {
                    JSONObject properties = parameters.optJSONObject("properties");
                    if (properties != null) {
                        JSONObject functionProperty = properties.optJSONObject("function");
                        JSONObject argsProperty = properties.optJSONObject("args");
                        summary.legacyFunctionDescription = functionProperty != null
                                ? functionProperty.optString("description")
                                : "";
                        summary.legacyArgsDescription = argsProperty != null
                                ? argsProperty.optString("description")
                                : "";
                    }
                }
            }
            if ("android_gesture_tool".equals(function.optString("name"))) {
                summary.containsGestureChannel = true;
                summary.gestureDescription = function.optString("description");
            }
        }
        return summary;
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

    private void appendChannelSummary(StringBuilder report,
                                      AndroidToolManager manager,
                                      SchemaSummary summary) {
        appendSectionHeader(report, "Channel Summary");
        report.append("Registered channels: ").append(manager.getRegisteredChannels().keySet()).append('\n');
        report.append("Gesture executor: ")
                .append(GestureExecutorRegistry.getExecutor().getClass().getSimpleName())
                .append('\n');
        report.append("Schema contains call_android_tool: ")
                .append(summary.containsLegacyChannel ? "PASS" : "FAIL")
                .append('\n');
        report.append("Schema contains android_gesture_tool: ")
                .append(summary.containsGestureChannel ? "PASS" : "FAIL")
                .append('\n');
        report.append("call_android_tool description: ")
                .append(summary.legacyDescription)
                .append('\n');
        report.append("call_android_tool.function description: ")
                .append(summary.legacyFunctionDescription)
                .append('\n');
        report.append("call_android_tool.args description: ")
                .append(summary.legacyArgsDescription)
                .append('\n');
        report.append("android_gesture_tool description: ")
                .append(summary.gestureDescription)
                .append("\n\n");
    }

    private void appendIntentMappingChecks(StringBuilder report, SchemaSummary summary) {
        appendSectionHeader(report, "Intent Mapping");

        appendIntentCheck(report,
                "给李四发消息说明天开会",
                "call_android_tool",
                "send_im_message",
                summary.legacyFunctionDescription,
                summary.legacyArgsDescription);
        appendIntentCheck(report,
                "查一下张三是不是联系人",
                "call_android_tool",
                "search_contacts",
                summary.legacyFunctionDescription,
                summary.legacyArgsDescription);
        appendIntentCheck(report,
                "看看剪贴板里是什么",
                "call_android_tool",
                "read_clipboard",
                summary.legacyFunctionDescription,
                summary.legacyArgsDescription);
        appendIntentCheck(report,
                "弹一个通知提醒我开会",
                "call_android_tool",
                "display_notification",
                summary.legacyFunctionDescription,
                summary.legacyArgsDescription);
        appendIntentCheck(report,
                "点击屏幕右下角按钮",
                "android_gesture_tool",
                "tap",
                summary.gestureDescription,
                "tap 需要 x/y；swipe 需要 startX/startY/endX/endY");
        appendIntentCheck(report,
                "从屏幕底部往上滑",
                "android_gesture_tool",
                "swipe",
                summary.gestureDescription,
                "tap 需要 x/y；swipe 需要 startX/startY/endX/endY");

        report.append('\n');
    }

    private void appendIntentCheck(StringBuilder report,
                                   String userIntent,
                                   String expectedChannel,
                                   String expectedToolOrAction,
                                   String schemaEvidence,
                                   String parameterEvidence) {
        report.append("intent: ").append(userIntent).append('\n');
        report.append("expected channel: ").append(expectedChannel).append('\n');
        report.append("expected tool/action: ").append(expectedToolOrAction).append('\n');
        report.append("schema evidence: ").append(schemaEvidence).append('\n');
        report.append("parameter evidence: ").append(parameterEvidence).append("\n\n");
    }

    private void appendImSenderCompatibilityNote(StringBuilder report) {
        appendSectionHeader(report, "IM Sender Compatibility");
        report.append("Skill path: app/src/main/assets/workspace/skills/im_sender/SKILL.md").append('\n');
        report.append("Expected channel: call_android_tool").append('\n');
        report.append("Expected functions: search_contacts, send_im_message").append('\n');
        report.append("Status: current schema still exposes both functions under call_android_tool, so the existing skill path remains valid.")
                .append("\n\n");
    }

    private void appendRuntimeCases(StringBuilder report, AndroidToolManager manager) throws Exception {
        appendSectionHeader(report, "Runtime Cases");

        runCase(
                report,
                manager,
                "Legacy success: search_contacts",
                "call_android_tool",
                new JSONObject()
                        .put("function", "search_contacts")
                        .put("args", new JSONObject().put("query", "李四")),
                "success");

        runCase(
                report,
                manager,
                "Legacy invalid args: missing function",
                "call_android_tool",
                new JSONObject().put("args", new JSONObject()),
                "invalid_args");

        runCase(
                report,
                manager,
                "Legacy missing tool",
                "call_android_tool",
                new JSONObject()
                        .put("function", "not_exists")
                        .put("args", new JSONObject()),
                "tool_not_found");

        runCase(
                report,
                manager,
                "Gesture mock success: tap",
                "android_gesture_tool",
                new JSONObject()
                        .put("action", "tap")
                        .put("x", 100)
                        .put("y", 200),
                "success");

        runCase(
                report,
                manager,
                "Gesture mock success: swipe",
                "android_gesture_tool",
                new JSONObject()
                        .put("action", "swipe")
                        .put("startX", 10)
                        .put("startY", 20)
                        .put("endX", 300)
                        .put("endY", 400)
                        .put("duration", 500),
                "success");

        runCase(
                report,
                manager,
                "Gesture invalid args: missing end coordinates",
                "android_gesture_tool",
                new JSONObject()
                        .put("action", "swipe")
                        .put("startX", 10)
                        .put("startY", 20),
                "invalid_args");
    }

    private void appendSectionHeader(StringBuilder report, String title) {
        report.append(title).append('\n');
        for (int i = 0; i < title.length(); i++) {
            report.append('-');
        }
        report.append("\n");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
