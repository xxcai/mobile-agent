package com.hh.agent.core.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话实体类
 */
public class Session {
    @SerializedName("key") private String key;                    // channel:chat_id
    @SerializedName("messages") private List<Message> messages;
    @SerializedName("createdAt") private long createdAt;
    @SerializedName("updatedAt") private long updatedAt;

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
     * 添加消息到会话
     */
    public void addMessage(Message message) {
        this.messages.add(message);
        this.updatedAt = System.currentTimeMillis();
    }
}
