package com.hh.agent.lib.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 聊天响应 DTO
 */
public class ChatResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("response")
    private String response;

    @SerializedName("session_key")
    private String sessionKey;

    @SerializedName("error")
    private String error;

    public ChatResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
