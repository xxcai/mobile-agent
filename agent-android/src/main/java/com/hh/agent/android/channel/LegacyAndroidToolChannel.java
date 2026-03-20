package com.hh.agent.android.channel;

import com.hh.agent.core.ToolExecutor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Backward-compatible channel that keeps the existing call_android_tool protocol:
 * {"function":"tool_name","args":{...}}
 */
public class LegacyAndroidToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "call_android_tool";

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
        StringBuilder descriptionBuilder = new StringBuilder("调用 Android 设备功能。可用功能:\n");
        JSONArray toolNames = new JSONArray();

        for (Map.Entry<String, ToolExecutor> entry : tools.entrySet()) {
            ToolExecutor executor = entry.getValue();
            String toolName = executor.getName();
            toolNames.put(toolName);

            descriptionBuilder.append("- ").append(toolName).append(": ")
                    .append(executor.getDescription())
                    .append(", 参数: ")
                    .append(executor.getArgsDescription())
                    .append("\n");
        }

        JSONObject functionObj = new JSONObject();
        functionObj.put("name", CHANNEL_NAME);
        functionObj.put("description", descriptionBuilder.toString().trim());

        JSONObject params = new JSONObject();
        params.put("type", "object");

        JSONObject properties = new JSONObject();

        JSONObject functionParam = new JSONObject();
        functionParam.put("type", "string");
        functionParam.put("description", "要调用的功能名称");
        functionParam.put("enum", toolNames);
        properties.put("function", functionParam);

        JSONObject argsParam = new JSONObject();
        argsParam.put("type", "object");
        argsParam.put("description", "功能参数，JSON 对象格式");
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
