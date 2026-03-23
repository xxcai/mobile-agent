package com.hh.agent.android;

import com.hh.agent.core.tool.ToolResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ToolResultTest {

    @Test
    public void serializesSuccessResultWithPayload() {
        String raw = ToolResult.success()
                .with("result", "notification_shown")
                .with("mock", true)
                .toJsonString();

        assertEquals("{\"success\":true,\"result\":\"notification_shown\",\"mock\":true}", raw);
    }

    @Test
    public void serializesErrorResultWithMessageAndExtraFields() {
        String raw = ToolResult.error("missing_required_param", "title is required")
                .with("param", "title")
                .toJsonString();

        assertEquals(
                "{\"success\":false,\"error\":\"missing_required_param\",\"message\":\"title is required\",\"param\":\"title\"}",
                raw
        );
    }

    @Test
    public void serializesNestedJsonPayload() {
        String raw = ToolResult.success()
                .withJson("result", "[{\"id\":\"003\"}]")
                .toJsonString();

        assertEquals("{\"success\":true,\"result\":[{\"id\":\"003\"}]}", raw);
    }
}
