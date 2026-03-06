package com.hh.agent.library;

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
     * Execute the tool with given arguments.
     *
     * @param args JSON object containing tool arguments
     * @return JSON string with result: {"success": true, "result": ...} or {"success": false, "error": "..."}
     */
    String execute(org.json.JSONObject args);
}
