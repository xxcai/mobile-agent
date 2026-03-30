package com.hh.agent.android.channel;

import com.hh.agent.android.webaction.WebActionExecutor;
import com.hh.agent.android.webaction.WebActionRequest;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WebActionToolChannelTest {

    @Test
    public void buildToolDefinition_exposesWebActionFields() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel();
        JSONObject properties = channel.buildToolDefinition()
                .getJSONObject("function")
                .getJSONObject("parameters")
                .getJSONObject("properties");

        assertTrue(properties.has("action"));
        assertTrue(properties.has("selector"));
        assertTrue(properties.has("text"));
        assertTrue(properties.has("observation"));
    }

    @Test
    public void execute_requiresObservationSnapshot() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel();
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("action", "click")
                .put("selector", "#submit")).toJsonString());

        assertEquals(false, result.getBoolean("success"));
        assertEquals("missing_web_observation", result.getString("error"));
        assertEquals("android_web_action_tool", result.getString("channel"));
        assertEquals("web", result.getString("domain"));
    }

    @Test
    public void execute_usesExecutorAfterValidation() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new WebActionExecutor() {
            @Override
            public ToolResult execute(WebActionRequest request) {
                return ToolResult.success()
                        .with("channel", WebActionToolChannel.CHANNEL_NAME)
                        .with("domain", "web")
                        .with("action", request.action)
                        .with("selector", request.selector)
                        .with("mock", true);
            }
        });

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("action", "click")
                .put("selector", "#submit")
                .put("observation", new JSONObject().put("snapshotId", "obs_123"))).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("android_web_action_tool", result.getString("channel"));
        assertEquals("click", result.getString("action"));
        assertEquals("#submit", result.getString("selector"));
    }
}
