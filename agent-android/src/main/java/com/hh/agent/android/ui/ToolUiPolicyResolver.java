package com.hh.agent.android.ui;

import com.hh.agent.android.channel.AndroidToolChannelExecutor;

import java.util.Map;

/**
 * Resolves response-card tool UI decisions from registered Android tool channels.
 */
public final class ToolUiPolicyResolver {

    private final Map<String, AndroidToolChannelExecutor> channels;

    public ToolUiPolicyResolver(Map<String, AndroidToolChannelExecutor> channels) {
        this.channels = channels;
    }

    public ToolUiDecision resolve(String topLevelToolName, String argumentsJson) {
        if (topLevelToolName == null || topLevelToolName.trim().isEmpty()) {
            return ToolUiDecision.hidden();
        }
        if (channels == null || channels.isEmpty()) {
            return ToolUiDecision.hidden();
        }

        AndroidToolChannelExecutor channel = channels.get(topLevelToolName.trim());
        if (channel == null) {
            return ToolUiDecision.hidden();
        }
        if (!channel.shouldExposeInnerToolInToolUi()) {
            return ToolUiDecision.hidden();
        }

        return ToolUiDecision.visible(channel.resolveInnerToolDisplayName(argumentsJson));
    }
}
