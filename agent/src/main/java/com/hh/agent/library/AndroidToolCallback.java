package com.hh.agent.library;

/**
 * Callback interface for Android tools.
 * Implemented by Java layer to handle tool calls from C++ native code.
 */
public interface AndroidToolCallback {

    /**
     * Call an Android tool with the given name and arguments.
     *
     * @param toolName The name of the tool to call (e.g., "show_toast")
     * @param argsJson JSON string containing tool arguments (e.g., {"message": "Hello"})
     * @return JSON string with result: {"success": true, "result": ...} or {"success": false, "error": "..."}
     */
    String callTool(String toolName, String argsJson);
}
