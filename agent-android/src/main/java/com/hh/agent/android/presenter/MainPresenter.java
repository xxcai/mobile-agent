package com.hh.agent.android.presenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.thread.ThreadPoolManager;
import com.hh.agent.core.AgentEventListener;
import com.hh.agent.core.api.MobileAgentApi;
import com.hh.agent.core.api.NativeMobileAgentApi;
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
            Log.d(TAG, "StreamTextParser parse (multi-think): text=" + text);
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

            Log.d(TAG, "StreamTextParser result = " + result);
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

            @Override
            public String toString() {
                return "ParsedResult{" +
                        "thinkContent='" + thinkContent + '\'' +
                        ", contentContent='" + contentContent + '\'' +
                        ", hasThinkStart=" + hasThinkStart +
                        ", hasThinkEnd=" + hasThinkEnd +
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
        Log.d(TAG, "loadMessages: start, sessionKey=" + sessionKey);
        if (messageListView != null) {
            mainHandler.post(() -> messageListView.showLoading());
        }

        ThreadPoolManager.executeAgentIO(() -> {
            try {
                Log.d(TAG, "loadMessages: calling getHistory, sessionKey=" + sessionKey);
                List<Message> messages = mobileAgentApi.getHistory(sessionKey, 50);
                Log.d(TAG, "loadMessages: got " + messages.size() + " messages");

                if (messageListView != null) {
                    mainHandler.post(() -> {
                        messageListView.hideLoading();
                        // 先加载历史消息
                        Log.d(TAG, "loadMessages: calling onMessagesLoaded with " + messages.size() + " messages");
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

        // 设置 StreamingManager 回调，将事件转发到 streamingView
        streamingManager.setCallback(new StreamingManager.StreamingCallback() {
            @Override
            public void onTextDelta(String text) {
                Log.d("MainPresenter", "onTextDelta: text=" + text);
                if (streamingView != null) {
                    // 累积所有文本，用于全量解析
                    accumulatedText.append(text);

                    // 解析文本，区分 think 块和正文块（全量解析）
                    StreamTextParser.ParsedResult result = currentParser.parse(accumulatedText.toString());

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
                Log.d("MainPresenter", "onToolUse: id=" + id);
                if (streamingView != null) {
                    // 创建 ToolCall 对象并添加到 Message
                    ToolCall toolCall = new ToolCall(id, name);
                    toolCall.setArguments(argumentsJson);
                    assistantMessage.addToolCall(toolCall);

                    mainHandler.post(() -> {
                        streamingView.onStreamMessageUpdate(assistantMessage);
                    });
                }
            }

            @Override
            public void onToolResult(String id, String result) {
                Log.d("MainPresenter", "onToolResult: id=" + id);
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
                Log.d("MainPresenter", "onMessageEnd: finishReason=" + finishReason);
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
                Log.d("MainPresenter", "onError: errorCode=" + errorCode + ", errorMessage=" +errorMessage);
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
        streamingManager.sendMessageStream(content, sessionKey);
    }

    @Override
    public void cancelStream() {
        if (streamingManager.isStreaming()) {
            // 使用 StreamingManager 取消流式请求
            streamingManager.cancel();
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
