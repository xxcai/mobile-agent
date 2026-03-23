package com.hh.agent.core.tool;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Structured tool execution result serialized at the Android tool boundary.
 */
public final class ToolResult {

    private final boolean success;
    private final String error;
    private final String message;
    private final JsonObject data = new JsonObject();

    private ToolResult(boolean success, String error, String message) {
        this.success = success;
        this.error = error;
        this.message = message;
    }

    public static ToolResult success() {
        return new ToolResult(true, null, null);
    }

    public static ToolResult error(String error) {
        return new ToolResult(false, error, null);
    }

    public static ToolResult error(String error, String message) {
        return new ToolResult(false, error, message);
    }

    public ToolResult with(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key cannot be null or empty");
        }
        if (value == null) {
            data.add(key, JsonNull.INSTANCE);
        } else {
            data.addProperty(key, value);
        }
        return this;
    }

    public ToolResult with(String key, boolean value) {
        validateKey(key);
        data.addProperty(key, value);
        return this;
    }

    public ToolResult with(String key, Number value) {
        validateKey(key);
        if (value == null) {
            data.add(key, JsonNull.INSTANCE);
        } else {
            data.addProperty(key, value);
        }
        return this;
    }

    public ToolResult withJson(String key, String json) {
        validateKey(key);
        if (json == null || json.trim().isEmpty()) {
            data.add(key, JsonNull.INSTANCE);
            return this;
        }
        data.add(key, JsonParser.parseString(json));
        return this;
    }

    public String toJsonString() {
        JsonObject root = new JsonObject();
        root.addProperty("success", success);
        if (success) {
            for (String key : data.keySet()) {
                root.add(key, data.get(key));
            }
        } else {
            if (error != null) {
                root.addProperty("error", error);
            }
            if (message != null) {
                root.addProperty("message", message);
            }
            for (String key : data.keySet()) {
                root.add(key, data.get(key));
            }
        }
        return root.toString();
    }

    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key cannot be null or empty");
        }
    }
}
