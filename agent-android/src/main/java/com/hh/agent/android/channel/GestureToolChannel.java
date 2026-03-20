package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.gesture.GestureExecutorRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Mock gesture channel used to validate multi-channel schema and parameter routing.
 * Real runtime gesture execution will be added in a later step.
 */
public class GestureToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "android_gesture_tool";

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        JSONObject properties = new JSONObject();

        properties.put("action", new JSONObject()
                .put("type", "string")
                .put("description", "手势动作类型。tap 表示点击单个屏幕坐标；swipe 表示从起点拖动到终点。不要填写业务工具名。")
                .put("enum", new JSONArray().put("tap").put("swipe")));
        properties.put("x", new JSONObject()
                .put("type", "integer")
                .put("description", "点击目标的 X 坐标。仅在 action=tap 时必填。"));
        properties.put("y", new JSONObject()
                .put("type", "integer")
                .put("description", "点击目标的 Y 坐标。仅在 action=tap 时必填。"));
        properties.put("startX", new JSONObject()
                .put("type", "integer")
                .put("description", "滑动起点的 X 坐标。仅在 action=swipe 时必填。"));
        properties.put("startY", new JSONObject()
                .put("type", "integer")
                .put("description", "滑动起点的 Y 坐标。仅在 action=swipe 时必填。"));
        properties.put("endX", new JSONObject()
                .put("type", "integer")
                .put("description", "滑动终点的 X 坐标。仅在 action=swipe 时必填。"));
        properties.put("endY", new JSONObject()
                .put("type", "integer")
                .put("description", "滑动终点的 Y 坐标。仅在 action=swipe 时必填。"));
        properties.put("duration", new JSONObject()
                .put("type", "integer")
                .put("description", "滑动持续时间，单位毫秒。仅在 action=swipe 时可选。"));

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", new JSONArray().put("action"));

        JSONObject function = new JSONObject();
        function.put("name", CHANNEL_NAME);
        function.put("description", "执行基于屏幕坐标的 Android 手势。适合点击和滑动等 UI 操作，例如点击按钮、从列表顶部滑到中部。不要用这个通道搜索联系人、发送消息、读取剪贴板或调用宿主 App 的业务工具；这类任务应使用 call_android_tool。当前运行时为 mock，用于验证通道选择、动作选择和参数组织。");
        function.put("parameters", parameters);

        return new JSONObject()
                .put("type", "function")
                .put("function", function);
    }

    @Override
    public String execute(JSONObject params) {
        try {
            String action = params.optString("action", "").trim();
            if (action.isEmpty()) {
                return buildError("invalid_args", "android_gesture_tool requires a non-empty 'action' field");
            }

            AndroidGestureExecutor executor = GestureExecutorRegistry.getExecutor();
            GestureExecutionResult result;
            switch (action) {
                case "tap":
                    if (!params.has("x") || !params.has("y")) {
                        return buildError("invalid_args", "tap requires integer fields 'x' and 'y'");
                    }
                    result = executor.tap(params);
                    return result.toJsonString(CHANNEL_NAME);
                case "swipe":
                    if (!params.has("startX") || !params.has("startY")
                            || !params.has("endX") || !params.has("endY")) {
                        return buildError("invalid_args", "swipe requires 'startX', 'startY', 'endX', and 'endY'");
                    }
                    result = executor.swipe(params);
                    return result.toJsonString(CHANNEL_NAME);
                default:
                    return buildError("invalid_args", "Unsupported gesture action '" + action + "'");
            }
        } catch (Exception e) {
            return buildError("execution_failed", e.getMessage());
        }
    }

    @Override
    public boolean shouldExposeInnerToolInToolUi() {
        return false;
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
