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
                .put("description", "Gesture action name. Use 'tap' for a single coordinate tap, or 'swipe' for a drag gesture.")
                .put("enum", new JSONArray().put("tap").put("swipe")));
        properties.put("x", new JSONObject()
                .put("type", "integer")
                .put("description", "Tap X coordinate. Required when action is 'tap'."));
        properties.put("y", new JSONObject()
                .put("type", "integer")
                .put("description", "Tap Y coordinate. Required when action is 'tap'."));
        properties.put("startX", new JSONObject()
                .put("type", "integer")
                .put("description", "Swipe start X. Required when action is 'swipe'."));
        properties.put("startY", new JSONObject()
                .put("type", "integer")
                .put("description", "Swipe start Y. Required when action is 'swipe'."));
        properties.put("endX", new JSONObject()
                .put("type", "integer")
                .put("description", "Swipe end X. Required when action is 'swipe'."));
        properties.put("endY", new JSONObject()
                .put("type", "integer")
                .put("description", "Swipe end Y. Required when action is 'swipe'."));
        properties.put("duration", new JSONObject()
                .put("type", "integer")
                .put("description", "Swipe duration in milliseconds. Optional for 'swipe'."));

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", new JSONArray().put("action"));

        JSONObject function = new JSONObject();
        function.put("name", CHANNEL_NAME);
        function.put("description", "Execute coordinate-based Android gestures. Use this channel for tap and swipe actions, not for app/business tools.");
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
