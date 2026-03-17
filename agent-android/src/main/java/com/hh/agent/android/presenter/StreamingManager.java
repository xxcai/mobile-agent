package com.hh.agent.android.presenter;

import com.hh.agent.library.AgentEventListener;
import com.hh.agent.library.NativeAgent;
import com.hh.agent.library.api.MobileAgentApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * StreamingManager - 流式状态管理和 API 封装
 *
 * 统一管理流式状态 (isStreaming) 和 API 调用，
 * 实现流式状态管理与 UI 层解耦。
 */
public class StreamingManager {

    /**
     * 流式事件回调接口
     */
    public interface StreamingCallback {
        void onTextDelta(String text);
        void onToolUse(String id, String name, String argumentsJson);
        void onToolResult(String id, String result);
        void onMessageEnd(String finishReason);
        void onError(String errorCode, String errorMessage);
    }

    private final MobileAgentApi api;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private StreamingCallback callback;

    /**
     * 构造函数
     *
     * @param api MobileAgentApi 实例，用于发送流式消息
     */
    public StreamingManager(MobileAgentApi api) {
        this.api = api;
    }

    /**
     * 设置流式事件回调
     *
     * @param callback StreamingCallback 实例
     */
    public void setCallback(StreamingCallback callback) {
        this.callback = callback;
    }

    /**
     * 获取当前流式状态
     *
     * @return true 表示正在流式响应中
     */
    public boolean isStreaming() {
        return isStreaming.get();
    }

    /**
     * 发送流式消息
     *
     * @param content 消息内容
     * @param sessionKey 会话 key
     */
    public void sendMessageStream(String content, String sessionKey) {
        // 设置流式状态
        isStreaming.set(true);

        // 创建 AgentEventListener 监听器
        AgentEventListener listener = new AgentEventListener() {
            @Override
            public void onTextDelta(String text) {
                if (callback != null) {
                    callback.onTextDelta(text);
                }
            }

            @Override
            public void onToolUse(String id, String name, String argumentsJson) {
                if (callback != null) {
                    callback.onToolUse(id, name, argumentsJson);
                }
            }

            @Override
            public void onToolResult(String id, String result) {
                if (callback != null) {
                    callback.onToolResult(id, result);
                }
            }

            @Override
            public void onMessageEnd(String finishReason) {
                // 清除流式状态
                isStreaming.set(false);
                if (callback != null) {
                    callback.onMessageEnd(finishReason);
                }
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                // 清除流式状态
                isStreaming.set(false);
                if (callback != null) {
                    callback.onError(errorCode, errorMessage);
                }
            }
        };

        // 使用 executor 执行网络请求，避免阻塞主线程
        executor.execute(() -> {
            api.sendMessageStream(content, sessionKey, listener);
        });
    }

    /**
     * 取消流式请求
     */
    public void cancel() {
        if (isStreaming.get()) {
            // 调用 NativeAgent 取消流式请求
            NativeAgent.cancelStream();
            // 清除流式状态
            isStreaming.set(false);
        }
    }
}
