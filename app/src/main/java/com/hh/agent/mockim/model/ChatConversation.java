package com.hh.agent.mockim.model;

import java.util.List;

public class ChatConversation {

    private final String id;
    private final String title;
    private final String unreadCount;
    private final String time;
    private final String lastMessage;
    private final boolean pinned;
    private final List<ChatMessage> messages;

    public ChatConversation(String id,
                            String title,
                            String unreadCount,
                            String time,
                            String lastMessage,
                            boolean pinned,
                            List<ChatMessage> messages) {
        this.id = id;
        this.title = title;
        this.unreadCount = unreadCount;
        this.time = time;
        this.lastMessage = lastMessage;
        this.pinned = pinned;
        this.messages = messages;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUnreadCount() {
        return unreadCount;
    }

    public String getTime() {
        return time;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public boolean isPinned() {
        return pinned;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }
}
