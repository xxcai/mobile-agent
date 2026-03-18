package com.hh.agent.core.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息实体类
 */
public class Message {
    @SerializedName("id") private String id;
    @SerializedName("role") private String role;        // "user", "assistant", "system", "thinking", "tool_use", "tool_result", "response"
    @SerializedName("name") private String name;         // tool name for tool_use and tool_result, or tool name for response card
    @SerializedName("content") private String content;   // main content or tool status for response card
    @SerializedName("think_content") private String thinkContent; // think block content for response card
    @SerializedName("timestamp") private long timestamp;
    @SerializedName("tool_calls") private List<ToolCall> toolCalls; // 工具调用列表

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

    public String getThinkContent() {
        return thinkContent;
    }

    public void setThinkContent(String thinkContent) {
        this.thinkContent = thinkContent;
    }

    public List<ToolCall> getToolCalls() {
        if (toolCalls == null) {
            toolCalls = new ArrayList<>();
        }
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public void addToolCall(ToolCall toolCall) {
        getToolCalls().add(toolCall);
    }

    public ToolCall getToolCall(String id) {
        if (toolCalls == null) return null;
        for (ToolCall tc : toolCalls) {
            if (tc.getId().equals(id)) {
                return tc;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", role='" + role + '\'' +
                ", name='" + name + '\'' +
                ", content='" + content + '\'' +
                ", thinkContent='" + thinkContent + '\'' +
                ", timestamp=" + timestamp +
                ", toolCalls=" + toolCalls +
                '}';
    }
}
