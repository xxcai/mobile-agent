package com.hh.agent.lib.model;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Message 模型的单元测试
 */
public class MessageTest {

    @Test
    public void testMessageCreation() {
        Message message = new Message();
        message.setId("msg-001");
        message.setRole("user");
        message.setContent("Hello");
        message.setTimestamp(1234567890L);

        assertEquals("msg-001", message.getId());
        assertEquals("user", message.getRole());
        assertEquals("Hello", message.getContent());
        assertEquals(1234567890L, message.getTimestamp());
    }

    @Test
    public void testMessageTypes() {
        Message message = new Message();
        message.setId("msg-002");
        message.setRole("assistant");
        message.setContent("Hi there");
        message.setTimestamp(System.currentTimeMillis());

        // 验证角色区分
        assertEquals("assistant", message.getRole());
        assertEquals("Hi there", message.getContent());
    }

    @Test
    public void testDefaultTimestamp() {
        Message message = new Message();
        message.setId("msg-003");
        message.setRole("user");
        message.setContent("Test");

        // 验证时间戳已被设置（不为0）
        assertTrue(message.getTimestamp() > 0);
    }
}
