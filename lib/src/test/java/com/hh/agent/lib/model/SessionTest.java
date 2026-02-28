package com.hh.agent.lib.model;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * Session 模型的单元测试
 */
public class SessionTest {

    @Test
    public void testSessionCreation() {
        Session session = new Session();
        session.setKey("cli:user123");

        assertEquals("cli:user123", session.getKey());
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
    }

    @Test
    public void testAddMessage() {
        Session session = new Session();
        session.setKey("cli:user123");

        Message userMsg = new Message("1", "user", "Hello", System.currentTimeMillis());
        Message botMsg = new Message("2", "assistant", "Hi there", System.currentTimeMillis());

        session.getMessages().add(userMsg);
        session.getMessages().add(botMsg);

        assertEquals(2, session.getMessages().size());
        assertEquals("Hello", session.getMessages().get(0).getContent());
        assertEquals("Hi there", session.getMessages().get(1).getContent());
    }

    @Test
    public void testGetHistory() {
        Session session = new Session();
        session.setKey("cli:user123");

        // 添加多条消息
        session.getMessages().add(new Message("1", "user", "Hello", 1000L));
        session.getMessages().add(new Message("2", "assistant", "Hi", 2000L));
        session.getMessages().add(new Message("3", "user", "How are you?", 3000L));
        session.getMessages().add(new Message("4", "assistant", "I'm good", 4000L));

        // 获取历史消息
        List<Map<String, Object>> history = session.getHistory(10);

        assertEquals(4, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals("Hello", history.get(0).get("content"));
    }

    @Test
    public void testGetHistoryWithLimit() {
        Session session = new Session();
        session.setKey("cli:user123");

        // 添加消息，确保最后3条都有user角色
        for (int i = 0; i < 10; i++) {
            Message msg = new Message();
            msg.setId(String.valueOf(i));
            // 最后3条都设置为user角色
            if (i >= 7) {
                msg.setRole("user");
            } else {
                msg.setRole("assistant");
            }
            msg.setContent("Message " + i);
            msg.setTimestamp(System.currentTimeMillis() + i);
            session.getMessages().add(msg);
        }

        // 只获取最近3条
        List<Map<String, Object>> history = session.getHistory(3);

        assertEquals(3, history.size());
        // 最后3条消息是 Message 7, 8, 9
        assertEquals("Message 7", history.get(0).get("content"));
    }
}
