package com.hh.agent.android.channel;

import com.hh.agent.android.webaction.WebActionExecutor;
import com.hh.agent.android.webaction.WebActionRequest;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebActionToolChannelTest {

    @Test
    public void buildToolDefinition_exposesWebActionFields() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new NoOpExecutor());
        JSONObject parameters = channel.buildToolDefinition()
                .getJSONObject("function")
                .getJSONObject("parameters");
        JSONObject properties = parameters.getJSONObject("properties");

        assertTrue(properties.has("action"));
        assertTrue(properties.has("ref"));
        assertTrue(properties.has("selector"));
        assertTrue(properties.has("text"));
        assertTrue(properties.has("script"));
        assertTrue(properties.has("observation"));

        JSONObject actionSchema = properties.getJSONObject("action");
        assertTrue(actionSchema.getJSONArray("enum").toString().contains("eval_js"));
        assertTrue(actionSchema.getJSONArray("enum").toString().contains("scroll_to_bottom"));

        assertTrue(parameters.has("required"));
        assertEquals(1, parameters.getJSONArray("required").length());
        assertEquals("action", parameters.getJSONArray("required").getString(0));
    }

    @Test
    public void execute_requiresObservationSnapshot() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new NoOpExecutor());
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

    @Test
    public void execute_clickAcceptsRefWithoutSelector() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new NoOpExecutor());

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("action", "click")
                .put("ref", "node-1")
                .put("observation", new JSONObject().put("snapshotId", "obs_123"))).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("node-1", result.getString("ref"));
    }

    @Test
    public void execute_inputRequiresRefOrSelector() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new NoOpExecutor());

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("action", "input")
                .put("text", "hello")
                .put("observation", new JSONObject().put("snapshotId", "obs_123"))).toJsonString());

        assertEquals(false, result.getBoolean("success"));
        assertEquals("invalid_args", result.getString("error"));
        assertTrue(result.getString("message").contains("requires a non-empty 'ref' or 'selector'"));
    }

    @Test
    public void execute_evalJsRequiresScript() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new NoOpExecutor());

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("action", "eval_js")
                .put("observation", new JSONObject().put("snapshotId", "obs_123"))).toJsonString());

        assertEquals(false, result.getBoolean("success"));
        assertEquals("invalid_args", result.getString("error"));
        assertTrue(result.getString("message").contains("eval_js requires a non-empty 'script'"));
    }

    @Test
    public void execute_scrollToBottomDoesNotRequireSelector() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new NoOpExecutor());

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("action", "scroll_to_bottom")
                .put("observation", new JSONObject().put("snapshotId", "obs_123"))).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("scroll_to_bottom", result.getString("action"));
    }

    @Test
    public void execute_inputAcceptsRefWithoutSelector() throws Exception {
        WebActionToolChannel channel = new WebActionToolChannel(new NoOpExecutor());

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("action", "input")
                .put("ref", "node-2")
                .put("text", "hello")
                .put("observation", new JSONObject().put("snapshotId", "obs_123"))).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("node-2", result.getString("ref"));
        assertEquals("hello", result.getString("text"));
    }

    private static final class NoOpExecutor implements WebActionExecutor {

        @Override
        public ToolResult execute(WebActionRequest request) {
            return ToolResult.success()
                    .with("channel", WebActionToolChannel.CHANNEL_NAME)
                    .with("domain", "web")
                    .with("action", request.action)
                    .with("selector", request.selector)
                    .with("ref", request.ref)
                    .with("text", request.text)
                    .with("script", request.script);
        }
    }
}
