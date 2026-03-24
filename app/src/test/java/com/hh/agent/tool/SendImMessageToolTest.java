package com.hh.agent.tool;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SendImMessageToolTest {

    @Test
    public void returnsBusinessTargetNotAccessibleForUiCurrentChatSentinel() {
        SendImMessageTool tool = new SendImMessageTool();

        String raw = tool.execute(testJsonObject()
                        .withString("contact_id", "ui_current_chat")
                        .withString("message", "test"))
                .toJsonString();

        assertTrue(raw.contains("\"success\":false"));
        assertTrue(raw.contains("\"error\":\"business_target_not_accessible\""));
        assertTrue(raw.contains("\"targetType\":\"contact_or_conversation\""));
        assertTrue(raw.contains("\"contact_id\":\"ui_current_chat\""));
        assertTrue(raw.contains("\"fallbackSuggested\":true"));
    }

    @Test
    public void returnsSuccessForKnownContactId() {
        SendImMessageTool tool = new SendImMessageTool();

        String raw = tool.execute(testJsonObject()
                        .withString("contact_id", "003")
                        .withString("message", "hello"))
                .toJsonString();

        assertTrue(raw.contains("\"success\":true"));
        assertTrue(raw.contains("\"result\":\"消息已发送给 003: hello\""));
    }

    private static TestJsonObject testJsonObject() {
        return new TestJsonObject();
    }

    private static final class TestJsonObject extends org.json.JSONObject {
        private final java.util.Map<String, String> stringValues = new java.util.HashMap<>();

        private TestJsonObject withString(String key, String value) {
            stringValues.put(key, value);
            return this;
        }

        @Override
        public boolean has(String name) {
            return stringValues.containsKey(name);
        }

        @Override
        public String getString(String name) {
            return stringValues.get(name);
        }
    }
}
