package com.hh.agent.core.api;

import android.content.Context;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.hh.agent.core.AgentEventListener;
import com.hh.agent.core.AndroidToolCallback;
import com.hh.agent.core.NativeAgent;
import com.hh.agent.core.model.Message;
import com.hh.agent.core.model.Session;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NativeMobileAgentApi 实现
 * 使用 NativeAgent JNI 调用本地 C++ Agent 引擎
 *
 * 注意：会话持久化为 Mock 实现，后续 C++ 模块开发时实现
 */
public class NativeMobileAgentApi implements MobileAgentApi {

    private static NativeMobileAgentApi instance;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private boolean initialized = false;
    private static final Gson gson = new Gson();

    private NativeMobileAgentApi() {
    }

    /**
     * 获取单例实例
     */
    public static synchronized NativeMobileAgentApi getInstance() {
        if (instance == null) {
            instance = new NativeMobileAgentApi();
        }
        return instance;
    }

    /**
     * 初始化上下文相关的组件
     * 必须在使用持久化功能前调用
     *
     * @param context Android Context (通常为 Application Context)
     */
    public synchronized void initializeContext(Context context) {
        // TODO: 后续 C++ 持久化需要 Context
        System.out.println("[NativeMobileAgentApi] initializeContext: Mock - session persistence not implemented");
    }

    /**
     * 保存会话（空实现）
     */
    public synchronized void saveSession(Session session) {
        // Not implemented - session persistence handled by C++ layer
    }

    /**
     * 设置 Android Tool 回调实现
     * 由 app 模块调用，注册 AndroidToolManager 实例
     *
     * @param callback 实现 AndroidToolCallback 接口的实例
     */
    public synchronized void setToolCallback(AndroidToolCallback callback) {
        // Forward to NativeAgent to register with C++ layer
        NativeAgent.registerAndroidToolCallback(callback);
    }

    /**
     * 设置动态生成的 tools.json
     * 由 AndroidToolManager 在初始化时调用，传递动态生成的工具描述
     *
     * @param toolsJson JSON string for tools definition
     */
    public synchronized void setToolsJson(String toolsJson) {
        if (toolsJson != null && !toolsJson.isEmpty()) {
            try {
                NativeAgent.nativeSetToolsSchema(toolsJson);
                System.out.println("[NativeMobileAgentApi] Successfully set tools.json to native layer");
            } catch (Exception e) {
                System.err.println("[NativeMobileAgentApi] Failed to set tools schema: " + e.getMessage());
            }
        }
    }

    /**
     * 初始化 Native Agent
     *
     * @param toolsJson JSON string for tools definition (required for tool definitions)
     * @param configPath 配置文件路径
     */
    public synchronized void initialize(String toolsJson, String configPath) {
        if (!initialized) {
            try {
                int result = NativeAgent.nativeInitialize(configPath);
                if (result != 0) {
                    throw new RuntimeException("Native agent initialization failed with code: " + result);
                }
                initialized = true;

                // Pass toolsJson to native layer
                if (toolsJson != null && !toolsJson.isEmpty()) {
                    try {
                        NativeAgent.nativeSetToolsSchema(toolsJson);
                        System.out.println("[NativeMobileAgentApi] Successfully passed tools.json to native layer");
                    } catch (Exception e) {
                        // Log but don't fail initialization
                        System.out.println("[NativeMobileAgentApi] Failed to set tools schema: " + e.getMessage());
                    }
                } else {
                    System.out.println("[NativeMobileAgentApi] toolsJson is empty, skipping native registration");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize native agent: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public Session createSession(String channel, String chatId) {
        String sessionKey = channel + ":" + chatId;
        Session session = new Session(sessionKey);
        sessions.put(sessionKey, session);
        return session;
    }

    @Override
    public Session getSession(String sessionKey) {
        return sessions.get(sessionKey);
    }

    @Override
    public Message sendMessage(String content, String sessionKey) {
        // 确保会话存在
        Session session = sessions.get(sessionKey);
        if (session == null) {
            // 自动创建会话，直接使用传入的 sessionKey
            session = new Session(sessionKey);
            sessions.put(sessionKey, session);
        }

        // 添加用户消息
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(content);
        session.addMessage(userMessage);

        // 调用 Native Agent
        String response;
        try {
            response = NativeAgent.nativeSendMessage(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to native agent: " + e.getMessage(), e);
        }

        // 创建助手回复消息
        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(response);
        session.addMessage(assistantMessage);

        // TODO: C++ 持久化
        saveSession(session);

        return assistantMessage;
    }

    @Override
    public void sendMessageStream(String content, String sessionKey, AgentEventListener listener) {
        // 确保会话存在
        Session session = sessions.get(sessionKey);
        if (session == null) {
            session = new Session(sessionKey);
            sessions.put(sessionKey, session);
        }

        // 添加用户消息
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(content);
        session.addMessage(userMessage);

        // 调用 Native Agent 流式接口
        NativeAgent.sendMessageStream(content, listener);
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        // Convert sessionKey to C++ session_id format (e.g., "native:default" -> "default")
        String sessionId = sessionKey;
        if (sessionKey != null && sessionKey.startsWith("native:")) {
            sessionId = sessionKey.substring(7); // Remove "native:" prefix
        }

        System.out.println("[NativeMobileAgentApi] getHistory: sessionKey=" + sessionKey + ", sessionId=" + sessionId + ", limit=" + maxMessages);

        // Call C++ to get messages from SQLite
        String jsonResult = NativeAgent.nativeGetHistory(sessionId, maxMessages);

        System.out.println("[NativeMobileAgentApi] getHistory: jsonResult=" + jsonResult);

        if (jsonResult == null || jsonResult.isEmpty()) {
            System.out.println("[NativeMobileAgentApi] getHistory: returning empty list");
            return new ArrayList<>();
        }

        try {
            Type listType = new TypeToken<List<JsonObject>>(){}.getType();
            List<JsonObject> jsonMessages = gson.fromJson(jsonResult, listType);

            List<Message> messages = new ArrayList<>();
            for (JsonObject jsonMsg : jsonMessages) {
                Message msg = new Message();
                msg.setRole(jsonMsg.get("role").getAsString());
                msg.setContent(jsonMsg.get("content").getAsString());

                // Parse ISO 8601 timestamp to milliseconds
                String timestampStr = jsonMsg.get("timestamp").getAsString();
                msg.setTimestamp(parseTimestamp(timestampStr));

                messages.add(msg);
            }

            return messages;
        } catch (Exception e) {
            System.err.println("[NativeMobileAgentApi] getHistory: Failed to parse messages: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse ISO 8601 timestamp string to milliseconds
     */
    private long parseTimestamp(String timestampStr) {
        try {
            // Format: "2026-03-16T10:30:00.123Z"
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            java.time.Instant instant = java.time.ZonedDateTime.parse(timestampStr,
                java.time.format.DateTimeFormatter.ISO_DATE_TIME).toInstant();
            return instant.toEpochMilli();
        } catch (Exception e) {
            // Fallback: try simpler parsing or use current time
            try {
                // Try without milliseconds
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
                java.time.Instant instant = java.time.ZonedDateTime.parse(timestampStr,
                    java.time.format.DateTimeFormatter.ISO_DATE_TIME).toInstant();
                return instant.toEpochMilli();
            } catch (Exception e2) {
                return System.currentTimeMillis();
            }
        }
    }

    /**
     * 关闭并清理资源
     */
    public synchronized void shutdown() {
        if (initialized) {
            try {
                NativeAgent.nativeShutdown();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
            sessions.clear();
            initialized = false;
        }
    }
}
