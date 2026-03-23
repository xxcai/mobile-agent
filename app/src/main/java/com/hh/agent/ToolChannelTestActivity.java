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
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.tool.DisplayNotificationTool;
import com.hh.agent.tool.ReadClipboardTool;
import com.hh.agent.tool.SearchContactsTool;
import com.hh.agent.tool.SendImMessageTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * App-level test entry retained as a separate launcher target.
 * Used to validate tool channel behavior without adding test hooks into agent-core/agent-android.
 */
public class ToolChannelTestActivity extends AppCompatActivity {

    private TextView outputView;

    private static final class SchemaSummary {
        boolean containsLegacyChannel;
        boolean containsGestureChannel;
        boolean containsViewContextChannel;
        String legacyDescription = "";
        String legacyFunctionDescription = "";
        String legacyArgsDescription = "";
        String gestureDescription = "";
        String viewContextDescription = "";
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
        addActionButton(container, "Run Contract Checks", v -> runContractChecksSection());
        addActionButton(container, "Run Runtime Cases", v -> runRuntimeCasesSection());
        addActionButton(container, "Run Routing Cases", v -> runRoutingCasesSection());
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

    private void runAllSections() {
        StringBuilder report = new StringBuilder();

        try {
            AndroidToolManager manager = buildTestToolManager();
            SchemaSummary summary = buildSchemaSummary(manager);
            appendChannelSummary(report, manager, summary);
            appendIntentMappingChecks(report, summary);
            appendImSenderCompatibilityNote(report);
            appendContractChecks(report, manager);
            appendRuntimeCases(report, manager);
            appendRoutingCases(report);
        } catch (Exception e) {
            report.append("Unexpected test failure: ").append(e.getMessage()).append('\n');
        }

        outputView.setText(report.toString());
    }

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

    private void runSkillCompatibilitySection() {
        StringBuilder report = new StringBuilder();
        appendImSenderCompatibilityNote(report);
        outputView.setText(report.toString());
    }

    private void runContractChecksSection() {
        StringBuilder report = new StringBuilder();
        try {
            AndroidToolManager manager = buildTestToolManager();
            appendContractChecks(report, manager);
        } catch (Exception e) {
            report.append("Unexpected test failure: ").append(e.getMessage()).append('\n');
        }
        outputView.setText(report.toString());
    }

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

    private void runRoutingCasesSection() {
        StringBuilder report = new StringBuilder();
        appendRoutingCases(report);
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
            if ("android_view_context_tool".equals(function.optString("name"))) {
                summary.containsViewContextChannel = true;
                summary.viewContextDescription = function.optString("description");
            }
        }
        return summary;
    }

    private void runCase(StringBuilder report,
                         AndroidToolManager manager,
                         String title,
                         String outerToolName,
                         String argumentsJson) {
        report.append("## ").append(title).append('\n');
        report.append("Input: ").append(outerToolName).append(" ").append(argumentsJson).append('\n');
        try {
            String raw = manager.callTool(outerToolName, argumentsJson);
            report.append("Output: ").append(raw).append('\n');
        } catch (Exception e) {
            report.append("Output: exception=").append(e.getMessage()).append('\n');
        }
        report.append('\n');
    }

    private void appendChannelSummary(StringBuilder report,
                                      AndroidToolManager manager,
                                      SchemaSummary summary) {
        report.append("# Channel Summary\n");
        report.append("registered_channels=").append(manager.getRegisteredChannels().keySet()).append('\n');
        report.append("legacy_present=").append(summary.containsLegacyChannel).append('\n');
        report.append("gesture_present=").append(summary.containsGestureChannel).append('\n');
        report.append("view_context_present=").append(summary.containsViewContextChannel).append('\n');
        report.append("legacy_description=").append(summary.legacyDescription).append('\n');
        report.append("legacy_function_description=").append(summary.legacyFunctionDescription).append('\n');
        report.append("legacy_args_description=").append(summary.legacyArgsDescription).append('\n');
        report.append("gesture_description=").append(summary.gestureDescription).append('\n');
        report.append("view_context_description=").append(summary.viewContextDescription).append("\n\n");
    }

