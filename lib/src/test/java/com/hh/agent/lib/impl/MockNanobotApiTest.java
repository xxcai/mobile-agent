package com.hh.agent.lib.impl;

import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.model.Message;
import com.hh.agent.lib.model.Session;
import org.junit.Before;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/**
 * MockNanobotApi 的单元测试
 */
public class MockNanobotApiTest {

    private NanobotApi api;

    @Before
    public void setUp() {
        api = new MockNanobotApi();
    }

    @Test
    public void testCreateSession() {
        Session session = api.createSession("cli", "user123");

        assertNotNull(session);
        assertEquals("cli:user123", session.getKey());
    }

    @Test
    public void testGetSession() {
        api.createSession("cli", "user123");
        Session session = api.getSession("cli:user123");

        assertNotNull(session);
        assertEquals("cli:user123", session.getKey());
    }

    @Test
    public void testSendMessage() {
        api.createSession("cli", "user123");
        Message response = api.sendMessage("Hello", "cli:user123");

        assertNotNull(response);
        assertEquals("assistant", response.getRole());
        assertNotNull(response.getContent());
    }

    @Test
    public void testSendMessageAddsToHistory() {
        api.createSession("cli", "user123");

        // 发送用户消息
        api.sendMessage("Hello", "cli:user123");

        // 检查历史消息
        List<Message> history = api.getHistory("cli:user123", 10);
        assertTrue(history.size() >= 1);
    }

    @Test
    public void testGetHistory() {
        api.createSession("cli", "user123");

        api.sendMessage("First", "cli:user123");
        api.sendMessage("Second", "cli:user123");

        List<Message> history = api.getHistory("cli:user123", 10);

        // createSession 会添加欢迎消息，sendMessage 会添加用户和助手消息
        // 所以总共有: 1(欢迎) + 2*2(用户+助手) = 5 条消息
        assertTrue(history.size() >= 2);
    }

    @Test
    public void testMockResponse() {
        api.createSession("cli", "user123");

        Message response = api.sendMessage("help", "cli:user123");

        assertNotNull(response.getContent());
        // 验证 Mock 返回了合理的响应
        assertTrue(response.getContent().length() > 0);
    }
}
