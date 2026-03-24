package com.hh.agent.android.channel;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class LegacyAndroidToolChannelTest {

    @Test
    public void returnsBusinessCapabilityNotSupportedWhenFunctionMissing() throws Exception {
        LegacyAndroidToolChannel channel =
                new LegacyAndroidToolChannel(Collections.emptyMap());

        ToolResult result = channel.execute(testJsonObject()
                .withString("function", "open_current_chat_send_button")
                .withObject("args", testJsonObject()));

        String raw = result.toJsonString();
        assertTrue(raw.contains("\"success\":false"));
        assertTrue(raw.contains("\"error\":\"business_capability_not_supported\""));
        assertTrue(raw.contains("\"channel\":\"call_android_tool\""));
        assertTrue(raw.contains("\"requestedFunction\":\"open_current_chat_send_button\""));
        assertTrue(raw.contains("\"suggestedNextTool\":\"android_view_context_tool\""));
        assertTrue(raw.contains("\"suggestedSource\":\"native_xml\""));
        assertTrue(raw.contains("\"fallbackSuggested\":true"));
    }

    @Test
    public void decoratesBusinessTargetNotAccessibleWithFallbackHints() throws Exception {
        ToolExecutor failingTool = new ToolExecutor() {
            @Override
            public String getName() {
                return "send_im_message";
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.builder("发送消息", "向指定联系人发送文本消息")
                        .stringParam("contact_id", "联系人ID", true, "003")
                        .stringParam("message", "消息内容", true, "test")
                        .build();
            }

            @Override
            public ToolResult execute(JSONObject args) {
                return ToolResult.error("business_target_not_accessible",
                                "The requested contact cannot be reached by send_im_message directly")
                        .with("targetType", "contact_or_conversation")
                        .with("contact_id", "ui_current_chat");
            }
        };

        LegacyAndroidToolChannel channel =
                new LegacyAndroidToolChannel(Collections.singletonMap("send_im_message", failingTool));

        ToolResult result = channel.execute(testJsonObject()
                .withString("function", "send_im_message")
                .withObject("args", testJsonObject()
                        .withString("contact_id", "ui_current_chat")
                        .withString("message", "test")));

        String raw = result.toJsonString();
        assertTrue(raw.contains("\"success\":false"));
        assertTrue(raw.contains("\"error\":\"business_target_not_accessible\""));
        assertTrue(raw.contains("\"channel\":\"call_android_tool\""));
        assertTrue(raw.contains("\"requestedFunction\":\"send_im_message\""));
        assertTrue(raw.contains("\"suggestedNextTool\":\"android_view_context_tool\""));
        assertTrue(raw.contains("\"suggestedSource\":\"native_xml\""));
        assertTrue(raw.contains("\"fallbackSuggested\":true"));
        assertTrue(raw.contains("\"targetType\":\"contact_or_conversation\""));
        assertTrue(raw.contains("\"contact_id\":\"ui_current_chat\""));
    }

    private static TestJsonObject testJsonObject() {
        return new TestJsonObject();
    }

    private static final class TestJsonObject extends org.json.JSONObject {
        private final Map<String, String> stringValues = new HashMap<>();
        private final Map<String, org.json.JSONObject> objectValues = new HashMap<>();

        private TestJsonObject withString(String key, String value) {
            stringValues.put(key, value);
            return this;
        }

        private TestJsonObject withObject(String key, org.json.JSONObject value) {
            objectValues.put(key, value);
            return this;
        }

        @Override
        public String optString(String name, String fallback) {
            String value = stringValues.get(name);
            return value != null ? value : fallback;
        }

        @Override
        public org.json.JSONObject optJSONObject(String name) {
            return objectValues.get(name);
        }

        @Override
        public boolean has(String name) {
            return stringValues.containsKey(name) || objectValues.containsKey(name);
        }
    }
}
