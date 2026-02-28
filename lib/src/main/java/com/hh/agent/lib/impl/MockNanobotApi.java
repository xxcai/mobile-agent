package com.hh.agent.lib.impl;

import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.model.Message;
import com.hh.agent.lib.model.Session;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock Nanobot API 实现
 * 提供模拟数据用于开发测试
 */
public class MockNanobotApi implements NanobotApi {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session createSession(String channel, String chatId) {
        String key = channel + ":" + chatId;
        Session session = new Session(key);
        sessions.put(key, session);

        // 添加欢迎消息
        Message welcomeMsg = new Message();
        welcomeMsg.setId(UUID.randomUUID().toString());
        welcomeMsg.setRole("assistant");
        welcomeMsg.setContent("你好！我是 Nanobot 助手，有什么可以帮你的吗？");
        welcomeMsg.setTimestamp(System.currentTimeMillis());
        session.getMessages().add(welcomeMsg);

        return session;
    }

    @Override
    public Session getSession(String sessionKey) {
        return sessions.get(sessionKey);
    }

    @Override
    public Message sendMessage(String content, String sessionKey) {
        Session session = sessions.get(sessionKey);
        if (session == null) {
            session = createSession("cli", sessionKey);
        }

        // 添加用户消息
        Message userMsg = new Message();
        userMsg.setId(UUID.randomUUID().toString());
        userMsg.setRole("user");
        userMsg.setContent(content);
        userMsg.setTimestamp(System.currentTimeMillis());
        session.getMessages().add(userMsg);

        // 生成回复
        String responseContent = generateResponse(content);
        Message botMsg = new Message();
        botMsg.setId(UUID.randomUUID().toString());
        botMsg.setRole("assistant");
        botMsg.setContent(responseContent);
        botMsg.setTimestamp(System.currentTimeMillis());
        session.getMessages().add(botMsg);

        return botMsg;
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        Session session = sessions.get(sessionKey);
        if (session == null) {
            return java.util.Collections.emptyList();
        }

        List<Message> messages = session.getMessages();
        if (messages.size() <= maxMessages) {
            return messages;
        }

        return messages.subList(messages.size() - maxMessages, messages.size());
    }

    /**
     * 生成 Mock 响应
     */
    private String generateResponse(String content) {
        String lowerContent = content.toLowerCase();

        if (lowerContent.contains("help") || lowerContent.contains("帮助")) {
            return "我可以帮助你完成各种任务，例如：\n- 查询信息\n- 执行操作\n- 回答问题\n\n请告诉我你需要什么帮助？";
        } else if (lowerContent.contains("hello") || lowerContent.contains("你好") || lowerContent.contains("hi")) {
            return "你好！有什么我可以帮你的吗？";
        } else if (lowerContent.contains("who") && lowerContent.contains("you")) {
            return "我是 Nanobot，一个 AI 助手。我可以帮助你完成各种任务。";
        } else if (lowerContent.contains("thanks") || lowerContent.contains("谢谢")) {
            return "不客气！很高兴能帮到你。还有其他需要吗？";
        } else {
            return "收到: " + content + "\n我正在理解你的意图...";
        }
    }
}
