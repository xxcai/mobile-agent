package com.hh.agent.android.presenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.floating.FloatingBallManager;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.thread.ThreadPoolManager;
import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.core.event.AgentEventListener;
import com.hh.agent.core.api.MobileAgentApi;
import com.hh.agent.core.api.impl.NativeMobileAgentApi;
import com.hh.agent.core.model.Message;
import com.hh.agent.core.model.ToolCall;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MainActivity 的 Presenter 实现
 * 使用 Native C++ Agent
 */
public class MainPresenter implements MainContract.Presenter {

    private static final String DEFAULT_SESSION_KEY = "native:default";
    private static final Map<String, MainPresenter> INSTANCES = new HashMap<>();

    private MainContract.MessageListView messageListView;
    private MainContract.StreamingView streamingView;
    private final MobileAgentApi mobileAgentApi;
    private final StreamingManager streamingManager;
    private final Handler mainHandler;
    private final String sessionKey;
    private final StringBuilder accumulatedContent = new StringBuilder();
    private final StringBuilder accumulatedReasoning = new StringBuilder();

    /**
     * 获取单例实例
     * 进程生命周期内只有一个 MainPresenter 实例
     */
    public static synchronized MainPresenter getInstance() {
        return getInstance(DEFAULT_SESSION_KEY);
    }

    public static synchronized MainPresenter getInstance(String sessionKey) {
        String normalizedSessionKey = normalizeSessionKey(sessionKey);
        MainPresenter presenter = INSTANCES.get(normalizedSessionKey);
        if (presenter == null) {
            presenter = new MainPresenter(normalizedSessionKey, true);
            INSTANCES.put(normalizedSessionKey, presenter);
        }
        return presenter;
    }

    /**
     * 私有构造函数
     */
    private MainPresenter(String sessionKey, boolean internalOnly) {
        this.mobileAgentApi = NativeMobileAgentApi.getInstance();
        this.streamingManager = new StreamingManager(mobileAgentApi);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionKey = normalizeSessionKey(sessionKey);
    }

    /**
     * 兼容旧代码的构造函数
     * @deprecated 使用 getInstance() 代替
     */
    @Deprecated
    public MainPresenter(Context context, String sessionKey) {
        this(normalizeSessionKey(sessionKey), true);
    }

    /**
     * 兼容旧代码的构造函数
     * @deprecated 使用 getInstance() 代替
     */
    @Deprecated
    public MainPresenter(String sessionKey) {
        this(normalizeSessionKey(sessionKey), true);
    }

    /**
     * 获取 MobileAgentApi 实例
     *
     * @return MobileAgentApi 实例
     */
    public MobileAgentApi getMobileAgentApi() {
        return mobileAgentApi;
    }

    public boolean isStreaming() {
        return streamingManager.isStreaming();
    }

    public String getSessionKey() {
        return sessionKey;
    }

    private static final String TAG = "MainPresenter";

