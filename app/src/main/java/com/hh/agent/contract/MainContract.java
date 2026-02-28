package com.hh.agent.contract;

import com.hh.agent.lib.model.Message;
import java.util.List;

/**
 * MainActivity 的 MVP 契约接口
 */
public interface MainContract {

    /**
     * View 接口 - UI 更新
     */
    interface View {
        /**
         * 加载历史消息
         */
        void onMessagesLoaded(List<Message> messages);

        /**
         * 收到新消息
         */
        void onMessageReceived(Message message);

        /**
         * 用户消息已发送
         */
        void onUserMessageSent(Message message);

        /**
         * 显示错误
         */
        void onError(String error);

        /**
         * 显示加载状态
         */
        void showLoading();

        /**
         * 隐藏加载状态
         */
        void hideLoading();

        /**
         * 显示思考中提示
         */
        void showThinking();

        /**
         * 隐藏思考中提示
         */
        void hideThinking();
    }

    /**
     * Presenter 接口 - 业务逻辑
     */
    interface Presenter {
        /**
         * 加载历史消息
         */
        void loadMessages();

        /**
         * 发送消息
         */
        void sendMessage(String content);

        /**
         * 绑定 View
         */
        void attachView(View view);

        /**
         * 解绑 View
         */
        void detachView();
    }
}
