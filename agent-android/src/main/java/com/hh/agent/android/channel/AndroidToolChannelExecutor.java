package com.hh.agent.android.channel;

import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

/**
 * Executes a top-level Android tool channel and provides its schema.
 */
public interface AndroidToolChannelExecutor {

    /**
     * Returns the outer tool channel name exposed to the model.
     */
    String getChannelName();

    /**
     * Builds the tool definition object that will be appended to tools.json.
     */
    JSONObject buildToolDefinition() throws Exception;

    /**
     * Executes a channel call using the raw params passed from native.
     */
    ToolResult execute(JSONObject params);

    /**
     * Whether concrete tools under this channel may appear in the response-card tool UI.
     */
    default boolean shouldExposeInnerToolInToolUi() {
        return false;
    }

    /**
     * Resolves the user-facing concrete tool metadata from the raw arguments.
     * Returns hidden when this channel should stay hidden or the tool cannot be resolved.
     */
    default ToolUiDecision resolveInnerToolUiDecision(String argumentsJson) {
        return ToolUiDecision.hidden();
    }
}
