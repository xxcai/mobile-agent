package com.hh.agent.lib.api;

import com.hh.agent.lib.model.Message;
import com.hh.agent.lib.model.Session;

import java.util.List;

/**
 * Nanobot API 接口
 */
public interface NanobotApi {

    /**
     * 创建新会话
     *
     * @param channel 渠道（如 cli, telegram, discord）
     * @param chatId 聊天 ID
     * @return 会话对象
     */
    Session createSession(String channel, String chatId);

    /**
     * 获取会话
     *
     * @param sessionKey 会话密钥 (channel:chatId)
     * @return 会话对象，如果不存在返回 null
     */
    Session getSession(String sessionKey);

    /**
     * 发送消息（同步）
     *
     * @param content    消息内容
     * @param sessionKey 会话密钥
     * @return 机器人回复消息
     */
    Message sendMessage(String content, String sessionKey);

    /**
     * 获取历史消息
     *
     * @param sessionKey   会话密钥
     * @param maxMessages 最大消息数
     * @return 消息列表
     */
    List<Message> getHistory(String sessionKey, int maxMessages);
}
