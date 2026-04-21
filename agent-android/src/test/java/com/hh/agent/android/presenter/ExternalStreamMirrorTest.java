package com.hh.agent.android.presenter;

import com.hh.agent.core.model.Message;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class ExternalStreamMirrorTest {
    @Test
    public void mirrorRendersPromptAndStreamUpdates() {
        RecordingListener listener = new RecordingListener();
        ExternalStreamMirror mirror = new ExternalStreamMirror("当前测试任务：click-test-2", listener);

        assertEquals(1, listener.userMessages.size());
        assertEquals("当前测试任务：click-test-2", listener.userMessages.get(0).getContent());

        mirror.onReasoningDelta("页面包含按钮 ONE。");
        mirror.onToolUse("tool-1", "android_view_context_tool", "{}");
        mirror.onToolResult("tool-1", "{\"pageSummary\":\"按钮 ONE 可点击\"}");
        mirror.onTextDelta("我将点击按钮 ONE。");
        mirror.onMessageEnd("stop");

        assertFalse(listener.updatedMessages.isEmpty());
        Message latest = listener.updatedMessages.get(listener.updatedMessages.size() - 1);
        assertEquals("我将点击按钮 ONE。", latest.getContent());
        assertEquals("页面包含按钮 ONE。", latest.getThinkContent());
        assertEquals(1, latest.getToolCalls().size());
        assertEquals("android_view_context_tool", latest.getToolCalls().get(0).getName());
        assertEquals("{\"pageSummary\":\"按钮 ONE 可点击\"}", latest.getToolCalls().get(0).getResult());
        assertNotNull(listener.endedMessage);
        assertEquals("stop", listener.finishReason);
    }

    private static final class RecordingListener implements ExternalStreamMirror.Listener {
        private final List<Message> userMessages = new ArrayList<>();
        private final List<Message> updatedMessages = new ArrayList<>();
        private Message endedMessage;
        private String finishReason;

        @Override
        public void onUserMessage(Message message) {
            userMessages.add(copyMessage(message));
        }

        @Override
        public void onStreamUpdate(Message message) {
            updatedMessages.add(copyMessage(message));
        }

        @Override
        public void onStreamEnd(Message message, String finishReason) {
            endedMessage = copyMessage(message);
            this.finishReason = finishReason;
        }

        @Override
        public void onStreamError(String errorCode, String errorMessage) {
            throw new AssertionError("unexpected error: " + errorCode + " " + errorMessage);
        }

        private Message copyMessage(Message source) {
            Message message = new Message();
            message.setRole(source.getRole());
            message.setContent(source.getContent());
            message.setThinkContent(source.getThinkContent());
            message.setTimestamp(source.getTimestamp());
            message.setShowToolUi(source.isShowToolUi());
            message.setToolCalls(new ArrayList<>(source.getToolCalls()));
            return message;
        }
    }
}
