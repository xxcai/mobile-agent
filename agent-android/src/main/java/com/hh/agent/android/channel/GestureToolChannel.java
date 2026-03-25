package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.gesture.GestureExecutorRegistry;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

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
        return ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "执行当前宿主页面内的 Android UI 动作。tap 应先读取 android_view_context_tool，再基于最新 observation 点击目标；swipe 也应先读取 android_view_context_tool，并且必须在 observation 中明确指定要滚动的容器 bounds，运行时再在当前 Activity 内注入真实触摸事件。不要猜测裸坐标，不要在没有最新 observation 的情况下调用本工具。不要用这个通道搜索联系人、发送消息、读取剪贴板或调用宿主 App 的业务工具；这类任务应使用 call_android_tool。")
                .property("action", ToolSchemaBuilder.string()
                        .description("手势动作类型。tap 表示点击页面元素；swipe 表示按高层滚动意图驱动当前页面滚动。运行时会在当前前台 Activity 内注入真实触摸事件。不要填写业务工具名。")
                        .enumValues("tap", "swipe"), true)
                .property("x", ToolSchemaBuilder.integer()
                        .description("点击目标的 X 坐标。仅在 action=tap 时必填。"), false)
                .property("y", ToolSchemaBuilder.integer()
                        .description("点击目标的 Y 坐标。仅在 action=tap 时必填。"), false)
                .property("direction", ToolSchemaBuilder.string()
                        .description("滚动方向。down 表示继续查看更早的内容，up 表示回看更新的内容。仅在 action=swipe 时必填。")
                        .enumValues("down", "up"), false)
                .property("amount", ToolSchemaBuilder.string()
                        .description("滚动幅度。small/medium/large/one_screen 分别对应容器高度的不同百分比。仅在 action=swipe 时可选。")
                        .enumValues("small", "medium", "large", "one_screen")
                        .defaultValue("medium"), false)
                .property("duration", ToolSchemaBuilder.integer()
                        .description("滚动后的稳定等待时间，单位毫秒。仅在 action=swipe 时可选。"), false)
                .property("observation", buildObservationSchema(), false)
                .property("allowCoordinateFallback", ToolSchemaBuilder.bool()
                        .description("仅用于兼容旧测试或受控回退场景。为 true 时，即使缺少 observation，也允许继续使用裸坐标。默认 false。")
                        .defaultValue(false), false)
                .build();
    }

    private ToolSchemaBuilder.ObjectSchemaBuilder buildObservationSchema() {
        return ToolSchemaBuilder.object()
                .description("基于 observation 执行时的引用信息。tap 和 swipe 都应优先使用该对象。swipe 必须用它明确指定要滚动的容器 bounds。")
                .property("snapshotId", ToolSchemaBuilder.string()
                        .description("当前回合 view context 返回的 snapshot 标识。"), false)
                .property("targetNodeIndex", ToolSchemaBuilder.integer()
                        .description("目标节点在 observation 中的引用索引，可选。"), false)
                .property("targetDescriptor", ToolSchemaBuilder.string()
                        .description("目标元素或目标容器的人类可读描述，例如“发送消息按钮”或“朋友圈列表”。"), false)
                .property("referencedBounds", ToolSchemaBuilder.string()
                        .description("从 observation 中引用的 bounds 字符串。tap 可用它定位点击目标；swipe 必须用它指定要滚动的容器。格式如 [l,t][r,b]。"), false);
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
                    ToolResult swipeGatingResult = validateSwipeObservation(params);
                    if (swipeGatingResult != null) {
                        return swipeGatingResult;
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

    private ToolResult validateSwipeObservation(JSONObject params) {
        JSONObject observation = params.optJSONObject("observation");
        if (observation == null) {
            return ToolResult.error("missing_view_context_observation",
                            "swipe requires current-turn observation evidence for the target scroll container")
                    .with("channel", CHANNEL_NAME)
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("suggestedSource", "native_xml")
                    .with("messageForModel",
                            "Call android_view_context_tool with source=native_xml first, then retry swipe with observation.snapshotId and observation.referencedBounds for the target container.");
        }

        String snapshotId = observation.optString("snapshotId", "").trim();
        if (snapshotId.isEmpty()) {
            return ToolResult.error("missing_view_context_snapshot_id",
                            "swipe observation must include a non-empty snapshotId")
                    .with("channel", CHANNEL_NAME)
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("suggestedSource", "native_xml")
                    .with("messageForModel",
                            "Call android_view_context_tool with source=native_xml first, then retry swipe with observation.snapshotId and observation.referencedBounds for the target container.");
        }

        String referencedBounds = observation.optString("referencedBounds", "").trim();
        if (referencedBounds.isEmpty()) {
            return ToolResult.error("missing_scroll_container_bounds",
                            "swipe observation must include referencedBounds for the target scroll container")
                    .with("channel", CHANNEL_NAME)
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("suggestedSource", "native_xml")
                    .with("messageForModel",
                            "Select the target scroll container from the latest android_view_context_tool result, then retry swipe with observation.referencedBounds for that container.");
        }
        return null;
    }

    private ToolResult buildError(String errorCode, String message) {
        return ToolResult.error(errorCode, message);
    }
}
