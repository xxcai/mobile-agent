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
     * @return JSON string with result: {"success": true, "result": ...} or {"success": false, "error": "..."}
     */
    String execute(org.json.JSONObject args);
}
