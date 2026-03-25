package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.gesture.GestureExecutorRegistry;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Gesture channel for observation-bound in-process touch injection.
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
                .put("description", "手势动作类型。tap 表示点击页面元素；swipe 表示按高层滚动意图驱动当前页面滚动。运行时会在当前前台 Activity 内注入真实触摸事件。不要填写业务工具名。")
                .put("enum", new JSONArray().put("tap").put("swipe")));
        properties.put("x", new JSONObject()
                .put("type", "integer")
                .put("description", "点击目标的 X 坐标。仅在 action=tap 时必填。"));
        properties.put("y", new JSONObject()
                .put("type", "integer")
                .put("description", "点击目标的 Y 坐标。仅在 action=tap 时必填。"));
        properties.put("direction", new JSONObject()
                .put("type", "string")
                .put("description", "滚动方向。down 表示继续查看更早的内容，up 表示回看更新的内容。仅在 action=swipe 时必填。")
                .put("enum", new JSONArray().put("down").put("up")));
        properties.put("scope", new JSONObject()
                .put("type", "string")
                .put("description", "滚动作用域。feed 表示优先命中当前主 feed 容器。仅在 action=swipe 时可选。")
                .put("enum", new JSONArray().put("feed"))
                .put("default", "feed"));
        properties.put("amount", new JSONObject()
                .put("type", "string")
                .put("description", "滚动幅度。small/medium/large/one_screen 分别对应容器高度的不同百分比。仅在 action=swipe 时可选。")
                .put("enum", new JSONArray().put("small").put("medium").put("large").put("one_screen"))
                .put("default", "medium"));
        properties.put("duration", new JSONObject()
                .put("type", "integer")
                .put("description", "滚动后的稳定等待时间，单位毫秒。仅在 action=swipe 时可选。"));
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
        function.put("description", "执行当前宿主页面内的 Android UI 动作。tap 会优先依据 observation 引用计算目标点，再通过当前 Activity 注入 DOWN/UP 事件；swipe 只接收高层滚动意图，由运行时自动计算安全距离并注入 DOWN/MOVE/UP 序列。不要用这个通道搜索联系人、发送消息、读取剪贴板或调用宿主 App 的业务工具；这类任务应使用 call_android_tool。");
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
                    if (!params.has("direction")) {
                        return buildError("invalid_args", "swipe requires 'direction'");
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
