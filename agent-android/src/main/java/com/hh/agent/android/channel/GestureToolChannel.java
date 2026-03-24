package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.gesture.GestureExecutorRegistry;
import com.hh.agent.core.tool.ToolResult;

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
        properties.put("observation", new JSONObject()
                .put("type", "object")
                .put("description", "基于 observation 执行时的引用信息。页面元素类任务优先填写该对象，再配合坐标作为兼容 fallback。")
                .put("properties", new JSONObject()
                        .put("snapshotId", new JSONObject()
                                .put("type", "string")
                                .put("description", "当前回合 view context 返回的 snapshot 标识。"))
                        .put("targetNodeIndex", new JSONObject()
                                .put("type", "integer")
                                .put("description", "目标节点在 observation 中的引用索引，可选。"))
                        .put("targetDescriptor", new JSONObject()
                                .put("type", "string")
                                .put("description", "目标元素的人类可读描述，例如“发送消息按钮”或“第二个卡片”。"))
                        .put("referencedBounds", new JSONObject()
                                .put("type", "string")
                                .put("description", "从 observation 中引用的 bounds 字符串，可选。格式如 [l,t][r,b]。"))));
        properties.put("allowCoordinateFallback", new JSONObject()
                .put("type", "boolean")
                .put("description", "仅用于兼容旧测试或受控回退场景。为 true 时，即使缺少 observation，也允许继续使用裸坐标。默认 false。")
                .put("default", false));

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", new JSONArray().put("action"));

        JSONObject function = new JSONObject();
        function.put("name", CHANNEL_NAME);
        function.put("description", "执行基于屏幕坐标的 Android 手势。适合点击和滑动等 UI 操作，例如点击按钮、从列表顶部滑到中部。页面元素类任务优先携带 observation 引用信息，再配合坐标作为兼容 fallback。不要用这个通道搜索联系人、发送消息、读取剪贴板或调用宿主 App 的业务工具；这类任务应使用 call_android_tool。当前运行时为 mock，用于验证通道选择、动作选择和参数组织。");
        function.put("parameters", parameters);

        return new JSONObject()
                .put("type", "function")
                .put("function", function);
    }

    @Override
    public ToolResult execute(JSONObject params) {
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
                    ToolResult gatingResult = validateTapObservation(params);
                    if (gatingResult != null) {
                        return gatingResult;
                    }
                    result = executor.tap(params);
                    return result.toToolResult(CHANNEL_NAME);
                case "swipe":
                    if (!params.has("startX") || !params.has("startY")
                            || !params.has("endX") || !params.has("endY")) {
                        return buildError("invalid_args", "swipe requires 'startX', 'startY', 'endX', and 'endY'");
                    }
                    result = executor.swipe(params);
                    return result.toToolResult(CHANNEL_NAME);
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

    private ToolResult validateTapObservation(JSONObject params) {
        if (params.optBoolean("allowCoordinateFallback", false)) {
            return null;
        }

        JSONObject observation = params.optJSONObject("observation");
        if (observation == null) {
            return ToolResult.error("missing_view_context_observation",
                            "tap requires current-turn observation evidence before execution")
                    .with("channel", CHANNEL_NAME)
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("suggestedSource", "native_xml")
                    .with("messageForModel",
                            "Call android_view_context_tool with source=native_xml first, then retry tap with observation.snapshotId.");
        }

        String snapshotId = observation.optString("snapshotId", "").trim();
        if (snapshotId.isEmpty()) {
            return ToolResult.error("missing_view_context_snapshot_id",
                            "tap observation must include a non-empty snapshotId")
                    .with("channel", CHANNEL_NAME)
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("suggestedSource", "native_xml")
                    .with("messageForModel",
                            "Call android_view_context_tool with source=native_xml first, then retry tap with observation.snapshotId.");
        }
        return null;
    }

    private ToolResult buildError(String errorCode, String message) {
        return ToolResult.error(errorCode, message);
    }
}
