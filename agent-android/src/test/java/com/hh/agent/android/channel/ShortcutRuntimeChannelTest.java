package com.hh.agent.android.channel;

import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShortcutRuntimeChannelTest {

    @Test
    public void buildToolDefinitionUsesExpectedRuntimeContract() throws Exception {
        ShortcutRuntime runtime = new ShortcutRuntime();
        runtime.register(new RecordingShortcutExecutor());
        ShortcutRuntimeChannel channel = new ShortcutRuntimeChannel(runtime);

        JSONObject schema = channel.buildToolDefinition();
        JSONObject function = schema.getJSONObject("function");
        JSONObject parameters = function.getJSONObject("parameters");
        JSONObject properties = parameters.getJSONObject("properties");

        assertEquals("run_shortcut", function.getString("name"));
        assertEquals(2, parameters.getJSONArray("required").length());
        assertEquals("shortcut", parameters.getJSONArray("required").getString(0));
        assertEquals("args", parameters.getJSONArray("required").getString(1));
        assertEquals("string", properties.getJSONObject("shortcut").getString("type"));
        assertEquals("object", properties.getJSONObject("args").getString("type"));
        assertFalse(properties.getJSONObject("shortcut").has("enum"));
        assertFalse(properties.getJSONObject("shortcut")
                .getString("description")
                .contains("requiredSkill="));
        assertFalse(properties.getJSONObject("args")
                .getString("description")
                .contains("schema="));
        assertFalse(properties.getJSONObject("args")
                .getString("description")
                .contains("example="));
    }

    @Test
    public void executeRoutesShortcutThroughRuntime() throws Exception {
        ShortcutRuntime runtime = new ShortcutRuntime();
        runtime.register(new RecordingShortcutExecutor());
        ShortcutRuntimeChannel channel = new ShortcutRuntimeChannel(runtime);

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("shortcut", "send_im_message")
                .put("args", new JSONObject()
                        .put("contact_id", "003")
                        .put("message", "test"))).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("run_shortcut", result.getString("channel"));
        assertEquals("send_im_message", result.getString("shortcut"));
        assertEquals("im", result.getString("domain"));
        assertEquals("im_sender", result.getString("requiredSkill"));
    }

    @Test
    public void executeReturnsShortcutNotSupportedWhenMissing() throws Exception {
        ShortcutRuntime runtime = new ShortcutRuntime();
        ShortcutRuntimeChannel channel = new ShortcutRuntimeChannel(runtime);

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("shortcut", "unknown_shortcut")
                .put("args", new JSONObject())).toJsonString());

        assertEquals(false, result.getBoolean("success"));
        assertEquals("shortcut_not_supported", result.getString("error"));
        assertEquals("run_shortcut", result.getString("channel"));
        assertEquals("unknown_shortcut", result.getString("shortcut"));
    }

    private static final class RecordingShortcutExecutor implements ShortcutExecutor {
        @Override
        public ShortcutDefinition getDefinition() {
            return ShortcutDefinition.builder("send_im_message", "发送消息", "向指定联系人发送文本消息")
                    .domain("im")
                    .requiredSkill("im_sender")
                    .risk("write")
                    .stringParam("contact_id", "联系人ID", true, "003")
                    .stringParam("message", "消息内容", true, "test")
                    .build();
        }

        @Override
        public ToolResult execute(JSONObject args) {
            return ToolResult.success()
                    .with("result", "ok")
                    .with("contact_id", args.optString("contact_id", ""));
        }
    }
}
