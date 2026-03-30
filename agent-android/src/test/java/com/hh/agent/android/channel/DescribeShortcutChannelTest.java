package com.hh.agent.android.channel;

import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DescribeShortcutChannelTest {

    @Test
    public void buildToolDefinitionUsesExpectedDiscoveryContract() throws Exception {
        ShortcutRuntime runtime = new ShortcutRuntime();
        DescribeShortcutChannel channel = new DescribeShortcutChannel(runtime);

        JSONObject schema = channel.buildToolDefinition();
        JSONObject function = schema.getJSONObject("function");
        JSONObject parameters = function.getJSONObject("parameters");
        JSONObject properties = parameters.getJSONObject("properties");

        assertEquals("describe_shortcut", function.getString("name"));
        assertEquals(1, parameters.getJSONArray("required").length());
        assertEquals("shortcut", parameters.getJSONArray("required").getString(0));
        assertEquals("string", properties.getJSONObject("shortcut").getString("type"));
    }

    @Test
    public void executeReturnsStructuredDefinitionForRegisteredShortcut() throws Exception {
        ShortcutRuntime runtime = new ShortcutRuntime();
        runtime.register(new RecordingShortcutExecutor());
        DescribeShortcutChannel channel = new DescribeShortcutChannel(runtime);

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("shortcut", "send_im_message")).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("describe_shortcut", result.getString("channel"));
        assertEquals("send_im_message", result.getString("shortcut"));

        JSONObject definition = result.getJSONObject("definition");
        assertEquals("send_im_message", definition.getString("name"));
        assertEquals("im", definition.getString("domain"));
        assertEquals("im_sender", definition.getString("requiredSkill"));
        assertEquals("write", definition.getString("risk"));
        assertTrue(definition.has("argsSchema"));
        assertTrue(definition.has("argsExample"));
    }

    @Test
    public void executeReturnsShortcutNotSupportedWhenMissing() throws Exception {
        ShortcutRuntime runtime = new ShortcutRuntime();
        DescribeShortcutChannel channel = new DescribeShortcutChannel(runtime);

        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("shortcut", "unknown_shortcut")).toJsonString());

        assertEquals(false, result.getBoolean("success"));
        assertEquals("shortcut_not_supported", result.getString("error"));
        assertEquals("describe_shortcut", result.getString("channel"));
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
            return ToolResult.success();
        }
    }
}
