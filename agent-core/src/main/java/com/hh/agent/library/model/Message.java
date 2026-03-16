package com.hh.agent.library.model;

import com.google.gson.annotations.SerializedName;

/**
 * 消息实体类
 */
public class Message {
    @SerializedName("id") private String id;
    @SerializedName("role") private String role;        // "user", "assistant", "system", "thinking", "tool_use", "tool_result"
    @SerializedName("name") private String name;         // tool name for tool_use and tool_result
    @SerializedName("content") private String content;
    @SerializedName("timestamp") private long timestamp;

    public Message() {
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String id, String role, String content, long timestamp) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
