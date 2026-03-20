package com.hh.agent.core;

/**
 * Callback interface for Android tool channels.
 * Implemented by Java layer to handle outer tool-channel calls from C++ native code.
 */
public interface AndroidToolCallback {

    /**
     * Call an Android tool channel with the given name and raw parameters.
     *
     * @param toolName The outer tool channel name (e.g., "call_android_tool")
     * @param argsJson JSON string containing the original tool parameters
     * @return JSON string with result: {"success": true, "result": ...} or {"success": false, "error": "..."}
     */
    String callTool(String toolName, String argsJson);
}
