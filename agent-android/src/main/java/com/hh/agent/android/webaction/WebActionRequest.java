package com.hh.agent.android.webaction;

import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Parsed request envelope for android_web_action_tool.
 */
public final class WebActionRequest {

    public final String action;
    @Nullable
    public final String selector;
    @Nullable
    public final String text;
    @Nullable
    public final JSONObject observation;

    private WebActionRequest(String action,
                             @Nullable String selector,
                             @Nullable String text,
                             @Nullable JSONObject observation) {
        this.action = action;
        this.selector = selector;
        this.text = text;
        this.observation = observation;
    }

    public static WebActionRequest fromJson(JSONObject params) {
        return new WebActionRequest(
                params.optString("action", "").trim(),
                normalize(params.optString("selector", null)),
                normalize(params.optString("text", null)),
                params.optJSONObject("observation")
        );
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
