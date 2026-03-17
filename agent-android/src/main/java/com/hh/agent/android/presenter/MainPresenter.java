package com.hh.agent.android.presenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.presenter.NativeMobileAgentApiAdapter;
import com.hh.agent.library.AgentEventListener;
import com.hh.agent.library.api.MobileAgentApi;
import com.hh.agent.library.api.NativeMobileAgentApi;
import com.hh.agent.library.model.Message;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity 的 Presenter 实现
 * 使用 Native C++ Agent
 *
 * TODO: isThinking 状态目前保存在 Presenter 层
 * 后续应该迁移到底层 C++ 实现，实现真正的异步消息处理
 */
public class MainPresenter implements MainContract.Presenter {

    private static MainPresenter instance;

    private MainContract.MessageListView messageListView;
    private MainContract.StreamingView streamingView;
    private final MobileAgentApi mobileAgentApi;
    private final StreamingManager streamingManager;
    private final ExecutorService executor;
    private final ExecutorService loadMessagesExecutor;
    private final Handler mainHandler;
    private final String sessionKey;

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
        this.executor = Executors.newSingleThreadExecutor();
        this.loadMessagesExecutor = Executors.newSingleThreadExecutor();
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

        loadMessagesExecutor.execute(() -> {
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
                if (streamingView != null) {
                    mainHandler.post(() -> streamingView.onStreamTextDelta(text));
                }
            }

            @Override
            public void onToolUse(String id, String name, String argumentsJson) {
                if (streamingView != null) {
                    mainHandler.post(() -> streamingView.onStreamToolUse(id, name, argumentsJson));
                }
            }

            @Override
            public void onToolResult(String id, String result) {
                if (streamingView != null) {
                    mainHandler.post(() -> streamingView.onStreamToolResult(id, result));
                }
            }

            @Override
            public void onMessageEnd(String finishReason) {
                if (streamingView != null && messageListView != null) {
                    mainHandler.post(() -> {
                        // 只隐藏 loading，不删除 thinking 消息
                        // thinking 消息的删除在 onStreamMessageEnd 中处理
                        messageListView.hideLoading();
                        streamingView.onStreamMessageEnd(finishReason);
                    });
                }
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                if (streamingView != null && messageListView != null) {
                    mainHandler.post(() -> {
                        streamingView.hideThinking();
                        messageListView.hideLoading();
                        streamingView.onStreamError(errorCode, errorMessage);
                    });
                }
            }
        });

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
        // 重新 attach 时，检查并恢复状态
        restoreViewState();
    }

    /**
     * 恢复 View 状态
     * 当页面重新打开时，根据 isThinking 状态恢复 UI
     *
     * 注意：thinking 状态的 UI 恢复现在统一在 loadMessages() 完成后处理，
     * 这样可以避免 setMessages() 替换掉思考消息的问题
     */
    private void restoreViewState() {
        // thinking 状态的 UI 恢复现在移到了 loadMessages() 完成后
        // 详见 loadMessages() 中的 onMessagesLoaded 回调
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
        // 关闭线程池
        executor.shutdown();
        loadMessagesExecutor.shutdown();
        // 清理 Context 引用，避免内存泄漏
        if (mobileAgentApi instanceof NativeMobileAgentApiAdapter) {
            ((NativeMobileAgentApiAdapter) mobileAgentApi).clearContext();
        }
        // 清除单例引用
        instance = null;
    }
}
