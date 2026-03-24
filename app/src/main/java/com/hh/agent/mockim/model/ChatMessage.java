package com.hh.agent.mockim.model;

public class ChatMessage {

    private final boolean fromMe;
    private final String content;

    public ChatMessage(boolean fromMe, String content) {
        this.fromMe = fromMe;
        this.content = content;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public String getContent() {
        return content;
    }
}
