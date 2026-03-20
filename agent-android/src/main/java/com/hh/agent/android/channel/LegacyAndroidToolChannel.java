package com.hh.agent.android.channel;

import com.hh.agent.core.ToolDefinition;
import com.hh.agent.core.ToolExecutor;

import org.json.JSONArray;
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
        JSONArray toolNames = new JSONArray();
        String functionChoicesDescription = buildFunctionChoicesDescription();
        String argsDescription = buildArgsDescription();

        for (Map.Entry<String, ToolExecutor> entry : tools.entrySet()) {
            ToolExecutor executor = entry.getValue();
            String toolName = executor.getName();
            toolNames.put(toolName);
        }

        JSONObject functionObj = new JSONObject();
        functionObj.put("name", CHANNEL_NAME);
        functionObj.put("description",
                "调用宿主 App 已注册的业务工具。适用于联系人、消息、通知、剪贴板等业务能力。"
                        + "协议固定为 {\"function\":\"工具名\",\"args\":{...}}。"
                        + "不要用这个通道做屏幕坐标点击或滑动，这类手势应使用 android_gesture_tool。");

        JSONObject params = new JSONObject();
        params.put("type", "object");

        JSONObject properties = new JSONObject();

        JSONObject functionParam = new JSONObject();
        functionParam.put("type", "string");
        functionParam.put("description", functionChoicesDescription);
        functionParam.put("enum", toolNames);
        properties.put("function", functionParam);

        JSONObject argsParam = new JSONObject();
        argsParam.put("type", "object");
        argsParam.put("description", argsDescription);
        properties.put("args", argsParam);

        params.put("properties", properties);

        JSONArray required = new JSONArray();
        required.put("function");
        required.put("args");
        params.put("required", required);

        functionObj.put("parameters", params);

        return new JSONObject()
                .put("type", "function")
                .put("function", functionObj);
    }

    private String buildFunctionChoicesDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("要调用的业务工具名称，只能从 enum 列表中选择。")
                .append("按用户意图选择最匹配的工具：\n");

        for (Map.Entry<String, ToolExecutor> entry : tools.entrySet()) {
            String toolName = entry.getKey();
            ToolDefinition definition = entry.getValue().getDefinition();
            builder.append("- ").append(toolName)
                    .append(": ").append(definition.getSummary())
                    .append("；常见意图：")
                    .append(String.join(" / ", definition.getIntentExamples()))
                    .append('\n');
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
    public String execute(JSONObject params) {
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
                return buildError("tool_not_found", "Tool '" + functionName + "' not found");
            }

            return executor.execute(args);
        } catch (Exception e) {
            return buildError("execution_failed", e.getMessage());
        }
    }

    @Override
    public boolean shouldExposeInnerToolInToolUi() {
        return true;
    }

    @Override
    public String resolveInnerToolDisplayName(String argumentsJson) {
        String normalizedArguments = normalizeArgumentsJson(argumentsJson);
        if (normalizedArguments == null || normalizedArguments.isEmpty()) {
            return null;
        }
        Matcher matcher = FUNCTION_PATTERN.matcher(normalizedArguments);
        if (!matcher.find()) {
            return null;
        }
        String functionName = matcher.group(1);
        if (functionName == null) {
            return null;
        }
        String normalized = functionName.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private String buildError(String errorCode, String message) {
        try {
            return new JSONObject()
                    .put("success", false)
                    .put("error", errorCode)
                    .put("message", message)
                    .toString();
        } catch (Exception ignored) {
            return "{\"success\":false,\"error\":\"execution_failed\"}";
        }
    }
}