    private void appendIntentMappingChecks(StringBuilder report, SchemaSummary summary) {
        report.append("# Intent Mapping\n");
        report.append("send message -> call_android_tool/send_im_message\n");
        report.append("search contact -> call_android_tool/search_contacts\n");
        report.append("read clipboard -> call_android_tool/read_clipboard\n");
        report.append("tap coordinates -> android_gesture_tool/tap\n");
        report.append("swipe screen -> android_gesture_tool/swipe\n");
        report.append("inspect current native screen -> android_view_context_tool/native_xml\n");
        report.append("legacy_summary_has_business_hint=")
                .append(summary.legacyDescription.contains("业务工具"))
                .append('\n');
        report.append("gesture_summary_has_coordinate_hint=")
                .append(summary.gestureDescription.contains("屏幕坐标"))
                .append('\n');
        report.append("view_context_has_perception_hint=")
                .append(summary.viewContextDescription.contains("视图上下文"))
                .append("\n\n");
    }

    private void appendImSenderCompatibilityNote(StringBuilder report) {
        report.append("# Skill Compatibility\n");
        report.append("Existing Android skills that still emit {function,args} remain compatible via call_android_tool.\n\n");
    }

    private void appendContractChecks(StringBuilder report, AndroidToolManager manager) throws Exception {
        report.append("# Contract Checks\n");
        JSONObject schema = new JSONObject(manager.generateToolsJsonString());
        report.append("schema_version=").append(schema.optInt("version", -1)).append('\n');
        report.append("tool_count=").append(schema.getJSONArray("tools").length()).append('\n');
        report.append("registered_tools=").append(manager.getRegisteredTools().keySet()).append("\n\n");
    }

    private void appendRuntimeCases(StringBuilder report, AndroidToolManager manager) {
        report.append("# Runtime Cases\n");

        runCase(report, manager,
                "Business Tool Success",
                "call_android_tool",
                "{\"function\":\"search_contacts\",\"args\":{\"query\":\"张三\"}}");

        runCase(report, manager,
                "Business Tool Missing Function",
                "call_android_tool",
                "{\"function\":\"\",\"args\":{}}");

        runCase(report, manager,
                "Gesture Tap",
                "android_gesture_tool",
                "{\"action\":\"tap\",\"x\":120,\"y\":360}");

        runCase(report, manager,
                "Gesture Swipe",
                "android_gesture_tool",
                "{\"action\":\"swipe\",\"startX\":200,\"startY\":800,\"endX\":200,\"endY\":300,\"duration\":400}");

        runCase(report, manager,
                "Gesture Unsupported",
                "android_gesture_tool",
                "{\"action\":\"pinch\"}");

        runCase(report, manager,
                "View Context Native XML",
                "android_view_context_tool",
                "{\"source\":\"native_xml\",\"targetHint\":\"发送按钮\"}");

        runCase(report, manager,
                "View Context All Sources",
                "android_view_context_tool",
                "{\"source\":\"all\",\"targetHint\":\"第二个卡片\",\"includeMockWebDom\":true,\"includeMockScreenshot\":true}");

        report.append("active_gesture_executor=")
                .append(GestureExecutorRegistry.getExecutor().getClass().getSimpleName())
                .append('\n');
    }

    private void appendRoutingCases(StringBuilder report) {
        report.append("# Routing Cases\n");
        report.append("CASE: 给张三发消息说明天开会\n");
        report.append("EXPECTED: call_android_tool first; no view_context first; no direct gesture\n\n");

        report.append("CASE: 点击第二个卡片\n");
        report.append("EXPECTED: android_view_context_tool first; gesture only after screen inspection\n\n");

        report.append("CASE: 看看当前页面结构\n");
        report.append("EXPECTED: android_view_context_tool directly; prefer source=native_xml\n\n");

        report.append("CASE: 点右上角那个红点\n");
        report.append("EXPECTED: android_view_context_tool first; do not guess coordinates before inspection\n\n");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
