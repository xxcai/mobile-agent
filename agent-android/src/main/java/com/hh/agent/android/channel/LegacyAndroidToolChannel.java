package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backward-compatible channel that keeps the existing call_android_tool protocol:
 * {"function":"tool_name","args":{...}}
 */
public class LegacyAndroidToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "call_android_tool";
    private static final Pattern FUNCTION_PATTERN =
            Pattern.compile("\"function\"\\s*:\\s*\"([^\"]+)\"");

    private final Map<String, ToolExecutor> tools;

    public LegacyAndroidToolChannel(Map<String, ToolExecutor> tools) {
        this.tools = tools;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        String functionChoicesDescription = buildFunctionChoicesDescription();
        String argsDescription = buildArgsDescription();
        return ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "调用宿主 App 已注册的业务工具。适用于联系人、消息、通知、剪贴板等业务能力。"
                                + "协议固定为 {\"function\":\"工具名\",\"args\":{...}}。"
                                + "不要用这个通道做屏幕坐标点击或滑动，这类手势应使用 android_gesture_tool。")
                .property("function", ToolSchemaBuilder.string()
                        .description(functionChoicesDescription)
                        .enumValues(getToolNames()), true)
                .property("args", ToolSchemaBuilder.object()
                        .description(argsDescription), true)
                .build();
    }

    private String[] getToolNames() {
        return tools.values().stream()
                .map(ToolExecutor::getName)
                .toArray(String[]::new);
    }

    private String buildFunctionChoicesDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("要调用的业务工具名称，只能从 enum 列表中选择。")
                .append("按用户意图选择最匹配的工具：\n");

        for (Map.Entry<String, ToolExecutor> entry : tools.entrySet()) {
            String toolName = entry.getKey();
            ToolDefinition definition = entry.getValue().getDefinition();
            String toolDescription = definition.getDescription() != null
                    ? definition.getDescription()
                    : definition.getTitle();
            builder.append("- ").append(toolName)
                    .append(": ").append(toolDescription);
            if (!definition.getIntentExamples().isEmpty()) {
                builder.append("；常见意图：")
                        .append(String.join(" / ", definition.getIntentExamples()));
            }
            builder.append('\n');
        }

        return builder.toString().trim();
    }

    private String buildArgsDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("传给 function 的 JSON 参数对象。args 的字段结构由所选 function 决定。")
                .append("最小可用样例如下：\n");

        for (Map.Entry<String, ToolExecutor> entry : tools.entrySet()) {
            String toolName = entry.getKey();
            ToolDefinition definition = entry.getValue().getDefinition();
            builder.append("- ").append(toolName)
                    .append(": schema=")
                    .append(definition.getArgsSchema())
                    .append("；example=")
                    .append(definition.getArgsExample())
                    .append('\n');
        }

        return builder.toString().trim();
    }

    @Override
    public ToolResult execute(JSONObject params) {
        try {
            String functionName = params.optString("function", "").trim();
            if (functionName.isEmpty()) {
                return buildError("invalid_args", "call_android_tool requires a non-empty 'function' field");
            }

            JSONObject args = params.optJSONObject("args");
            if (args == null) {
                return buildError("invalid_args", "call_android_tool requires an 'args' object");
            }

            ToolExecutor executor = tools.get(functionName);
            if (executor == null) {
                return ToolResult.error("business_capability_not_supported",
                                "Business tool '" + functionName + "' is not supported")
                        .with("channel", CHANNEL_NAME)
                        .with("requestedFunction", functionName)
                        .with("fallbackSuggested", true)
                        .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                        .with("suggestedSource", "native_xml");
            }

            ToolResult innerResult = executor.execute(args);
            return decorateBusinessFailure(innerResult, functionName);
        } catch (Exception e) {
            return buildError("execution_failed", e.getMessage());
        }
    }

    @Override
    public boolean shouldExposeInnerToolInToolUi() {
        return true;
    }

    @Override
    public ToolUiDecision resolveInnerToolUiDecision(String argumentsJson) {
        String functionName = extractFunctionName(argumentsJson);
        if (functionName == null || functionName.isEmpty()) {
            return ToolUiDecision.hidden();
        }
        ToolExecutor executor = tools.get(functionName);
        if (executor == null) {
            return ToolUiDecision.hidden();
        }
        ToolDefinition definition = executor.getDefinition();
        if (definition == null) {
            return ToolUiDecision.hidden();
        }
        return ToolUiDecision.visible(definition.getTitle(), definition.getDescription());
    }

    private String extractFunctionName(String argumentsJson) {
        String normalized = normalizeArgumentsJson(argumentsJson);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        Matcher matcher = FUNCTION_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        String functionName = matcher.group(1);
        if (functionName == null) {
            return null;
        }
        String trimmed = functionName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeArgumentsJson(String argumentsJson) {
        if (argumentsJson == null) {
            return null;
        }
        String normalized = argumentsJson.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() >= 2
                && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return normalized;
    }

    private ToolResult buildError(String errorCode, String message) {
        return ToolResult.error(errorCode, message);
    }

    private ToolResult decorateBusinessFailure(ToolResult result, String functionName) {
        if (result == null) {
            return ToolResult.error("execution_failed", "Business tool returned null result")
                    .with("channel", CHANNEL_NAME)
                    .with("requestedFunction", functionName);
        }

        String json = result.toJsonString();
        if (!json.contains("\"success\":false")) {
            return result;
        }

        if (json.contains("\"error\":\"business_capability_not_supported\"")
                || json.contains("\"error\":\"business_target_not_accessible\"")) {
            return result
                    .with("channel", CHANNEL_NAME)
                    .with("requestedFunction", functionName)
                    .with("fallbackSuggested", true)
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("suggestedSource", "native_xml");
        }
        return result;
    }
}
