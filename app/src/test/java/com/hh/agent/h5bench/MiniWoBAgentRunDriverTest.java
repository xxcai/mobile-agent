package com.hh.agent.h5bench;

import com.hh.agent.core.api.MobileAgentApi;
import com.hh.agent.core.event.AgentEventListener;
import com.hh.agent.core.model.Message;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class MiniWoBAgentRunDriverTest {
    @Test
    public void runTaskMirrorsConversationEventsToProvidedListener() throws Exception {
        FakeMobileAgentApi api = new FakeMobileAgentApi();
        AtomicReference<MiniWoBTaskDefinition> mirroredTask = new AtomicReference<>();
        RecordingAgentEventListener mirroredListener = new RecordingAgentEventListener();
        MiniWoBAgentRunDriver driver = new MiniWoBAgentRunDriver(
                api,
                task -> {
                    mirroredTask.set(task);
                    return mirroredListener;
                });
        MiniWoBTaskDefinition task = new MiniWoBTaskDefinition(
                "click-test-2",
                "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                "Click the highlighted area.",
                "click",
                1,
                5,
                1000
        );

        int steps = driver.runTask(task);

        assertEquals(task, mirroredTask.get());
        assertEquals("页面上下文", mirroredListener.reasoningDelta);
        assertEquals("android_view_context_tool", mirroredListener.toolName);
        assertEquals("{\"pageSummary\":\"按钮 ONE 可点击\"}", mirroredListener.toolResult);
        assertEquals("我将点击按钮 ONE。", mirroredListener.textDelta);
        assertEquals("stop", mirroredListener.finishReason);
        assertEquals(1, steps);
    }

    private static final class FakeMobileAgentApi implements MobileAgentApi {
        @Override
        public List<Message> getHistory(String sessionKey, int maxMessages) {
            return Collections.emptyList();
        }

        @Override
        public void sendMessageStream(String content, String sessionKey, AgentEventListener listener) {
            listener.onReasoningDelta("页面上下文");
            listener.onToolUse("tool-1", "android_view_context_tool", "{}");
            listener.onToolResult("tool-1", "{\"pageSummary\":\"按钮 ONE 可点击\"}");
            listener.onTextDelta("我将点击按钮 ONE。");
            listener.onMessageEnd("stop");
        }

        @Override
        public boolean clearHistory(String sessionKey) {
            return true;
        }

        @Override
        public boolean clearHistoryAndLongTermMemory(String sessionKey) {
            return true;
        }

        @Override
        public boolean clearDailyMemory() {
            return true;
        }
    }

    private static final class RecordingAgentEventListener implements AgentEventListener {
        private String textDelta;
        private String reasoningDelta;
        private String toolName;
        private String toolResult;
        private String finishReason;

        @Override
        public void onTextDelta(String text) {
            textDelta = text;
        }

        @Override
        public void onReasoningDelta(String text) {
            reasoningDelta = text;
        }

        @Override
        public void onToolUse(String id, String name, String argumentsJson) {
            toolName = name;
        }

        @Override
        public void onToolResult(String id, String result) {
            toolResult = result;
        }

        @Override
        public void onMessageEnd(String finishReason) {
            this.finishReason = finishReason;
        }

        @Override
        public void onError(String errorCode, String errorMessage) {
            throw new AssertionError("unexpected error: " + errorCode + " " + errorMessage);
        }
    }
}
