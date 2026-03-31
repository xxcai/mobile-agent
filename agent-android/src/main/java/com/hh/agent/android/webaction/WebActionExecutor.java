package com.hh.agent.android.webaction;

import com.hh.agent.core.tool.ToolResult;

/**
 * Executes a web-domain action using a previously captured web observation.
 */
public interface WebActionExecutor {

    ToolResult execute(WebActionRequest request);
}
