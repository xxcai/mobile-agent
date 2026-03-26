package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutorRegistry;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gesture channel for observation-bound in-process touch injection.
 */
public class GestureToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "android_gesture_tool";
    private final Map<String, GestureToolActionHandler> actionHandlers =
            createActionHandlers();

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        ToolSchemaBuilder.FunctionToolBuilder builder = ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "执行当前宿主页面内的 Android UI 动作。tap 应先读取 android_view_context_tool，再基于最新 observation 点击目标；swipe 也应先读取 android_view_context_tool，并且必须在 observation 中明确指定要滚动的容器 bounds，运行时再在当前 Activity 内注入真实触摸事件。不要猜测裸坐标，不要在没有最新 observation 的情况下调用本工具。不要用这个通道搜索联系人、发送消息、读取剪贴板或调用宿主 App 的业务工具；这类任务应使用 call_android_tool。")
                .property("action", ToolSchemaBuilder.string()
                        .description(buildActionDescription())
                        .enumValues(getActionNames()), true)
                .property("observation", buildObservationSchema(), false)
                ;
        for (GestureToolActionHandler handler : actionHandlers.values()) {
            handler.contributeProperties(builder);
        }
        return builder.build();
    }

    static ToolSchemaBuilder.ObjectSchemaBuilder buildObservationSchema() {
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

            GestureToolActionHandler actionHandler = actionHandlers.get(action);
            if (actionHandler == null) {
                return buildError("invalid_args", "Unsupported gesture action '" + action + "'");
            }

            AndroidGestureExecutor executor = GestureExecutorRegistry.getExecutor();
            return actionHandler.execute(params, executor);
        } catch (Exception e) {
            return buildError("execution_failed", e.getMessage());
        }
    }

    @Override
    public boolean shouldExposeInnerToolInToolUi() {
        return false;
    }

    private Map<String, GestureToolActionHandler> createActionHandlers() {
        LinkedHashMap<String, GestureToolActionHandler> handlers = new LinkedHashMap<>();
        register(handlers, new TapGestureToolActionHandler());
        register(handlers, new SwipeGestureToolActionHandler());
        return handlers;
    }

    private void register(Map<String, GestureToolActionHandler> handlers,
                          GestureToolActionHandler handler) {
        String actionName = handler.getActionName();
        if (handlers.containsKey(actionName)) {
            throw new IllegalStateException("Duplicate gesture action handler: " + actionName);
        }
        handlers.put(actionName, handler);
    }

    private String buildActionDescription() {
        StringBuilder description = new StringBuilder("手势动作类型。");
        boolean first = true;
        for (GestureToolActionHandler handler : actionHandlers.values()) {
            if (!first) {
                description.append("；");
            }
            description.append(handler.getActionDescription());
            first = false;
        }
        description.append("。运行时会在当前前台 Activity 内注入真实触摸事件。不要填写业务工具名。");
        return description.toString();
    }

    private String[] getActionNames() {
        return actionHandlers.keySet().toArray(new String[0]);
    }

    private ToolResult buildError(String errorCode, String message) {
        return ToolResult.error(errorCode, message);
    }
}
