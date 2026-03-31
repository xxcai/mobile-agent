package com.hh.agent.core.tool;

/**
 * Callback interface for Android tool channels.
 * Implemented by Java layer to handle outer tool-channel calls from C++ native code.
 */
public interface AndroidToolCallback {

    /**
     * Call an Android tool channel with the given name and raw parameters.
     *
     * @param toolName The outer tool channel name (e.g., "run_shortcut")
     * @param argsJson JSON string containing the original tool parameters
     * @return JSON string serialized from the structured tool result
     */
    String callTool(String toolName, String argsJson);
}
