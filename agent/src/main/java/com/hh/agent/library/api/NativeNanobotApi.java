package com.hh.agent.library.api;

import com.hh.agent.library.NativeAgent;
import com.hh.agent.library.model.Message;
import com.hh.agent.library.model.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NativeNanobotApi 实现
 * 使用 NativeAgent JNI 调用本地 C++ Agent 引擎
 */
public class NativeNanobotApi implements NanobotApi {

    private static NativeNanobotApi instance;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private boolean initialized = false;

    private NativeNanobotApi() {
    }

    /**
     * 获取单例实例
     */
    public static synchronized NativeNanobotApi getInstance() {
        if (instance == null) {
            instance = new NativeNanobotApi();
        }
        return instance;
    }

    /**
     * 初始化 Native Agent
     *
     * @param configPath 配置文件路径
     */
    public synchronized void initialize(String configPath) {
        if (!initialized) {
            try {
                NativeAgent.nativeInitialize(configPath);
                initialized = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize native agent: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public Session createSession(String channel, String chatId) {
        String sessionKey = channel + ":" + chatId;
        Session session = new Session(sessionKey);
        sessions.put(sessionKey, session);
        return session;
    }

    @Override
    public Session getSession(String sessionKey) {
        return sessions.get(sessionKey);
    }

    @Override
    public Message sendMessage(String content, String sessionKey) {
        // 确保会话存在
        Session session = sessions.get(sessionKey);
        if (session == null) {
            // 自动创建会话
            session = createSession("default", sessionKey);
        }

        // 添加用户消息
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(content);
        session.addMessage(userMessage);

        // 调用 Native Agent
        String response;
        try {
            response = NativeAgent.nativeSendMessage(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to native agent: " + e.getMessage(), e);
        }

        // 创建助手回复消息
        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(response);
        session.addMessage(assistantMessage);

        return assistantMessage;
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        Session session = sessions.get(sessionKey);
        if (session == null) {
            return new ArrayList<>();
        }

        List<Message> messages = session.getMessages();
        if (messages.size() <= maxMessages) {
            return new ArrayList<>(messages);
        }

        return new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
    }

    /**
     * 关闭并清理资源
     */
    public synchronized void shutdown() {
        if (initialized) {
            try {
                NativeAgent.nativeShutdown();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
            sessions.clear();
            initialized = false;
        }
    }
}
