package com.hh.agent.android.presenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.presenter.NativeMobileAgentApiAdapter;
import com.hh.agent.android.thread.ThreadPoolManager;
import com.hh.agent.library.AgentEventListener;
import com.hh.agent.library.api.MobileAgentApi;
import com.hh.agent.library.api.NativeMobileAgentApi;
import com.hh.agent.library.model.Message;

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

    /**
     * 流式文本解析器 - 用于区分 think 块和正文块
     */
    private static class StreamTextParser {
        // 当前块类型
        private BlockType currentBlockType = BlockType.CONTENT;
        // 累积的 think 内容
        private StringBuilder thinkContent = new StringBuilder();
        // 累积的正文内容
        private StringBuilder contentText = new StringBuilder();

        enum BlockType {
            THINK,   // think 块
            CONTENT // 正文块
        }

        /**
         * 解析文本，根据标签返回对应的增量
         * @param text 输入的增量文本
         * @return 解析结果，包含类型和实际内容
         */
        ParsedResult parse(String text) {
            ParsedResult result = new ParsedResult();

            // 检查是否包含开始标签
            if (text.contains("<think>")) {
                currentBlockType = BlockType.THINK;
                result.hasThinkStart = true;
            }

            // 检查是否包含结束标签
            if (text.contains("</think>")) {
                // 遇到结束标签，think 块结束
                result.hasThinkEnd = true;
            }

            // 处理文本
            if (currentBlockType == BlockType.THINK) {
                // 如果包含结束标签，需要拆分
                if (result.hasThinkEnd) {
                    // 分割文本
                    int endIndex = text.indexOf("</think>");
                    String thinkPart = text.substring(0, endIndex + 6); // 包含 </think> 标签
                    String contentPart = text.substring(endIndex + 6);

                    thinkContent.append(thinkPart);
                    result.thinkDelta = thinkPart;
                    result.contentDelta = contentPart;

                    // think 结束，切换到正文
                    currentBlockType = BlockType.CONTENT;
                    thinkContent = new StringBuilder(); // 清空累积
                } else {
                    // 纯 think 内容
                    thinkContent.append(text);
                    result.thinkDelta = text;
                }
            } else {
                // 正文块
                contentText.append(text);
                result.contentDelta = text;
            }

            return result;
        }

        /**
         * 重置解析器状态
         */
        void reset() {
            currentBlockType = BlockType.CONTENT;
            thinkContent = new StringBuilder();
            contentText = new StringBuilder();
        }

        static class ParsedResult {
            String thinkDelta;
            String contentDelta;
            boolean hasThinkStart;
            boolean hasThinkEnd;
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
        this.mobileAgentApi = new NativeMobileAgentApiAdapter();
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
                // 确保会话存在
                mobileAgentApi.getSession(sessionKey);

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
                    // 解析文本，区分 think 块和正文块
                    StreamTextParser.ParsedResult result = currentParser.parse(text);

                    mainHandler.post(() -> {
                        // 调用原有的 onStreamTextDelta 保持兼容
                        streamingView.onStreamTextDelta(text);

                        // 分别调用 think 和正文回调
                        if (result.thinkDelta != null && !result.thinkDelta.isEmpty()) {
                            streamingView.onStreamThinkDelta(result.thinkDelta);
                        }
                        if (result.contentDelta != null && !result.contentDelta.isEmpty()) {
                            streamingView.onStreamContentDelta(result.contentDelta);
                        }
                    });
                }
            }

            @Override
            public void onToolUse(String id, String name, String argumentsJson) {
                Log.d("MainPresenter", "onToolUse: id=" + id);
                if (streamingView != null) {
                    mainHandler.post(() -> streamingView.onStreamToolUse(id, name, argumentsJson));
                }
            }

            @Override
            public void onToolResult(String id, String result) {
                Log.d("MainPresenter", "onToolResult: id=" + id);
                if (streamingView != null) {
                    mainHandler.post(() -> streamingView.onStreamToolResult(id, result));
                }
            }

            @Override
            public void onMessageEnd(String finishReason) {
                Log.d("MainPresenter", "onMessageEnd: finishReason=" + finishReason);
                if (streamingView != null && messageListView != null) {
                    mainHandler.post(() -> {
                        streamingView.hideThinking();
                        messageListView.hideLoading();
                        streamingView.onStreamMessageEnd(finishReason);
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
        // 清理 Context 引用，避免内存泄漏
        if (mobileAgentApi instanceof NativeMobileAgentApiAdapter) {
            ((NativeMobileAgentApiAdapter) mobileAgentApi).clearContext();
        }
        // 清除单例引用
        instance = null;
    }
}
