package com.hh.agent.lib.api;

import com.hh.agent.lib.model.Message;
import com.hh.agent.lib.model.Session;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/**
 * NanobotApi 接口的单元测试
 */
public class NanobotApiTest {

    @Test
    public void testApiInterfaceExists() {
        // 验证 NanobotApi 接口存在且包含所需方法
        // 通过创建实现类来验证接口定义
        assertNotNull(NanobotApi.class);
    }

    @Test
    public void testCreateSession() {
        // 测试创建会话功能
        // 这里需要有一个实现类
        NanobotApi api = new TestNanobotApi();

        Session session = api.createSession("cli", "user123");

        assertNotNull(session);
        assertEquals("cli:user123", session.getKey());
    }

    @Test
    public void testGetSession() {
        NanobotApi api = new TestNanobotApi();

        api.createSession("cli", "user123");
        Session session = api.getSession("cli:user123");

        assertNotNull(session);
        assertEquals("cli:user123", session.getKey());
    }

    @Test
    public void testSendMessage() {
        NanobotApi api = new TestNanobotApi();

        api.createSession("cli", "user123");
        Message response = api.sendMessage("Hello", "cli:user123");

        assertNotNull(response);
        assertEquals("assistant", response.getRole());
    }

    @Test
    public void testGetHistory() {
        NanobotApi api = new TestNanobotApi();

        Session session = api.createSession("cli", "user123");
        // 先添加消息到会话
        session.getMessages().add(new Message("1", "user", "Hello", System.currentTimeMillis()));
        session.getMessages().add(new Message("2", "assistant", "Hi there", System.currentTimeMillis()));

        List<Message> history = api.getHistory("cli:user123", 10);

        assertTrue(history.size() >= 2);
    }

    /**
     * 测试用实现类
     */
    private static class TestNanobotApi implements NanobotApi {
        private Session session;

        @Override
        public Session createSession(String channel, String chatId) {
            this.session = new Session(channel + ":" + chatId);
            return this.session;
        }

        @Override
        public Session getSession(String sessionKey) {
            return session;
        }

        @Override
        public Message sendMessage(String content, String sessionKey) {
            Message response = new Message();
            response.setId("response-1");
            response.setRole("assistant");
            response.setContent("Echo: " + content);
            response.setTimestamp(System.currentTimeMillis());
            return response;
        }

        @Override
        public List<Message> getHistory(String sessionKey, int maxMessages) {
            return session != null ? session.getMessages() : java.util.Collections.emptyList();
        }
    }
}
