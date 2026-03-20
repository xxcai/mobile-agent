package com.hh.agent.android.gesture;

import org.json.JSONObject;

/**
 * Result wrapper for gesture executor outputs.
 */
public class GestureExecutionResult {

    private final boolean success;
    private final String error;
    private final String message;
    private final String action;
    private final boolean mock;
    private final JSONObject payload;

    private GestureExecutionResult(boolean success,
                                   String error,
                                   String message,
                                   String action,
                                   boolean mock,
                                   JSONObject payload) {
        this.success = success;
        this.error = error;
        this.message = message;
        this.action = action;
        this.mock = mock;
        this.payload = payload;
    }

    public static GestureExecutionResult success(String action, boolean mock, JSONObject payload) {
        return new GestureExecutionResult(true, null, null, action, mock, payload);
    }

    public static GestureExecutionResult error(String action, String error, String message) {
        return new GestureExecutionResult(false, error, message, action, false, null);
    }

    public String toJsonString(String channelName) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", success);
            result.put("channel", channelName);
            if (action != null) {
                result.put("action", action);
            }
            if (success) {
                result.put("mock", mock);
                result.put("result", "not_implemented");
                if (payload != null) {
                    result.put("params", new JSONObject(payload.toString()));
                }
            } else {
                result.put("error", error);
                result.put("message", message);
            }
            return result.toString();
        } catch (Exception ignored) {
            return "{\"success\":false,\"error\":\"execution_failed\"}";
        }
    }
}
