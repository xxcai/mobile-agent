package com.hh.agent.android.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ToolDisplayNameResolverTest {

    @Test
    public void resolvesLegacyAndroidToolFunctionName() {
        String displayName = ToolDisplayNameResolver.resolve(
                "call_android_tool",
                "{\"function\":\"send_im_message\",\"args\":{\"message\":\"hi\"}}"
        );

        assertEquals("send_im_message", displayName);
    }

    @Test
    public void resolvesGestureActionName() {
        String displayName = ToolDisplayNameResolver.resolve(
                "android_gesture_tool",
                "{\"action\":\"tap\",\"x\":120,\"y\":240}"
        );

        assertEquals("gesture_tap", displayName);
    }

    @Test
    public void fallsBackToRawNameForNonChannelTool() {
        String displayName = ToolDisplayNameResolver.resolve(
                "read_file",
                "{\"path\":\"skills/demo/SKILL.md\"}"
        );

        assertEquals("read_file", displayName);
    }

    @Test
    public void returnsNullWhenLegacyToolFunctionMissing() {
        String displayName = ToolDisplayNameResolver.resolve(
                "call_android_tool",
                "{\"args\":{\"message\":\"hi\"}}"
        );

        assertNull(displayName);
    }
}
