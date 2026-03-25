package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.gesture.GestureExecutorRegistry;
import com.hh.agent.core.api.impl.NativeMobileAgentApi;
import com.hh.agent.core.event.AgentEventListener;
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

    private static final String PROBE_SESSION_KEY_PREFIX = "native:route-probe";
    private static final String BUILTIN_READ_FILE_TOOL = "read_file";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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

        addSectionHeader(container, "Core Runtime");
        addActionButton(container, "Run Runtime Cases", v -> runRuntimeCasesSection());
        addActionButton(container, "Run Live View Context", v -> runLiveViewContextProbe(false));
        addActionButton(container, "Run Container View Context", v -> runLiveViewContextProbe(true));
        addActionButton(container, "Run Observation Bound Gesture", v -> runObservationBoundGestureProbe());
        addActionButton(container, "Run Business Fallback Linkage", v -> runBusinessFallbackLinkageProbe());

        addSectionHeader(container, "Touch Injection");
        addActionButton(container, "Open Touch Dispatch Probe",
                v -> startActivity(new Intent(this, TouchDispatchProbeActivity.class)));
        addActionButton(container, "Open Recycler Swipe Probe",
                v -> startActivity(new Intent(this, RecyclerViewSwipeProbeActivity.class)));
        addActionButton(container, "Open Long Press / Double Tap Probe",
                v -> startActivity(new Intent(this, LongPressDoubleTapProbeActivity.class)));

        addSectionHeader(container, "LLM Routing");
        addActionButton(container, "Probe LLM Business Route", v ->
                runLlmRouteProbe(
                        "给张三发消息说明天开会",
                        "call_android_tool",
                        null));
        addActionButton(container, "Probe LLM Contact Route", v ->
                runLlmRouteProbe(
                        "点击张三",
                        "android_view_context_tool",
                        "android_gesture_tool"));
        addActionButton(container, "Probe LLM Send Button Route", v ->
                runLlmRouteProbe(
                        "点击发送消息按钮",
                        "android_view_context_tool",
                        "android_gesture_tool"));
        addActionButton(container, "Probe LLM Structure Route", v ->
                runLlmRouteProbe(
                        "看看当前页面结构",
                        "android_view_context_tool",
                        null));

        addSectionHeader(container, "Legacy Diagnostics");
        addActionButton(container, "Run Channel Summary", v -> runChannelSummarySection());
        addActionButton(container, "Run Intent Mapping", v -> runIntentMappingSection());
        addActionButton(container, "Run Skill Compatibility", v -> runSkillCompatibilitySection());
        addActionButton(container, "Run Contract Checks", v -> runContractChecksSection());

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

    private void addSectionHeader(LinearLayout container, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(18);
        header.setPadding(0, dp(12), 0, dp(8));
        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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

    private void runLiveViewContextProbe(boolean launchContainerFirst) {
        StringBuilder report = new StringBuilder();
        report.append("# Live View Context Probe\n");
        report.append("launch_container_first=").append(launchContainerFirst).append('\n');
        report.append("source=native_xml\n");
        report.append("target_hint=发送消息按钮\n");
        report.append("expected_mock=false\n");
        report.append("expected_activity=")
                .append(launchContainerFirst ? getClass().getName() : getClass().getName())
                .append('\n');
        if (launchContainerFirst) {
            report.append("expected_behavior=ContainerActivity closes before dump\n");
        }
        report.append('\n');
        outputView.setText(report.toString());

        if (launchContainerFirst) {
            startActivity(new Intent(this, ContainerActivity.class));
        }

        mainHandler.postDelayed(() -> {
            Thread worker = new Thread(() -> {
                String resultJson;
                try {
                    AndroidToolManager manager = buildTestToolManager();
                    resultJson = manager.callTool(
                            "android_view_context_tool",
                            "{\"source\":\"native_xml\",\"targetHint\":\"发送消息按钮\"}");
                } catch (Exception e) {
                    resultJson = "{\"success\":false,\"error\":\"probe_failed\",\"message\":\""
                            + e.getMessage() + "\"}";
                }
                final String finalResultJson = resultJson;
                mainHandler.post(() -> appendLiveViewContextReport(finalResultJson));
            });
            worker.start();
        }, launchContainerFirst ? 300L : 0L);
    }

    private void appendLiveViewContextReport(String resultJson) {
        StringBuilder next = new StringBuilder(outputView.getText());
        next.append("raw_result=").append(resultJson).append("\n\n");
        try {
            JSONObject json = new JSONObject(resultJson);
            boolean success = json.optBoolean("success", false);
            next.append("success=").append(success).append('\n');
            if (!success) {
                next.append("error=").append(json.optString("error")).append('\n');
                next.append("message=").append(json.optString("message")).append('\n');
            } else {
                next.append("mock=").append(json.optBoolean("mock", true)).append('\n');
                next.append("activityClassName=").append(json.optString("activityClassName", "<none>")).append('\n');
                next.append("snapshotId=").append(json.optString("snapshotId", "<none>")).append('\n');
                next.append("snapshotCreatedAtEpochMs=")
                        .append(json.optLong("snapshotCreatedAtEpochMs", -1L))
                        .append('\n');
                next.append("snapshotScope=").append(json.optString("snapshotScope", "<none>")).append('\n');
                next.append("snapshotCurrentTurnOnly=")
                        .append(json.optBoolean("snapshotCurrentTurnOnly", false))
                        .append('\n');
                String nativeViewXml = json.optString("nativeViewXml", "");
                next.append("nativeViewXml_length=").append(nativeViewXml.length()).append('\n');
                next.append("has_send_button_text=").append(nativeViewXml.contains("发送消息")).append('\n');
                next.append("has_tool_channel_button_text=").append(nativeViewXml.contains("RUN LIVE VIEW CONTEXT")).append('\n');
                next.append("observationMode=").append(json.optString("observationMode", "<none>")).append('\n');
            }
        } catch (Exception e) {
            next.append("parse_error=").append(e.getMessage()).append('\n');
        }
        outputView.setText(next.toString());
    }

    private void runObservationBoundGestureProbe() {
        StringBuilder report = new StringBuilder();
        report.append("# Observation Bound Gesture Probe\n");
        report.append("step_1=android_view_context_tool(native_xml)\n");
        report.append("step_2=android_gesture_tool(tap + observation + activity touch injection)\n");
        report.append("target_hint=发送消息按钮\n\n");
        outputView.setText(report.toString());

        Thread worker = new Thread(() -> {
            try {
                AndroidToolManager manager = buildTestToolManager();
                String viewContextResult = manager.callTool(
                        "android_view_context_tool",
                        "{\"source\":\"native_xml\",\"targetHint\":\"发送消息按钮\"}");
                JSONObject viewContextJson = new JSONObject(viewContextResult);
                if (!viewContextJson.optBoolean("success", false)) {
                    mainHandler.post(() -> appendObservationBoundGestureReport(
                            viewContextResult,
                            null,
                            null));
                    return;
                }

                String snapshotId = viewContextJson.optString("snapshotId", "");
                String gestureArgs = "{\"action\":\"tap\",\"x\":120,\"y\":360,"
                        + "\"observation\":{\"snapshotId\":\"" + snapshotId + "\","
                        + "\"targetDescriptor\":\"发送消息按钮\"}}";
                String gestureResult = manager.callTool("android_gesture_tool", gestureArgs);

                mainHandler.post(() -> appendObservationBoundGestureReport(
                        viewContextResult,
                        gestureArgs,
                        gestureResult));
            } catch (Exception e) {
                String errorJson = "{\"success\":false,\"error\":\"probe_failed\",\"message\":\""
                        + e.getMessage() + "\"}";
                mainHandler.post(() -> appendObservationBoundGestureReport(
                        errorJson,
                        null,
                        null));
            }
        });
        worker.start();
    }

    private void runBusinessFallbackLinkageProbe() {
        StringBuilder report = new StringBuilder();
        report.append("# Business Fallback Linkage Probe\n");
        report.append("step_1=call_android_tool(send_im_message -> business_target_not_accessible)\n");
        report.append("step_2=android_view_context_tool(native_xml)\n");
        report.append("step_3=android_gesture_tool(tap + observation + activity touch injection)\n");
        report.append("target_hint=发送\n\n");
        outputView.setText(report.toString());

        Thread worker = new Thread(() -> {
            try {
                AndroidToolManager manager = buildTestToolManager();
                String businessArgs = "{\"function\":\"send_im_message\","
                        + "\"args\":{\"contact_id\":\"ui_current_chat\",\"message\":\"test\"}}";
                String businessResult = manager.callTool("call_android_tool", businessArgs);

                String viewContextResult = manager.callTool(
                        "android_view_context_tool",
                        "{\"source\":\"native_xml\",\"targetHint\":\"发送\"}");
                JSONObject viewContextJson = new JSONObject(viewContextResult);
                if (!viewContextJson.optBoolean("success", false)) {
                    mainHandler.post(() -> appendBusinessFallbackLinkageReport(
                            businessArgs,
                            businessResult,
                            viewContextResult,
                            null,
                            null));
                    return;
                }

                String snapshotId = viewContextJson.optString("snapshotId", "");
                String gestureArgs = "{\"action\":\"tap\",\"x\":960,\"y\":2200,"
                        + "\"observation\":{\"snapshotId\":\"" + snapshotId + "\","
                        + "\"targetDescriptor\":\"发送\"}}";
                String gestureResult = manager.callTool("android_gesture_tool", gestureArgs);

                mainHandler.post(() -> appendBusinessFallbackLinkageReport(
                        businessArgs,
                        businessResult,
                        viewContextResult,
                        gestureArgs,
                        gestureResult));
            } catch (Exception e) {
                String errorJson = "{\"success\":false,\"error\":\"probe_failed\",\"message\":\""
                        + e.getMessage() + "\"}";
                mainHandler.post(() -> appendBusinessFallbackLinkageReport(
                        null,
                        errorJson,
                        null,
                        null,
                        null));
            }
        });
        worker.start();
    }

    private void appendObservationBoundGestureReport(String viewContextResult,
                                                     String gestureArgs,
                                                     String gestureResult) {
        StringBuilder next = new StringBuilder(outputView.getText());
        next.append("view_context_result=").append(viewContextResult).append("\n\n");

        try {
            JSONObject viewContextJson = new JSONObject(viewContextResult);
            String snapshotId = viewContextJson.optString("snapshotId", "<none>");
            next.append("snapshotId=").append(snapshotId).append('\n');
            if (gestureArgs != null) {
                next.append("gesture_input=").append(gestureArgs).append('\n');
            }
            if (gestureResult != null) {
                next.append("gesture_result=").append(gestureResult).append("\n\n");
                JSONObject gestureJson = new JSONObject(gestureResult);
                JSONObject paramsJson = gestureJson.optJSONObject("params");
                JSONObject observationJson = paramsJson != null
                        ? paramsJson.optJSONObject("observation")
                        : null;
                String referencedSnapshotId = observationJson != null
                        ? observationJson.optString("snapshotId", "<none>")
                        : "<none>";
                String resolvedViewText = paramsJson != null
                        ? paramsJson.optString("resolvedViewText", "<none>")
                        : "<none>";
                boolean snapshotMatch = snapshotId.equals(referencedSnapshotId);
                boolean dispatchHandled = paramsJson != null
                        && paramsJson.optBoolean("dispatchAnyHandled", false);
                boolean targetMatch = resolvedViewText.contains("发送");
                next.append("gesture_observation_snapshot_id=")
                        .append(referencedSnapshotId)
                        .append('\n');
                next.append("snapshot_match=")
                        .append(snapshotMatch ? "PASS" : "FAIL")
                        .append('\n');
                next.append("gesture_resolved_view_text=")
                        .append(resolvedViewText)
                        .append('\n');
                next.append("gesture_target_match=")
                        .append(targetMatch ? "PASS" : "FAIL")
                        .append('\n');
                next.append("gesture_dispatch_path=")
                        .append(paramsJson != null
                                ? paramsJson.optString("dispatchPath", "<none>")
                                : "<none>")
                        .append('\n');
                next.append("gesture_dispatch_any_handled=")
                        .append(dispatchHandled)
                        .append('\n');
                next.append("probe_result=")
                        .append(snapshotMatch && dispatchHandled && targetMatch ? "PASS" : "FAIL")
                        .append('\n');
            }
        } catch (Exception e) {
            next.append("parse_error=").append(e.getMessage()).append('\n');
        }

        outputView.setText(next.toString());
    }

    private void appendBusinessFallbackLinkageReport(String businessArgs,
                                                     String businessResult,
                                                     String viewContextResult,
                                                     String gestureArgs,
                                                     String gestureResult) {
        StringBuilder next = new StringBuilder(outputView.getText());
        if (businessArgs != null) {
            next.append("business_input=").append(businessArgs).append('\n');
        }
        next.append("business_result=").append(businessResult).append("\n\n");

        try {
            JSONObject businessJson = new JSONObject(businessResult);
            next.append("business_error=").append(businessJson.optString("error", "<none>")).append('\n');
            next.append("business_suggested_next_tool=")
                    .append(businessJson.optString("suggestedNextTool", "<none>"))
                    .append('\n');
        } catch (Exception e) {
            next.append("business_parse_error=").append(e.getMessage()).append('\n');
        }

        if (viewContextResult != null) {
            next.append('\n').append("view_context_result=").append(viewContextResult).append("\n\n");
        }
        if (gestureArgs != null) {
            next.append("gesture_input=").append(gestureArgs).append('\n');
        }
        if (gestureResult != null) {
            next.append("gesture_result=").append(gestureResult).append("\n\n");
            try {
                JSONObject gestureJson = new JSONObject(gestureResult);
                JSONObject paramsJson = gestureJson.optJSONObject("params");
                next.append("gesture_tap_point_source=")
                        .append(paramsJson != null
                                ? paramsJson.optString("tapPointSource", "<none>")
                                : "<none>")
                        .append('\n');
                next.append("gesture_dispatch_path=")
                        .append(paramsJson != null
                                ? paramsJson.optString("dispatchPath", "<none>")
                                : "<none>")
                        .append('\n');
                next.append("gesture_dispatch_trace=")
                        .append(paramsJson != null
                                ? paramsJson.optString("dispatchTrace", "<none>")
                                : "<none>")
                        .append('\n');
            } catch (Exception e) {
                next.append("gesture_parse_error=").append(e.getMessage()).append('\n');
            }
        }

        outputView.setText(next.toString());
    }

    private void runLlmRouteProbe(String prompt,
                                  String expectedFirstTool,
                                  String expectedSecondTool) {
        String probeSessionKey = buildIsolatedProbeSessionKey();

        StringBuilder report = new StringBuilder();
        report.append("# LLM Route Probe\n");
        report.append("prompt=").append(prompt).append('\n');
        report.append("probe_session_key=").append(probeSessionKey).append('\n');
        report.append('\n');
        report.append("expected_first_route_tool=").append(expectedFirstTool).append('\n');
        report.append("expected_second_route_tool=")
                .append(expectedSecondTool == null ? "<none>" : expectedSecondTool)
                .append('\n');
        report.append("note=probe uses a dedicated session key for this run\n\n");
        outputView.setText(report.toString());

        final boolean[] firstToolCaptured = {false};
        final int[] routeToolCount = {0};

        NativeMobileAgentApi.getInstance().sendMessageStream(prompt, probeSessionKey, new AgentEventListener() {
            @Override
            public void onTextDelta(String text) {
            }

            @Override
            public void onToolUse(String id, String name, String argumentsJson) {
                if (firstToolCaptured[0]) {
                    // Keep listening for the first route tool even after the first tool is captured.
                } else {
                    firstToolCaptured[0] = true;
                    mainHandler.post(() -> {
                        StringBuilder next = new StringBuilder(outputView.getText());
                        next.append("first_tool_id=").append(id).append('\n');
                        next.append("first_tool_name=").append(name).append('\n');
                        next.append("first_tool_arguments=").append(argumentsJson).append("\n\n");
                        outputView.setText(next.toString());
                    });
                }

                if (isRouteTool(name)) {
                    routeToolCount[0]++;
                    int currentRouteIndex = routeToolCount[0];
                    if (currentRouteIndex <= 2) {
                        mainHandler.post(() -> appendObservedRouteTool(
                                id,
                                name,
                                argumentsJson,
                                currentRouteIndex,
                                expectedFirstTool,
                                expectedSecondTool));
                    }
                }
            }

            @Override
            public void onToolResult(String id, String result) {
            }

            @Override
            public void onMessageEnd(String finishReason) {
                mainHandler.post(() -> {
                    StringBuilder next = new StringBuilder(outputView.getText());
                    next.append("finish_reason=").append(finishReason).append('\n');
                    if (!firstToolCaptured[0]) {
                        next.append("first_tool_name=<none>\n");
                    }
                    if (routeToolCount[0] < 1) {
                        next.append("first_route_tool_name=<none>\n");
                        next.append("first_route_match=FAIL\n");
                    }
                    if (expectedSecondTool != null && routeToolCount[0] < 2) {
                        next.append("second_route_tool_name=<none>\n");
                        next.append("second_route_match=FAIL\n");
                    }
                    outputView.setText(next.toString());
                });
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                mainHandler.post(() -> {
                    StringBuilder next = new StringBuilder(outputView.getText());
                    next.append("error_code=").append(errorCode).append('\n');
                    next.append("error_message=").append(errorMessage).append('\n');
                    outputView.setText(next.toString());
                });
            }
        });
    }

    private void appendObservedRouteTool(String id,
                                         String name,
                                         String argumentsJson,
                                         int routeIndex,
                                         String expectedFirstTool,
                                         String expectedSecondTool) {
        StringBuilder next = new StringBuilder(outputView.getText());
        if (routeIndex == 1) {
            next.append("first_route_tool_id=").append(id).append('\n');
            next.append("first_route_tool_name=").append(name).append('\n');
            next.append("first_route_match=")
                    .append(expectedFirstTool.equals(name) ? "PASS" : "FAIL")
                    .append('\n');
            next.append("first_route_tool_arguments=").append(argumentsJson).append("\n\n");
        } else if (routeIndex == 2) {
            next.append("second_route_tool_id=").append(id).append('\n');
            next.append("second_route_tool_name=").append(name).append('\n');
            next.append("second_route_match=")
                    .append(expectedSecondTool != null && expectedSecondTool.equals(name) ? "PASS" : "FAIL")
                    .append('\n');
            next.append("second_route_tool_arguments=").append(argumentsJson).append("\n\n");
        }
        outputView.setText(next.toString());
    }

    private boolean isRouteTool(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        if (BUILTIN_READ_FILE_TOOL.equals(toolName)) {
            return false;
        }
        return "call_android_tool".equals(toolName)
                || "android_view_context_tool".equals(toolName)
                || "android_gesture_tool".equals(toolName);
    }

    private String buildIsolatedProbeSessionKey() {
        return PROBE_SESSION_KEY_PREFIX + ":" + System.currentTimeMillis();
    }

    private AndroidToolManager buildTestToolManager() {
        AndroidToolManager manager = new AndroidToolManager(this);
        manager.registerTools(buildTestTools());
        manager.initialize();
        return manager;
    }

    private Map<String, ToolExecutor> buildTestTools() {
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("display_notification", new DisplayNotificationTool(this));
        tools.put("read_clipboard", new ReadClipboardTool(this));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());
        return tools;
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
        report.append("registered_tools=").append(manager.getRegisteredTools().keySet()).append('\n');

        JSONObject gestureFunction = findFunction(schema.getJSONArray("tools"), "android_gesture_tool");
        report.append("gesture_tool_present=").append(gestureFunction != null ? "PASS" : "FAIL").append('\n');
        if (gestureFunction == null) {
            report.append('\n');
            return;
        }

        JSONObject gestureParameters = gestureFunction.optJSONObject("parameters");
        JSONObject gestureProperties = gestureParameters != null
                ? gestureParameters.optJSONObject("properties")
                : null;
        JSONArray gestureRequired = gestureParameters != null
                ? gestureParameters.optJSONArray("required")
                : null;
        JSONObject actionProperty = gestureProperties != null
                ? gestureProperties.optJSONObject("action")
                : null;
        JSONObject directionProperty = gestureProperties != null
                ? gestureProperties.optJSONObject("direction")
                : null;
        JSONObject amountProperty = gestureProperties != null
                ? gestureProperties.optJSONObject("amount")
                : null;
        JSONObject observationProperty = gestureProperties != null
                ? gestureProperties.optJSONObject("observation")
                : null;
        JSONObject observationProperties = observationProperty != null
                ? observationProperty.optJSONObject("properties")
                : null;

        report.append("gesture_action_required=")
                .append(asPassFail(arrayContains(gestureRequired, "action")))
                .append('\n');
        report.append("gesture_action_enum_has_tap_swipe=")
                .append(asPassFail(arrayContains(actionProperty != null ? actionProperty.optJSONArray("enum") : null, "tap")
                        && arrayContains(actionProperty != null ? actionProperty.optJSONArray("enum") : null, "swipe")))
                .append('\n');
        report.append("gesture_direction_enum_has_down_up=")
                .append(asPassFail(arrayContains(directionProperty != null ? directionProperty.optJSONArray("enum") : null, "down")
                        && arrayContains(directionProperty != null ? directionProperty.optJSONArray("enum") : null, "up")))
                .append('\n');
        report.append("gesture_amount_default_medium=")
                .append(asPassFail("medium".equals(amountProperty != null
                        ? amountProperty.optString("default", "")
                        : "")))
                .append('\n');
        report.append("gesture_observation_has_snapshot_id=")
                .append(asPassFail(observationProperties != null && observationProperties.has("snapshotId")))
                .append('\n');
        report.append("gesture_observation_has_referenced_bounds=")
                .append(asPassFail(observationProperties != null && observationProperties.has("referencedBounds")))
                .append("\n\n");
    }

    private JSONObject findFunction(JSONArray tools, String functionName) throws Exception {
        for (int index = 0; index < tools.length(); index++) {
            JSONObject tool = tools.getJSONObject(index);
            JSONObject function = tool.optJSONObject("function");
            if (function == null) {
                continue;
            }
            if (functionName.equals(function.optString("name"))) {
                return function;
            }
        }
        return null;
    }

    private boolean arrayContains(JSONArray array, String expected) {
        if (array == null || expected == null) {
            return false;
        }
        for (int index = 0; index < array.length(); index++) {
            if (expected.equals(array.optString(index, null))) {
                return true;
            }
        }
        return false;
    }

    private String asPassFail(boolean passed) {
        return passed ? "PASS" : "FAIL";
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
                "Business Capability Not Supported",
                "call_android_tool",
                "{\"function\":\"open_current_chat_send_button\",\"args\":{}}");

        runCase(report, manager,
                "Business Target Not Accessible",
                "call_android_tool",
                "{\"function\":\"send_im_message\",\"args\":{\"contact_id\":\"ui_current_chat\",\"message\":\"test\"}}");

        runCase(report, manager,
                "Gesture Tap",
                "android_gesture_tool",
                "{\"action\":\"tap\",\"x\":120,\"y\":360,\"allowCoordinateFallback\":true}");

        runCase(report, manager,
                "Gesture Tap Missing Observation",
                "android_gesture_tool",
                "{\"action\":\"tap\",\"x\":120,\"y\":360}");

        runCase(report, manager,
                "Gesture Swipe",
                "android_gesture_tool",
                "{\"action\":\"swipe\",\"direction\":\"down\",\"scope\":\"feed\",\"amount\":\"medium\",\"duration\":400}");

        runCase(report, manager,
                "Gesture Tap With Observation",
                "android_gesture_tool",
                "{\"action\":\"tap\",\"x\":120,\"y\":360,"
                        + "\"observation\":{\"snapshotId\":\"obs_test_123\","
                        + "\"targetNodeIndex\":7,"
                        + "\"targetDescriptor\":\"发送消息按钮\","
                        + "\"referencedBounds\":\"[24,640][240,720]\"}}");

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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
