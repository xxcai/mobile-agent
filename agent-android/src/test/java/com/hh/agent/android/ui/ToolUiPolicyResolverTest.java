package com.hh.agent.android.ui;

import com.hh.agent.android.channel.AndroidToolChannelExecutor;
import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ToolUiPolicyResolverTest {

    @Test
    public void exposesConcreteToolMetadataForLegacyAndroidChannel() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "call_android_tool",
                "{\"function\":\"send_im_message\",\"args\":{\"message\":\"hi\"}}"
        );

        assertTrue(decision.isVisible());
        assertEquals("发送消息", decision.getTitle());
        assertEquals("向指定联系人发送文本消息", decision.getDescription());
    }

    @Test
    public void exposesConcreteToolMetadataForQuotedLegacyArguments() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "call_android_tool",
                "\"{\\\"function\\\":\\\"send_im_message\\\",\\\"args\\\":{\\\"message\\\":\\\"hi\\\"}}\""
        );

        assertTrue(decision.isVisible());
        assertEquals("发送消息", decision.getTitle());
        assertEquals("向指定联系人发送文本消息", decision.getDescription());
    }

    @Test
    public void hidesGestureChannelTools() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "android_gesture_tool",
                "{\"action\":\"tap\",\"x\":120,\"y\":240}"
        );

        assertFalse(decision.isVisible());
        assertNull(decision.getTitle());
        assertNull(decision.getDescription());
    }

    @Test
    public void hidesCppToolsByDefault() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "read_file",
                "{\"path\":\"skills/demo/SKILL.md\"}"
        );

        assertFalse(decision.isVisible());
        assertNull(decision.getTitle());
        assertNull(decision.getDescription());
    }

    @Test
    public void hidesLegacyChannelWhenConcreteToolMissing() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "call_android_tool",
                "{\"args\":{\"message\":\"hi\"}}"
        );

        assertFalse(decision.isVisible());
        assertNull(decision.getTitle());
        assertNull(decision.getDescription());
    }

    @Test
    public void hidesLegacyChannelWhenToolTitleMissing() {
        Map<String, AndroidToolChannelExecutor> channels = new HashMap<>();
        channels.put("empty_title_channel", new AndroidToolChannelExecutor() {
            @Override
            public String getChannelName() {
                return "empty_title_channel";
            }

            @Override
            public JSONObject buildToolDefinition() {
                return new JSONObject();
            }

            @Override
            public ToolResult execute(JSONObject params) {
                return ToolResult.success();
            }

            @Override
            public boolean shouldExposeInnerToolInToolUi() {
                return true;
            }

            @Override
            public ToolUiDecision resolveInnerToolUiDecision(String argumentsJson) {
                return ToolUiDecision.visible("   ", "描述");
            }
        });
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(channels);

        ToolUiDecision decision = resolver.resolve(
                "empty_title_channel",
                "{\"function\":\"send_im_message\",\"args\":{\"message\":\"hi\"}}"
        );

        assertFalse(decision.isVisible());
        assertNull(decision.getTitle());
        assertNull(decision.getDescription());
    }

    @Test
    public void keepsVisibleWhenDescriptionMissing() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannelsWithNullDescription());

        ToolUiDecision decision = resolver.resolve(
                "call_android_tool",
                "{\"function\":\"send_im_message\",\"args\":{\"message\":\"hi\"}}"
        );

        assertTrue(decision.isVisible());
        assertEquals("发送消息", decision.getTitle());
        assertNull(decision.getDescription());
    }

    private Map<String, AndroidToolChannelExecutor> buildChannels() {
        Map<String, AndroidToolChannelExecutor> channels = new HashMap<>();
        channels.put("call_android_tool", createLegacyLikeChannel("发送消息", "向指定联系人发送文本消息"));
        channels.put(GestureToolChannel.CHANNEL_NAME, new GestureToolChannel());
        return channels;
    }

    private Map<String, AndroidToolChannelExecutor> buildChannelsWithNullDescription() {
        Map<String, AndroidToolChannelExecutor> channels = new HashMap<>();
        channels.put("call_android_tool", createLegacyLikeChannel("发送消息", null));
        channels.put(GestureToolChannel.CHANNEL_NAME, new GestureToolChannel());
        return channels;
    }

    private AndroidToolChannelExecutor createLegacyLikeChannel(
            final String title,
            final String description
    ) {
        return new AndroidToolChannelExecutor() {
            @Override
            public String getChannelName() {
                return "call_android_tool";
            }

            @Override
            public JSONObject buildToolDefinition() {
                return new JSONObject();
            }

            @Override
            public ToolResult execute(JSONObject params) {
                return ToolResult.success();
            }

            @Override
            public boolean shouldExposeInnerToolInToolUi() {
                return true;
            }

            @Override
            public ToolUiDecision resolveInnerToolUiDecision(String argumentsJson) {
                String normalizedArguments = normalizeArgumentsJson(argumentsJson);
                if (normalizedArguments == null
                        || !normalizedArguments.contains("\"function\":\"send_im_message\"")) {
                    return ToolUiDecision.hidden();
                }
                return ToolUiDecision.visible(title, description);
            }
        };
    }

    private String normalizeArgumentsJson(String argumentsJson) {
        if (argumentsJson == null) {
            return null;
        }
        String normalized = argumentsJson.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() >= 2
                && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return normalized;
    }
}
