package com.hh.agent.android.gesture;

import com.hh.agent.core.ToolResult;
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

    public ToolResult toToolResult(String channelName) {
        ToolResult result = success
                ? ToolResult.success()
                : ToolResult.error(error, message);
        result.with("channel", channelName);
        if (action != null) {
            result.with("action", action);
        }
        if (success) {
            result.with("mock", mock);
            result.with("result", "not_implemented");
            if (payload != null) {
                result.withJson("params", payload.toString());
            }
        }
        return result;
    }
}
