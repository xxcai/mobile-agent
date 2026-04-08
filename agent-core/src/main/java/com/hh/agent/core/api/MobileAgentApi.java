package com.hh.agent.core.api;

import com.hh.agent.core.event.AgentEventListener;
import com.hh.agent.core.model.Message;

import java.util.List;

/**
 * MobileAgent API 接口
 */
public interface MobileAgentApi {

    /**
     * 获取历史消息
     *
     * @param sessionKey   会话密钥
     * @param maxMessages 最大消息数
     * @return 消息列表
     */
    List<Message> getHistory(String sessionKey, int maxMessages);

    /**
     * 发送消息（流式）
     *
     * @param content    消息内容
     * @param sessionKey 会话密钥
     * @param listener 流式事件监听器
     */
    void sendMessageStream(String content, String sessionKey, AgentEventListener listener);

    /**
     * Clear persisted chat history for a session.
     *
     * @param sessionKey 会话密钥
     */
    boolean clearHistory(String sessionKey);

    /**
     * Clear persisted chat history and long-term memory for a session.
     *
     * @param sessionKey 会话密钥
     */
    boolean clearHistoryAndLongTermMemory(String sessionKey);
}
