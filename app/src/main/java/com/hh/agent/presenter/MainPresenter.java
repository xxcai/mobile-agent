package com.hh.agent.presenter;

import android.os.Handler;
import android.os.Looper;
import com.hh.agent.contract.MainContract;
import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.config.NanobotConfig;
import com.hh.agent.lib.http.HttpNanobotApi;
import com.hh.agent.lib.impl.MockNanobotApi;
import com.hh.agent.lib.model.Message;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity 的 Presenter 实现
 */
public class MainPresenter implements MainContract.Presenter {

    /**
     * API 类型枚举
     */
    public enum ApiType {
        MOCK,   // Mock 实现
        HTTP    // HTTP 调用 nanobot
    }

    private MainContract.View view;
    private final NanobotApi nanobotApi;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final String sessionKey;

    /**
     * 默认构造函数，使用 Mock API
     */
    public MainPresenter() {
        this(ApiType.HTTP, "http:default");
    }

    /**
     * 指定 API 类型
     *
     * @param apiType   API 类型 (MOCK 或 HTTP)
     * @param sessionKey 会话 key
     */
    public MainPresenter(ApiType apiType, String sessionKey) {
        this.nanobotApi = createApi(apiType);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionKey = sessionKey;
    }

    /**
     * 指定自定义 NanobotApi
     *
     * @param nanobotApi 自定义 API 实现
     * @param sessionKey 会话 key
     */
    public MainPresenter(NanobotApi nanobotApi, String sessionKey) {
        this.nanobotApi = nanobotApi;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionKey = sessionKey;
    }

    /**
     * 创建 API 实例
     */
    private NanobotApi createApi(ApiType apiType) {
        switch (apiType) {
            case HTTP:
                return new HttpNanobotApi();
            case MOCK:
            default:
                return new MockNanobotApi();
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
                nanobotApi.getSession(sessionKey);

                List<Message> messages = nanobotApi.getHistory(sessionKey, 50);

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
                Message response = nanobotApi.sendMessage(content, sessionKey);

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
    }
}
