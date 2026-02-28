package com.hh.agent.lib.http;

import com.google.gson.Gson;
import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.config.NanobotConfig;
import com.hh.agent.lib.dto.ChatRequest;
import com.hh.agent.lib.dto.ChatResponse;
import com.hh.agent.lib.model.Message;
import com.hh.agent.lib.model.Session;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP 实现类 - 通过 HTTP 调用 nanobot 服务
 */
public class HttpNanobotApi implements NanobotApi {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final NanobotConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Map<String, Session> sessions;

    public HttpNanobotApi() {
        this(new NanobotConfig());
    }

    public HttpNanobotApi(NanobotConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.sessions = new ConcurrentHashMap<>();
    }

    @Override
    public Session createSession(String channel, String chatId) {
        String key = channel + ":" + chatId;
        Session session = new Session(key);
        sessions.put(key, session);
        return session;
    }

    @Override
    public Session getSession(String sessionKey) {
        return sessions.get(sessionKey);
    }

    @Override
    public Message sendMessage(String content, String sessionKey) {
        // 创建请求
        ChatRequest request = new ChatRequest(content, sessionKey);
        String json = gson.toJson(request);

        try {
            RequestBody body = RequestBody.create(json, JSON);
            Request httpRequest = new Request.Builder()
                    .url(config.getApiUrl())
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                String responseBody = response.body().string();
                ChatResponse chatResponse = gson.fromJson(responseBody, ChatResponse.class);

                if (!chatResponse.isSuccess()) {
                    throw new IOException(chatResponse.getError());
                }

                // 将响应转换为 Message
                Message message = new Message();
                message.setId(UUID.randomUUID().toString());
                message.setRole("assistant");
                message.setContent(chatResponse.getResponse());
                message.setTimestamp(System.currentTimeMillis());

                // 保存到会话历史
                Session session = sessions.get(sessionKey);
                if (session == null) {
                    session = createSession("http", sessionKey);
                }

                // 添加用户消息
                Message userMsg = new Message();
                userMsg.setId(UUID.randomUUID().toString());
                userMsg.setRole("user");
                userMsg.setContent(content);
                userMsg.setTimestamp(System.currentTimeMillis());
                session.getMessages().add(userMsg);

                // 添加助手回复
                session.getMessages().add(message);

                return message;
            }
        } catch (IOException e) {
            // 网络错误时返回错误消息
            Message errorMsg = new Message();
            errorMsg.setId(UUID.randomUUID().toString());
            errorMsg.setRole("assistant");
            errorMsg.setContent("Error: " + e.getMessage());
            errorMsg.setTimestamp(System.currentTimeMillis());
            return errorMsg;
        }
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        Session session = sessions.get(sessionKey);
        if (session == null) {
            return Collections.emptyList();
        }

        List<Message> messages = session.getMessages();
        if (messages.size() <= maxMessages) {
            return messages;
        }

        return messages.subList(messages.size() - maxMessages, messages.size());
    }

    /**
     * 检查服务是否可用
     */
    public boolean isAvailable() {
        try {
            Request request = new Request.Builder()
                    .url(config.getHealthUrl())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }
}
