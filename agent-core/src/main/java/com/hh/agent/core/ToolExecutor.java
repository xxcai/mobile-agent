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
     * Execute the tool with given arguments.
     *
     * @param args JSON object containing tool arguments
     * @return JSON string with result: {"success": true, "result": ...} or {"success": false, "error": "..."}
     */
    String execute(org.json.JSONObject args);

    /**
     * Get the description of this tool's functionality.
     *
     * @return Tool description (e.g., "显示Toast消息")
     */
    String getDescription();

    /**
     * Get the description of the arguments.
     *
     * @return Arguments description (e.g., "message: 消息内容, duration: 时长")
     */
    String getArgsDescription();

    /**
     * Get the JSON Schema for the arguments.
     *
     * @return JSON Schema string
     */
    String getArgsSchema();
}
