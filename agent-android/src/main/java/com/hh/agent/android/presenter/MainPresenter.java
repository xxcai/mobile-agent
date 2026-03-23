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

import java.util.List;

/**
 * MainActivity 的 Presenter 实现
 * 使用 Native C++ Agent
 */
public class MainPresenter implements MainContract.Presenter {

    private static MainPresenter instance;

    private MainContract.MessageListView messageListView;
    private MainContract.StreamingView streamingView;
    private final MobileAgentApi mobileAgentApi;
    private final StreamingManager streamingManager;
    private final Handler mainHandler;
    private final String sessionKey;
    // 累积文本，用于全量解析
    private StringBuilder accumulatedText = new StringBuilder();

    /**
     * 流式文本解析器 - 用于区分 think 块和正文块（全量解析）
     * 每次 parse 调用时传入累积的所有文本，从中解析出 thinking 和正文部分
     */
    private static class StreamTextParser {
        private static final String THINK_START = "<think>";
        private static final String THINK_END = "</think>";

        /**
         * 解析文本，根据标签返回对应的内容（全量解析）
         * @param text 累积的所有文本
         * @return 解析结果，包含完整的 think 内容 和正文内容
         */
        ParsedResult parse(String text) {
            ParsedResult result = new ParsedResult();

            if (text == null || text.isEmpty()) {
                return result;
            }

            int cursor = 0;
            StringBuilder thinkBuilder = new StringBuilder();
            StringBuilder contentBuilder = new StringBuilder();

            while (cursor < text.length()) {
                int thinkStart = text.indexOf(THINK_START, cursor);
                if (thinkStart < 0) {
                    appendIfNotEmpty(contentBuilder, text.substring(cursor));
                    break;
                }

                result.hasThinkStart = true;
                appendIfNotEmpty(contentBuilder, text.substring(cursor, thinkStart));

                int thinkContentStart = thinkStart + THINK_START.length();
                int thinkEnd = text.indexOf(THINK_END, thinkContentStart);
                if (thinkEnd < 0) {
                    appendThinkIfNotEmpty(thinkBuilder, text.substring(thinkContentStart));
                    break;
                }

                result.hasThinkEnd = true;
                appendThinkIfNotEmpty(thinkBuilder, text.substring(thinkContentStart, thinkEnd));
                cursor = thinkEnd + THINK_END.length();
            }

            result.thinkContent = toNullableString(thinkBuilder);
            result.contentContent = toNullableString(contentBuilder);
            result.thinkLength = result.thinkContent != null ? result.thinkContent.length() : 0;
            result.contentLength = result.contentContent != null ? result.contentContent.length() : 0;

            return result;
        }

        /**
         * 重置解析器状态（新消息开始时调用）
         */
        void reset() {
            // 全量解析不需要维护状态，无需重置
        }

        private static void appendIfNotEmpty(StringBuilder builder, String text) {
            if (text != null && !text.isEmpty()) {
                builder.append(text);
            }
        }

        private static void appendThinkIfNotEmpty(StringBuilder builder, String text) {
            if (text == null) {
                return;
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(trimmed);
        }

        private static String toNullableString(StringBuilder builder) {
            return builder.length() == 0 ? null : builder.toString();
        }

        static class ParsedResult {
            String thinkContent;    // think 内容（已去掉标签，多个块用换行拼接）
            String contentContent;  // 完整的正文内容
            boolean hasThinkStart;
            boolean hasThinkEnd;
            int thinkLength;
            int contentLength;

            @Override
            public String toString() {
                return "ParsedResult{" +
                        "thinkContent='" + thinkContent + '\'' +
                        ", contentContent='" + contentContent + '\'' +
                        ", hasThinkStart=" + hasThinkStart +
                        ", hasThinkEnd=" + hasThinkEnd +
                        ", thinkLength=" + thinkLength +
                        ", contentLength=" + contentLength +
                        '}';
            }
        }
    }

    // 每个流式请求创建一个解析器
    private StreamTextParser currentParser = new StreamTextParser();

