package com.hh.agent.android.contract;

import com.hh.agent.library.model.Message;
import java.util.List;

/**
 * MainActivity 的 MVP 契约接口
 */
public interface MainContract {

    /**
     * 基础 View 接口 - 公共方法
     */
    interface BaseView {
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
    }

    /**
     * 消息列表 View 接口
     */
    interface MessageListView extends BaseView {
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
    }

    /**
     * 流式响应 View 接口
     */
    interface StreamingView extends BaseView {
        /**
         * 流式消息更新回调（传递完整的 Message 对象）
         */
        void onStreamMessageUpdate(Message message);

        /**
         * 流式消息结束回调
         * @param message 最终的 Message 对象
         * @param finishReason 结束原因：stop/error/cancel/tool_calls
         */
        void onStreamMessageEnd(Message message, String finishReason);

        /**
         * 流式错误回调
         */
        void onStreamError(String errorCode, String errorMessage);

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
     * View 接口 - UI 更新（保留向后兼容）
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
        void attachView(MessageListView messageListView, StreamingView streamingView);

        /**
         * 解绑 View
         */
        void detachView();

        /**
         * 取消流式请求
         */
        void cancelStream();
    }
}
