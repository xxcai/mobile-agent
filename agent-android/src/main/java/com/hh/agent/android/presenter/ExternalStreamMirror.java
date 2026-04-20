package com.hh.agent.android.presenter;

import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.core.event.AgentEventListener;
import com.hh.agent.core.model.Message;
import com.hh.agent.core.model.ToolCall;

final class ExternalStreamMirror implements AgentEventListener {
    interface Listener {
        void onUserMessage(Message message);

        void onStreamUpdate(Message message);

        void onStreamEnd(Message message, String finishReason);

        void onStreamError(String errorCode, String errorMessage);
    }

    private final Listener listener;
    private final Message assistantMessage;
    private final StringBuilder accumulatedContent = new StringBuilder();
    private final StringBuilder accumulatedReasoning = new StringBuilder();

    ExternalStreamMirror(String prompt, Listener listener) {
        this.listener = listener;

        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        userMessage.setTimestamp(System.currentTimeMillis());
        listener.onUserMessage(userMessage);

        assistantMessage = new Message();
        assistantMessage.setRole("response");
        assistantMessage.setTimestamp(System.currentTimeMillis());
    }

    @Override
    public void onTextDelta(String text) {
        if (text != null && !text.isEmpty()) {
            accumulatedContent.append(text);
        }
        syncAssistantMessageContent();
        listener.onStreamUpdate(assistantMessage);
    }

    @Override
    public void onReasoningDelta(String text) {
        if (text != null && !text.isEmpty()) {
            accumulatedReasoning.append(text);
        }
        syncAssistantMessageContent();
        listener.onStreamUpdate(assistantMessage);
    }

    @Override
    public void onToolUse(String id, String name, String argumentsJson) {
        ToolCall toolCall = new ToolCall(id, name);
        toolCall.setArguments(argumentsJson);
        ToolUiDecision toolUiDecision = AndroidToolManager.resolveToolUiDecision(name, argumentsJson);
        toolCall.setTitle(toolUiDecision.getTitle());
        toolCall.setDescription(toolUiDecision.getDescription());
        toolCall.setVisibleInToolUi(toolUiDecision.isVisible());
        assistantMessage.addToolCall(toolCall);
        listener.onStreamUpdate(assistantMessage);
    }

    @Override
    public void onToolResult(String id, String result) {
        ToolCall toolCall = assistantMessage.getToolCall(id);
        if (toolCall != null) {
            toolCall.setResult(result);
            toolCall.setStatus("completed");
        }
        listener.onStreamUpdate(assistantMessage);
    }

    @Override
    public void onMessageEnd(String finishReason) {
        listener.onStreamEnd(assistantMessage, finishReason);
    }

    @Override
    public void onError(String errorCode, String errorMessage) {
        listener.onStreamError(errorCode, errorMessage);
    }

    private void syncAssistantMessageContent() {
        assistantMessage.setThinkContent(toNullableString(accumulatedReasoning));
        assistantMessage.setContent(toNullableString(accumulatedContent));
    }

    private String toNullableString(StringBuilder builder) {
        return builder.length() == 0 ? null : builder.toString();
    }
}
