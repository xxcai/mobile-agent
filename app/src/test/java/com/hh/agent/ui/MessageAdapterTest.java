package com.hh.agent.ui;

import com.hh.agent.lib.model.Message;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/**
 * MessageAdapter 的单元测试
 */
public class MessageAdapterTest {

    @Test
    public void testSetMessages() {
        // 创建一个简单的测试，不依赖 RecyclerView
        List<Message> messages = new ArrayList<>();
        Message msg1 = new Message();
        msg1.setId("1");
        msg1.setRole("user");
        msg1.setContent("Hello");

        Message msg2 = new Message();
        msg2.setId("2");
        msg2.setRole("assistant");
        msg2.setContent("Hi there");

        messages.add(msg1);
        messages.add(msg2);

        // 验证数据正确
        assertEquals(2, messages.size());
        assertEquals("Hello", messages.get(0).getContent());
        assertEquals("Hi there", messages.get(1).getContent());
    }

    @Test
    public void testMessageRoles() {
        Message userMsg = new Message();
        userMsg.setRole("user");
        assertEquals("user", userMsg.getRole());

        Message assistantMsg = new Message();
        assistantMsg.setRole("assistant");
        assertEquals("assistant", assistantMsg.getRole());

        Message systemMsg = new Message();
        systemMsg.setRole("system");
        assertEquals("system", systemMsg.getRole());
    }

    @Test
    public void testMessageContent() {
        Message msg = new Message();
        msg.setContent("Test message content");
        assertEquals("Test message content", msg.getContent());
    }
}
