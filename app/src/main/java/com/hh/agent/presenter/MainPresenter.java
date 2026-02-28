package com.hh.agent.presenter;

import android.os.Handler;
import android.os.Looper;
import com.hh.agent.contract.MainContract;
import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.impl.MockNanobotApi;
import com.hh.agent.lib.model.Message;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity 的 Presenter 实现
 */
public class MainPresenter implements MainContract.Presenter {

    private MainContract.View view;
    private final NanobotApi nanobotApi;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final String sessionKey;

    public MainPresenter() {
        this.nanobotApi = new MockNanobotApi();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionKey = "cli:default";
    }

    public MainPresenter(NanobotApi nanobotApi, String sessionKey) {
        this.nanobotApi = nanobotApi;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sessionKey = sessionKey;
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

        if (view != null) {
            mainHandler.post(() -> view.showLoading());
        }

        executor.execute(() -> {
            try {
                // 发送消息，获取回复
                Message response = nanobotApi.sendMessage(content, sessionKey);

                if (view != null) {
                    mainHandler.post(() -> {
                        view.hideLoading();
                        view.onMessageReceived(response);
                    });
                }
            } catch (Exception e) {
                if (view != null) {
                    mainHandler.post(() -> {
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