    @Override
    public void loadMessages() {
        AgentLogs.info(TAG, "history_load_start", "session_key=" + sessionKey);
        if (messageListView != null) {
            mainHandler.post(() -> messageListView.showLoading());
        }

        ThreadPoolManager.executeAgentIO(() -> {
            try {
                List<Message> messages = mobileAgentApi.getHistory(sessionKey, 50);
                AgentLogs.info(TAG, "history_load_success", "session_key=" + sessionKey + " count=" + messages.size());

                if (messageListView != null) {
                    mainHandler.post(() -> {
                        messageListView.hideLoading();
                        // 先加载历史消息
                        messageListView.onMessagesLoaded(messages);
                        // 然后判断是否处于 thinking 状态，如果是则显示思考占位符
                        // 这样可以避免 setMessages() 替换掉思考消息的问题
                        if (streamingManager.isStreaming() && streamingView != null) {
                            streamingView.showThinking();
                            if (messageListView != null) {
                                messageListView.showLoading();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                AgentLogs.error(TAG, "history_load_failed", "session_key=" + sessionKey + " message=" + e.getMessage(), e);
                if (messageListView != null) {
                    mainHandler.post(() -> {
                        messageListView.hideLoading();
                        messageListView.onError("加载消息失败: " + e.getMessage());
                    });
                }
            }
        });
    }

    @Override
    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) {
            if (messageListView != null) {
                mainHandler.post(() -> messageListView.onError("消息不能为空"));
            }
            return;
        }

        // 创建用户消息
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setTimestamp(System.currentTimeMillis());

        // 先通知 View 显示用户消息（同步执行，确保 UI 立即更新）
        if (messageListView != null) {
            messageListView.onUserMessageSent(userMessage);
        }

        // 创建 assistant 消息对象，用于在整个流式过程中更新
        Message assistantMessage = new Message();
        assistantMessage.setRole("response");
        assistantMessage.setTimestamp(System.currentTimeMillis());

        // 先进入 streaming 状态，避免后续 showLoading() 提前禁用“取消”按钮。
        streamingManager.beginStreaming();

        // 显示思考中提示（同步执行，确保 thinking 消息在 API 调用前创建）
        if (streamingView != null) {
            streamingView.showThinking();
        }

        if (messageListView != null) {
            messageListView.showLoading();
        }

        AgentLogs.info(TAG, "stream_start",
                "session_key=" + sessionKey
                        + " input_length=" + content.length());

        // 设置 StreamingManager 回调，将事件转发到 streamingView
        streamingManager.setCallback(new StreamingManager.StreamingCallback() {
            @Override
            public void onTextDelta(String text) {
                if (streamingView != null) {
                    if (text != null && !text.isEmpty()) {
                        accumulatedContent.append(text);
                    }
                    syncAssistantMessageContent(assistantMessage);

                    mainHandler.post(() -> {
                        // 传递完整的 Message 对象给界面
                        streamingView.onStreamMessageUpdate(assistantMessage);
                    });
                }
            }

            @Override
            public void onReasoningDelta(String text) {
                AgentLogs.debug(TAG, "reasoning_delta_received",
                        "session_key=" + sessionKey
                                + " delta_length=" + (text != null ? text.length() : 0));
                if (streamingView != null) {
                    if (text != null && !text.isEmpty()) {
                        accumulatedReasoning.append(text);
                    }
                    syncAssistantMessageContent(assistantMessage);
                    mainHandler.post(() -> streamingView.onStreamMessageUpdate(assistantMessage));
                }
            }

            @Override
            public void onToolUse(String id, String name, String argumentsJson) {
                AgentLogs.info(TAG, "tool_use", "tool_id=" + id + " tool_name=" + name);
                if (streamingView != null) {
                    // 创建 ToolCall 对象并添加到 Message
                    ToolCall toolCall = new ToolCall(id, name);
                    toolCall.setArguments(argumentsJson);
                    ToolUiDecision toolUiDecision = AndroidToolManager.resolveToolUiDecision(name, argumentsJson);
                    toolCall.setTitle(toolUiDecision.getTitle());
                    toolCall.setDescription(toolUiDecision.getDescription());
                    toolCall.setVisibleInToolUi(toolUiDecision.isVisible());
                    assistantMessage.addToolCall(toolCall);

                    mainHandler.post(() -> {
                        streamingView.onStreamMessageUpdate(assistantMessage);
                    });
                }
            }

            @Override
            public void onToolResult(String id, String result) {
                AgentLogs.info(TAG, "tool_result", "tool_id=" + id + " result_length=" + (result != null ? result.length() : 0));
                if (streamingView != null) {
                    // 更新 ToolCall 的结果
                    ToolCall toolCall = assistantMessage.getToolCall(id);
                    if (toolCall != null) {
                        toolCall.setResult(result);
                        toolCall.setStatus("completed");
                    }

                    mainHandler.post(() -> {
                        streamingView.onStreamMessageUpdate(assistantMessage);
                    });
                }
            }

            @Override
            public void onMessageEnd(String finishReason) {
                if ("parse_error".equals(finishReason)) {
                    AgentLogs.warn(TAG, "stream_parse_error_finish", "session_key=" + sessionKey);
                }
                if ("stop".equals(finishReason)
                        || "cancel".equals(finishReason)
                        || "tool_calls".equals(finishReason)
                        || "max_iterations".equals(finishReason)) {
                    AgentLogs.info(TAG, "stream_finish", "finish_reason=" + finishReason);
                } else {
                    AgentLogs.warn(TAG, "stream_finish_unexpected", "finish_reason=" + finishReason);
                }
                FloatingBallManager floatingBallManager = FloatingBallManager.getInstance();
                if (floatingBallManager != null && !"tool_calls".equals(finishReason)) {
                    floatingBallManager.setWorking(false);
                }
                if (streamingView != null && messageListView != null) {
                    mainHandler.post(() -> {
                        streamingView.hideThinking();
                        messageListView.hideLoading();
                        // 传递带有完成状态的 Message
                        streamingView.onStreamMessageEnd(assistantMessage, finishReason);
                    });
                }
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                AgentLogs.error(TAG, "stream_error", "error_code=" + errorCode + " message=" + errorMessage);
                FloatingBallManager floatingBallManager = FloatingBallManager.getInstance();
                if (floatingBallManager != null) {
                    floatingBallManager.setWorking(false);
                }
                if (streamingView != null && messageListView != null) {
                    mainHandler.post(() -> {
                        streamingView.hideThinking();
                        messageListView.hideLoading();
                        streamingView.onStreamError(errorCode, errorMessage);
                    });
                }
            }
        });

        resetStreamingBuffers();

        // 使用 StreamingManager 发送流式消息
        FloatingBallManager floatingBallManager = FloatingBallManager.getInstance();
        if (floatingBallManager != null) {
            floatingBallManager.setWorking(true);
        }
        streamingManager.sendMessageStream(content, sessionKey);
    }

    @Override
    public void cancelStream() {
        if (streamingManager.isStreaming()) {
            AgentLogs.info(TAG, "stream_cancel_requested", "session_key=" + sessionKey);
            // 使用 StreamingManager 取消流式请求
            streamingManager.cancel();
            FloatingBallManager floatingBallManager = FloatingBallManager.getInstance();
            if (floatingBallManager != null) {
                floatingBallManager.setWorking(false);
            }
            if (streamingView != null && messageListView != null) {
                mainHandler.post(() -> {
                    streamingView.hideThinking();
                    messageListView.hideLoading();
                });
            }
        }
    }

    @Override
    public void clearHistory() {
        cancelStream();
        resetStreamingBuffers();
        ThreadPoolManager.executeAgentIO(() -> {
            try {
                boolean success = mobileAgentApi.clearHistory(sessionKey);
                if (!success) {
                    throw new IllegalStateException("native clear history returned false");
                }
                if (messageListView != null) {
                    mainHandler.post(() -> messageListView.onMessagesLoaded(new ArrayList<>()));
                }
            } catch (Exception e) {
                AgentLogs.error(TAG, "history_clear_failed", "session_key=" + sessionKey + " message=" + e.getMessage(), e);
                if (messageListView != null) {
                    mainHandler.post(() -> messageListView.onError("清空聊天历史失败: " + e.getMessage()));
                }
            }
        });
    }

    @Override
    public void clearHistoryAndLongTermMemory() {
        cancelStream();
        resetStreamingBuffers();
        ThreadPoolManager.executeAgentIO(() -> {
            try {
                boolean success = mobileAgentApi.clearHistoryAndLongTermMemory(sessionKey);
                if (!success) {
                    throw new IllegalStateException("native clear history and memory returned false");
                }
                if (messageListView != null) {
                    mainHandler.post(() -> messageListView.onMessagesLoaded(new ArrayList<>()));
                }
            } catch (Exception e) {
                AgentLogs.error(TAG, "memory_clear_failed", "session_key=" + sessionKey + " message=" + e.getMessage(), e);
                if (messageListView != null) {
                    mainHandler.post(() -> messageListView.onError("清空聊天历史和长期记忆失败: " + e.getMessage()));
                }
            }
        });
    }

    @Override
    public void attachView(MainContract.MessageListView messageListView, MainContract.StreamingView streamingView) {
        this.messageListView = messageListView;
        this.streamingView = streamingView;
    }

    @Override
    public void detachView() {
        this.messageListView = null;
        this.streamingView = null;
        resetStreamingBuffers();
        // 注意：单例模式下不销毁 presenter，保留状态
    }

    /**
     * 销毁Presenter，释放资源
     * 注意：单例模式下不真正销毁，只是解绑 view
     */
    public void destroy() {
        // 关闭统一线程池
        ThreadPoolManager.shutdown();
        synchronized (MainPresenter.class) {
            INSTANCES.remove(sessionKey);
        }
    }

    private static String normalizeSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            return DEFAULT_SESSION_KEY;
        }
        return sessionKey.trim();
    }

    private void syncAssistantMessageContent(Message assistantMessage) {
        assistantMessage.setThinkContent(toNullableString(accumulatedReasoning));
        assistantMessage.setContent(toNullableString(accumulatedContent));
    }

    private void resetStreamingBuffers() {
        accumulatedContent.setLength(0);
        accumulatedReasoning.setLength(0);
    }

    private static String toNullableString(StringBuilder builder) {
        return builder.length() == 0 ? null : builder.toString();
    }
}