    /**
     * 获取单例实例
     * 进程生命周期内只有一个 MainPresenter 实例
     */
    public static synchronized MainPresenter getInstance() {
        if (instance == null) {
            instance = new MainPresenter();
        }
        return instance;
    }

    /**
     * 私有构造函数
     */
    private MainPresenter() {
        this.mobileAgentApi = NativeMobileAgentApi.getInstance();
        this.streamingManager = new StreamingManager(mobileAgentApi);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionKey = "native:default";
    }

    /**
     * 兼容旧代码的构造函数
     * @deprecated 使用 getInstance() 代替
     */
    @Deprecated
    public MainPresenter(Context context, String sessionKey) {
        this();
    }

    /**
     * 兼容旧代码的构造函数
     * @deprecated 使用 getInstance() 代替
     */
    @Deprecated
    public MainPresenter(String sessionKey) {
        this();
    }

    /**
     * 获取 MobileAgentApi 实例
     *
     * @return MobileAgentApi 实例
     */
    public MobileAgentApi getMobileAgentApi() {
        return mobileAgentApi;
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

        // 显示思考中提示（同步执行，确保 thinking 消息在 API 调用前创建）
        if (streamingView != null) {
            streamingView.showThinking();
        }

        if (messageListView != null) {
            messageListView.showLoading();
        }

        AgentLogs.info(TAG, "stream_start",
                "session_key=" + sessionKey + " input_length=" + content.length());

        // 设置 StreamingManager 回调，将事件转发到 streamingView
        streamingManager.setCallback(new StreamingManager.StreamingCallback() {
            @Override
            public void onTextDelta(String text) {
                if (streamingView != null) {
                    // 累积所有文本，用于全量解析
                    accumulatedText.append(text);

                    // 解析文本，区分 think 块和正文块（全量解析）
                    StreamTextParser.ParsedResult result = currentParser.parse(accumulatedText.toString());

                    if (result.hasThinkStart && !result.hasThinkEnd) {
                        AgentLogs.warn(TAG, "think_parse_incomplete",
                                "session_key=" + sessionKey
                                        + " accumulated_length=" + accumulatedText.length()
                                        + " think_length=" + result.thinkLength
                                        + " content_length=" + result.contentLength);
                    } else if (result.hasThinkStart) {
                        AgentLogs.debug(TAG, "think_parse_complete",
                                "session_key=" + sessionKey
                                        + " think_length=" + result.thinkLength
                                        + " content_length=" + result.contentLength);
                    }

                    // 更新 Message 对象
                    if (result.thinkContent != null && !result.thinkContent.isEmpty()) {
                        assistantMessage.setThinkContent(result.thinkContent);
                    }
                    if (result.contentContent != null && !result.contentContent.isEmpty()) {
                        assistantMessage.setContent(result.contentContent);
                    }

                    mainHandler.post(() -> {
                        // 传递完整的 Message 对象给界面
                        streamingView.onStreamMessageUpdate(assistantMessage);
                    });
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
                    AgentLogs.warn(TAG, "think_parse_error_finish", "session_key=" + sessionKey);
                }
                if ("stop".equals(finishReason) || "tool_calls".equals(finishReason)) {
                    AgentLogs.info(TAG, "stream_finish", "finish_reason=" + finishReason);
                } else {
                    AgentLogs.warn(TAG, "stream_finish_unexpected", "finish_reason=" + finishReason);
                }
                FloatingBallManager floatingBallManager = FloatingBallManager.getInstance();
                if (floatingBallManager != null) {
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

        // 重置文本解析器状态
        currentParser.reset();
        // 清空累积文本
        accumulatedText.setLength(0);

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
    public void attachView(MainContract.MessageListView messageListView, MainContract.StreamingView streamingView) {
        this.messageListView = messageListView;
        this.streamingView = streamingView;
    }

    @Override
    public void detachView() {
        this.messageListView = null;
        this.streamingView = null;
        // 注意：单例模式下不销毁 presenter，保留状态
    }

    /**
     * 销毁Presenter，释放资源
     * 注意：单例模式下不真正销毁，只是解绑 view
     */
    public void destroy() {
        // 关闭统一线程池
        ThreadPoolManager.shutdown();
        // 清除单例引用
        instance = null;
    }
}
