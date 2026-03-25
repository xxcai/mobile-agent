package com.hh.agent.android.channel;

import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

abstract class AbstractViewContextSourceDefinition implements ViewContextSourceDefinition {

    protected ToolResult buildBaseResult(String source, String targetHint) {
        return ToolResult.success()
                .with("channel", ViewContextToolChannel.CHANNEL_NAME)
                .with("source", source)
                .with("mock", true)
                .with("targetHint", targetHint);
    }

    protected String optOptionalText(JSONObject params, String key) {
        String value = params.optString(key, null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
