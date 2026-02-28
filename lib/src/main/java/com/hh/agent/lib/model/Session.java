package com.hh.agent.lib.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话实体类
 */
public class Session {
    private String key;                    // channel:chat_id
    private List<Message> messages;
    private long createdAt;
    private long updatedAt;

    public Session() {
        this.messages = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Session(String key) {
        this.key = key;
        this.messages = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 获取对齐到用户轮次的历史消息
     * 避免孤立的 tool_result 消息
     *
     * @param maxMessages 最大消息数
     * @return 历史消息列表
     */
    public List<Map<String, Object>> getHistory(int maxMessages) {
        List<Message> sliced = messages.size() > maxMessages
            ? messages.subList(messages.size() - maxMessages, messages.size())
            : messages;

        // 找到第一个用户消息的索引，去除开头的非用户消息
        int startIndex = 0;
        for (int i = 0; i < sliced.size(); i++) {
            if ("user".equals(sliced.get(i).getRole())) {
                startIndex = i;
                break;
            }
        }

        List<Message> aligned = sliced.subList(startIndex, sliced.size());

        // 转换为 Map 格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : aligned) {
            Map<String, Object> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            result.add(map);
        }

        return result;
    }

    /**
     * 添加消息到会话
     */
    public void addMessage(Message message) {
        this.messages.add(message);
        this.updatedAt = System.currentTimeMillis();
    }
}
