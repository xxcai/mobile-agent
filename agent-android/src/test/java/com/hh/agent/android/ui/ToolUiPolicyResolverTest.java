package com.hh.agent.android.ui;

import com.hh.agent.android.channel.AndroidToolChannelExecutor;
import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.LegacyAndroidToolChannel;
import com.hh.agent.core.ToolExecutor;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ToolUiPolicyResolverTest {

    @Test
    public void exposesConcreteToolNameForLegacyAndroidChannel() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "call_android_tool",
                "{\"function\":\"send_im_message\",\"args\":{\"message\":\"hi\"}}"
        );

        assertTrue(decision.isVisible());
        assertEquals("send_im_message", decision.getDisplayName());
    }

    @Test
    public void exposesConcreteToolNameForQuotedLegacyArguments() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "call_android_tool",
                "\"{\\\"function\\\":\\\"send_im_message\\\",\\\"args\\\":{\\\"message\\\":\\\"hi\\\"}}\""
        );

        assertTrue(decision.isVisible());
        assertEquals("send_im_message", decision.getDisplayName());
    }

    @Test
    public void hidesGestureChannelTools() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "android_gesture_tool",
                "{\"action\":\"tap\",\"x\":120,\"y\":240}"
        );

        assertFalse(decision.isVisible());
        assertNull(decision.getDisplayName());
    }

    @Test
    public void hidesCppToolsByDefault() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "read_file",
                "{\"path\":\"skills/demo/SKILL.md\"}"
        );

        assertFalse(decision.isVisible());
        assertNull(decision.getDisplayName());
    }

    @Test
    public void hidesLegacyChannelWhenConcreteToolMissing() {
        ToolUiPolicyResolver resolver = new ToolUiPolicyResolver(buildChannels());

        ToolUiDecision decision = resolver.resolve(
                "call_android_tool",
                "{\"args\":{\"message\":\"hi\"}}"
        );

        assertFalse(decision.isVisible());
        assertNull(decision.getDisplayName());
    }

    private Map<String, AndroidToolChannelExecutor> buildChannels() {
        Map<String, ToolExecutor> tools = new HashMap<>();
        Map<String, AndroidToolChannelExecutor> channels = new HashMap<>();
        channels.put(LegacyAndroidToolChannel.CHANNEL_NAME, new LegacyAndroidToolChannel(tools));
        channels.put(GestureToolChannel.CHANNEL_NAME, new GestureToolChannel());
        return channels;
    }
}
