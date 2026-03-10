package com.hh.agent.android.presenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.presenter.NativeMobileAgentApiAdapter;
import com.hh.agent.library.api.MobileAgentApi;
import com.hh.agent.library.model.Message;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity 的 Presenter 实现
 * 使用 Native C++ Agent
 */
public class MainPresenter implements MainContract.Presenter {

    private MainContract.View view;
    private final MobileAgentApi mobileAgentApi;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final String sessionKey;

    /**
     * 构造函数，使用 Native Agent
     *
     * @param context   Android Context
     * @param sessionKey 会话 key
     */
    public MainPresenter(Context context, String sessionKey) {
        this.mobileAgentApi = createApi(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionKey = sessionKey;
    }

    /**
     * 简化构造函数
     */
    public MainPresenter(String sessionKey) {
        this(null, sessionKey);
    }

    /**
     * 创建 API 实例 - 只使用 Native Agent
     * Note: AndroidToolManager 现在由 app 层 LauncherActivity 初始化和注册 Tool
     */
    private MobileAgentApi createApi(Context context) {
        try {
            NativeMobileAgentApiAdapter adapter = new NativeMobileAgentApiAdapter();
            if (context != null) {
                adapter.setContext(context);
            }
            adapter.initialize("");

            return adapter;
        } catch (Exception e) {
            // 如果初始化失败，抛出异常
            throw new RuntimeException("Failed to initialize Native API: " + e.getMessage(), e);
        }
    }

    @Override
    public void loadMessages() {
        if (view != null) {
            mainHandler.post(() -> view.showLoading());
        }

        executor.execute(() -> {
            try {
                // 确保会话存在
                mobileAgentApi.getSession(sessionKey);

                List<Message> messages = mobileAgentApi.getHistory(sessionKey, 50);

                if (view != null) {
                    mainHandler.post(() -> {
                        view.hideLoading();
                        view.onMessagesLoaded(messages);
                    });
                }
            } catch (Exception e) {
                if (view != null) {
                    mainHandler.post(() -> {
                        view.hideLoading();
                        view.onError("加载消息失败: " + e.getMessage());
                    });
                }
            }
        });
    }

    @Override
    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) {
            if (view != null) {
                mainHandler.post(() -> view.onError("消息不能为空"));
            }
            return;
        }

        // 创建用户消息
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setTimestamp(System.currentTimeMillis());

        // 先通知 View 显示用户消息
        if (view != null) {
            mainHandler.post(() -> view.onUserMessageSent(userMessage));
        }

        // 显示思考中提示
        if (view != null) {
            mainHandler.post(() -> view.showThinking());
        }

        if (view != null) {
            mainHandler.post(() -> view.showLoading());
        }

        executor.execute(() -> {
            try {
                // 发送消息，获取回复
                Message response = mobileAgentApi.sendMessage(content, sessionKey);

                if (view != null) {
                    mainHandler.post(() -> {
                        view.hideThinking();
                        view.hideLoading();
                        view.onMessageReceived(response);
                    });
                }
            } catch (Exception e) {
                if (view != null) {
                    mainHandler.post(() -> {
                        view.hideThinking();
                        view.hideLoading();
                        view.onError("发送消息失败: " + e.getMessage());
                    });
                }
            }
        });
    }

    @Override
    public void attachView(MainContract.View view) {
        this.view = view;
    }

    @Override
    public void detachView() {
        this.view = null;
    }

    /**
     * 销毁Presenter，释放资源
     */
    public void destroy() {
        executor.shutdown();
        // 清理 Context 引用，避免内存泄漏
        if (mobileAgentApi instanceof NativeMobileAgentApiAdapter) {
            ((NativeMobileAgentApiAdapter) mobileAgentApi).clearContext();
        }
    }
}
