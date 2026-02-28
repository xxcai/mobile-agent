package com.hh.agent.lib.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 聊天请求 DTO
 */
public class ChatRequest {

    @SerializedName("message")
    private String message;

    @SerializedName("session_key")
    private String sessionKey;

    public ChatRequest() {
    }

    public ChatRequest(String message, String sessionKey) {
        this.message = message;
        this.sessionKey = sessionKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }
}
