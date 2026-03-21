package com.hh.agent.android.ui;

import com.hh.agent.core.model.Message;
import com.hh.agent.core.model.ToolCall;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MessageAdapterToolUiStateTest {

    @Test
    public void showsToolUiOnlyForActiveResponseCard() {
        Message message = buildResponseMessage(1001L, true);

        assertTrue(MessageAdapter.shouldShowToolUi(message, 1001L));
        assertFalse(MessageAdapter.shouldShowToolUi(message, 1002L));
    }

    @Test
    public void hidesToolUiForHistoryAssistantMessageEvenWhenFlagIsSet() {
        Message message = new Message();
        message.setRole("assistant");
        message.setShowToolUi(true);
        message.setTimestamp(1001L);

        assertFalse(MessageAdapter.shouldShowToolUi(message, 1001L));
    }

    @Test
    public void hidesToolUiWhenMessageFlagIsFalse() {
        Message message = buildResponseMessage(1001L, false);

        assertFalse(MessageAdapter.shouldShowToolUi(message, 1001L));
    }

    @Test
    public void filtersOnlyVisibleToolCalls() {
        Message message = buildResponseMessage(1001L, true);
        ToolCall visibleTool = new ToolCall("tool-1", "send_im_message");
        visibleTool.setVisibleInToolUi(true);
        ToolCall hiddenTool = new ToolCall("tool-2", "android_gesture_tool");
        hiddenTool.setVisibleInToolUi(false);
        message.addToolCall(visibleTool);
        message.addToolCall(hiddenTool);

        List<ToolCall> visibleToolCalls = MessageAdapter.filterVisibleToolCalls(message);

        assertEquals(1, visibleToolCalls.size());
        assertSame(visibleTool, visibleToolCalls.get(0));
    }

    @Test
    public void keepsVisibleToolCallDataStableForRebind() {
        Message message = buildResponseMessage(1001L, true);
        ToolCall toolCall = new ToolCall("tool-1", "send_im_message");
        toolCall.setVisibleInToolUi(true);
        toolCall.setTitle("发送消息");
        toolCall.setDescription("向指定联系人或会话发送文本消息");
        toolCall.setStatus("completed");
        message.addToolCall(toolCall);

        List<ToolCall> visibleToolCalls = MessageAdapter.filterVisibleToolCalls(message);

        assertEquals(1, visibleToolCalls.size());
        assertEquals("发送消息", visibleToolCalls.get(0).getTitle());
        assertEquals("向指定联系人或会话发送文本消息", visibleToolCalls.get(0).getDescription());
        assertEquals("completed", visibleToolCalls.get(0).getStatus());
    }

    private Message buildResponseMessage(long timestamp, boolean showToolUi) {
        Message message = new Message();
        message.setRole("response");
        message.setTimestamp(timestamp);
        message.setShowToolUi(showToolUi);
        return message;
    }
}
