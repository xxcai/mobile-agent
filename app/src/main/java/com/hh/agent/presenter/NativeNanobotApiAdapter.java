package com.hh.agent.presenter;

import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.model.Message;
import com.hh.agent.lib.model.Session;
import com.hh.agent.library.api.NativeNanobotApi;

import java.util.ArrayList;
import java.util.List;

/**
 * NativeNanobotApi 适配器
 * 将 agent 模块的 Message/Session 模型转换为 lib 模块的模型
 */
public class NativeNanobotApiAdapter implements NanobotApi {

    private final NativeNanobotApi nativeApi;

    public NativeNanobotApiAdapter() {
        this.nativeApi = NativeNanobotApi.getInstance();
    }

    /**
     * 初始化 Native Agent
     */
    public void initialize(String configPath) {
        nativeApi.initialize(configPath);
    }

    @Override
    public Session createSession(String channel, String chatId) {
        // 不需要转换，返回 null 即可，getSession 会自动创建
        return null;
    }

    @Override
    public Session getSession(String sessionKey) {
        com.hh.agent.library.model.Session agentSession = nativeApi.getSession(sessionKey);
        if (agentSession == null) {
            // 自动创建会话
            agentSession = nativeApi.createSession("default", sessionKey);
        }
        // 将 AgentSession 转换为 lib Session
        return convertSession(agentSession, sessionKey);
    }

    @Override
    public Message sendMessage(String content, String sessionKey) {
        com.hh.agent.library.model.Message agentMessage = nativeApi.sendMessage(content, sessionKey);
        return convertMessage(agentMessage);
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        List<com.hh.agent.library.model.Message> agentMessages = nativeApi.getHistory(sessionKey, maxMessages);
        List<Message> messages = new ArrayList<>();
        for (com.hh.agent.library.model.Message agentMessage : agentMessages) {
            messages.add(convertMessage(agentMessage));
        }
        return messages;
    }

    /**
     * 将 agent 模块的 Message 转换为 lib 模块的 Message
     */
    private Message convertMessage(com.hh.agent.library.model.Message agentMessage) {
        if (agentMessage == null) {
            return null;
        }
        Message message = new Message();
        message.setId(agentMessage.getId());
        message.setRole(agentMessage.getRole());
        message.setContent(agentMessage.getContent());
        message.setTimestamp(agentMessage.getTimestamp());
        return message;
    }

    /**
     * 将 agent 模块的 Session 转换为 lib 模块的 Session
     */
    private Session convertSession(com.hh.agent.library.model.Session agentSession, String sessionKey) {
        if (agentSession == null) {
            return new Session(sessionKey);
        }
        Session session = new Session(sessionKey);
        // Session 模型可能没有消息列表，暂不转换
        return session;
    }
}
