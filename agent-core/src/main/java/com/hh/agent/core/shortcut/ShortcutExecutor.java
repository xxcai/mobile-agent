package com.hh.agent.core.shortcut;

import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

/**
 * Executes a single shortcut inside the shortcut runtime.
 */
public interface ShortcutExecutor {

    ShortcutDefinition getDefinition();

    default ToolResult validate(JSONObject args) {
        return ToolResult.success();
    }

    ToolResult execute(JSONObject args);
}
