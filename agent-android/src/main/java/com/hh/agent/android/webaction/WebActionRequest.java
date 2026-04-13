package com.hh.agent.android.webaction;

import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Parsed request envelope for android_web_action_tool.
 */
public final class WebActionRequest {

    public final String action;
    @Nullable
    public final String ref;
    @Nullable
    public final String selector;
    @Nullable
    public final String text;
    @Nullable
    public final String script;
    @Nullable
    public final JSONObject observation;

    private WebActionRequest(String action,
                             @Nullable String ref,
                             @Nullable String selector,
                             @Nullable String text,
                             @Nullable String script,
                             @Nullable JSONObject observation) {
        this.action = action;
        this.ref = ref;
        this.selector = selector;
        this.text = text;
        this.script = script;
        this.observation = observation;
    }

    public static WebActionRequest fromJson(JSONObject params) {
        return new WebActionRequest(
                params.optString("action", "").trim(),
                normalize(params.optString("ref", null)),
                normalize(params.optString("selector", null)),
                normalize(params.optString("text", null)),
                normalize(params.optString("script", null)),
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
