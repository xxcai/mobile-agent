package com.hh.agent.android.route;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Result returned by RouteOpener. Tool mapping lands in later steps.
 */
public final class RouteOpenResult {
    private final boolean success;
    private final String errorCode;
    private final String message;
    private final RouteTarget target;
    private final boolean containerDismissed;
    private final boolean hostActivityReady;

    private RouteOpenResult(boolean success,
                            String errorCode,
                            String message,
                            RouteTarget target,
                            boolean containerDismissed,
                            boolean hostActivityReady) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.target = target;
        this.containerDismissed = containerDismissed;
        this.hostActivityReady = hostActivityReady;
    }

    public static RouteOpenResult success(RouteTarget target,
                                          boolean containerDismissed,
                                          boolean hostActivityReady) {
        return new RouteOpenResult(true, null, null, target, containerDismissed, hostActivityReady);
    }

    public static RouteOpenResult failure(String errorCode,
                                          String message,
                                          RouteTarget target,
                                          boolean containerDismissed,
                                          boolean hostActivityReady) {
        return new RouteOpenResult(false, errorCode, message, target, containerDismissed, hostActivityReady);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        put(json, "success", success);
        if (errorCode != null) {
            put(json, "error", errorCode);
        }
        if (message != null) {
            put(json, "message", message);
        }
        if (target != null) {
            put(json, "target", target.toJson());
        }
        JSONObject meta = new JSONObject();
        put(meta, "containerDismissed", containerDismissed);
        put(meta, "hostActivityReady", hostActivityReady);
        put(json, "meta", meta);
        return json;
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize RouteOpenResult field: " + key, exception);
        }
    }
}
