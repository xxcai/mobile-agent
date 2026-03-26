package com.hh.agent.core.event;

/**
 * Callback interface for stream events from C++ native agent.
 * Receives streaming events like text_delta, reasoning_delta, tool_use, tool_result, message_end, and errors.
 */
public interface AgentEventListener {

    /**
     * Called when text delta is received during streaming.
     *
     * @param text The incremental text content
     */
    void onTextDelta(String text);

    /**
     * Called when reasoning delta is received during streaming.
     *
     * @param text The incremental reasoning content
     */
    void onReasoningDelta(String text);

    /**
     * Called when a tool use is initiated.
     *
     * @param id The tool call ID
     * @param name The tool name
     * @param argumentsJson JSON string containing tool arguments
     */
    void onToolUse(String id, String name, String argumentsJson);

    /**
     * Called when a tool use result is received.
     *
     * @param id The tool call ID
     * @param result The tool result as JSON string
     */
    void onToolResult(String id, String result);

    /**
     * Called when the message stream ends.
     *
     * @param finishReason The reason for completion (e.g., "stop", "length")
     */
    void onMessageEnd(String finishReason);

    /**
     * Called when an error occurs during streaming.
     *
     * @param errorCode The error code
     * @param errorMessage The error message
     */
    void onError(String errorCode, String errorMessage);
}
