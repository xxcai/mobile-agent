package com.hh.agent.lib.config;

/**
 * Nanobot 配置类
 */
public class NanobotConfig {

    private String baseUrl;
    private int connectTimeout;
    private int readTimeout;

    public NanobotConfig() {
        this.baseUrl = "http://localhost:18791";
        this.connectTimeout = 30;
        this.readTimeout = 60;
    }

    public NanobotConfig(String baseUrl) {
        this.baseUrl = baseUrl;
        this.connectTimeout = 30;
        this.readTimeout = 60;
    }

    public NanobotConfig(String baseUrl, int connectTimeout, int readTimeout) {
        this.baseUrl = baseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getApiUrl() {
        return baseUrl + "/api/chat";
    }

    public String getHealthUrl() {
        return baseUrl + "/health";
    }
}
