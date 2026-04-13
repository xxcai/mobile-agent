package com.hh.agent.android.webaction;

import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.core.tool.ToolResult;

/**
 * Mock executor that keeps the web-action contract stable before real JS injection lands.
 */
public final class MockWebActionExecutor implements WebActionExecutor {

    @Override
    public ToolResult execute(WebActionRequest request) {
        AgentLogs.info("MockWebActionExecutor", "execute",
                "action=" + request.action + " selector=" + request.selector);
        return ToolResult.success()
                .with("channel", "android_web_action_tool")
                .with("domain", "web")
                .with("action", request.action)
                .with("selector", request.selector)
                .with("mock", true)
                .with("message", "Mock web action executor accepted the request")
                .with("observationSnapshotId",
                        request.observation != null ? request.observation.optString("snapshotId", null) : null);
    }
}
