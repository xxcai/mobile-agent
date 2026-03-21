package com.hh.agent.core;

/**
 * Tool executor interface.
 * Implemented by concrete tool implementations.
 */
public interface ToolExecutor {

    /**
     * Get the name of this tool.
     *
     * @return Tool name (e.g., "show_toast")
     */
    String getName();

    /**
     * Get the structured definition of this tool for schema aggregation and model guidance.
     *
     * @return tool definition metadata
     */
    ToolDefinition getDefinition();

    /**
     * Execute the tool with given arguments.
     *
     * @param args JSON object containing tool arguments
     * @return structured tool result serialized later at the Android tool boundary
     */
    ToolResult execute(org.json.JSONObject args);
}
