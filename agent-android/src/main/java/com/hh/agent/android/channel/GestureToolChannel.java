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
                        "Run an Android UI gesture on the current host page. Read android_view_context_tool first; use call_android_tool for business actions.")
                .property("action", ToolSchemaBuilder.string()
                        .description(buildActionDescription())
                        .enumValues(getActionNames()), true)
                .property("observation", buildObservationSchema(), false);
        for (GestureToolActionHandler handler : actionHandlers.values()) {
            handler.contributeProperties(builder);
        }
        return builder.build();
    }

    static ToolSchemaBuilder.ObjectSchemaBuilder buildObservationSchema() {
        return ToolSchemaBuilder.object()
                .description("Observation reference for the gesture. Prefer this for tap and swipe.")
                .property("snapshotId", ToolSchemaBuilder.string()
                        .description("Current-turn snapshot id"), false)
                .property("targetNodeIndex", ToolSchemaBuilder.integer()
                        .description("Referenced target node index"), false)
                .property("targetDescriptor", ToolSchemaBuilder.string()
                        .description("Human-readable target description"), false)
                .property("referencedBounds", ToolSchemaBuilder.string()
                        .description("Referenced bounds in [l,t][r,b] format"), false);
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
        StringBuilder description = new StringBuilder("Gesture action type.");
        boolean first = true;
        for (GestureToolActionHandler handler : actionHandlers.values()) {
            if (!first) {
                description.append(' ');
            }
            description.append(handler.getActionDescription());
            first = false;
        }
        return description.toString();
    }

    private String[] getActionNames() {
        return actionHandlers.keySet().toArray(new String[0]);
    }

    private ToolResult buildError(String errorCode, String message) {
        return ToolResult.error(errorCode, message);
    }
}