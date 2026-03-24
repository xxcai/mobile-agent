package com.hh.agent.core.api.impl;

import android.content.Context;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.hh.agent.core.NativeAgent;
import com.hh.agent.core.api.MobileAgentApi;
import com.hh.agent.core.event.AgentEventListener;
import com.hh.agent.core.log.AgentLogger;
import com.hh.agent.core.log.AgentLogs;
import com.hh.agent.core.model.Message;
import com.hh.agent.core.tool.AndroidToolCallback;
import java.lang.reflect.Type;
import java.util.List;

/**
 * NativeMobileAgentApi 实现
 * 使用 NativeAgent JNI 调用本地 C++ Agent 引擎
 *
 * 注意：会话持久化为 Mock 实现，后续 C++ 模块开发时实现
 */
public class NativeMobileAgentApi implements MobileAgentApi {
    private static final String SCOPE = "NativeMobileAgentApi";
    private static final String THINK_START = "<think>";
    private static final String THINK_END = "</think>";

    private static NativeMobileAgentApi instance;
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
        debug("initialize_context_skipped", "reason=session_persistence_not_implemented");
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
     * 设置当前生效 logger，并同步到 native 层。
     * 供 agent-android 初始化链路透传当前 logger 使用。
     */
    public synchronized void setLogger(AgentLogger logger) {
        NativeAgent.setLogger(logger);
        debug("logger_synced", "logger_null=" + (logger == null));
    }

    /**
     * 显式设置 native logger level。
     * level 由 Java 层决定并下传，不再依赖 config.json 中的 logging.level。
     */
    public synchronized void setNativeLogLevel(String level) {
        NativeAgent.setNativeLogLevel(level);
        debug("native_log_level_synced", "level=" + nullToEmpty(level));
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
                info("tools_schema_set", "schema_length=" + toolsJson.length());
            } catch (Exception e) {
                error("tools_schema_set_failed", "message=" + e.getMessage(), e);
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
                info("initialize_start", "config_length=" + lengthOf(configPath));
                int result = NativeAgent.nativeInitialize(configPath);
                if (result != 0) {
                    throw new RuntimeException("Native agent initialization failed with code: " + result);
                }
                initialized = true;

                // Pass toolsJson to native layer
                if (toolsJson != null && !toolsJson.isEmpty()) {
                    try {
                        NativeAgent.nativeSetToolsSchema(toolsJson);
                        info("tools_schema_set", "source=initialize schema_length=" + toolsJson.length());
                    } catch (Exception e) {
                        warn("tools_schema_set_failed", "source=initialize message=" + e.getMessage());
                    }
                } else {
                    debug("tools_schema_register_skipped", "reason=empty_tools_json");
                }
                info("initialize_complete", "initialized=true");
            } catch (Exception e) {
                error("initialize_failed", "message=" + e.getMessage(), e);
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
    public Message sendMessage(String content, String sessionKey) {
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

        return assistantMessage;
    }

    @Override
    public void sendMessageStream(String content, String sessionKey, AgentEventListener listener) {
        NativeAgent.sendMessageStream(toSessionId(sessionKey), content, listener);
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        String sessionId = toSessionId(sessionKey);

        info("history_query_start", "session_key=" + nullToEmpty(sessionKey) + " limit=" + maxMessages);

        // Call C++ to get messages from SQLite
        String jsonResult = NativeAgent.nativeGetHistory(sessionId, maxMessages);

        debug("history_query_payload", "payload_length=" + lengthOf(jsonResult));

        if (jsonResult == null || jsonResult.isEmpty()) {
            info("history_query_empty", "session_key=" + nullToEmpty(sessionKey));
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

                sanitizePersistedAssistantContent(msg);

                messages.add(msg);
            }

            info("history_query_complete", "session_key=" + nullToEmpty(sessionKey)
                    + " message_count=" + messages.size());
            return messages;
        } catch (Exception e) {
            error("history_query_parse_failed", "message=" + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static void debug(String event, String detail) {
        AgentLogs.d(buildMessage(event, detail));
    }

    private static void info(String event, String detail) {
        AgentLogs.i(buildMessage(event, detail));
    }

    private static void warn(String event, String detail) {
        AgentLogs.w(buildMessage(event, detail));
    }

    private static void error(String event, String detail, Throwable throwable) {
        AgentLogs.e(buildMessage(event, detail), throwable);
    }

    private static String buildMessage(String event, String detail) {
        StringBuilder builder = new StringBuilder();
        builder.append("[")
                .append(SCOPE)
                .append("][")
                .append(event)
                .append("]");
        if (detail != null && !detail.isEmpty()) {
            builder.append(" ").append(detail);
        }
        return builder.toString();
    }

    private static int lengthOf(String value) {
        return value != null ? value.length() : 0;
    }

    private static String toSessionId(String sessionKey) {
        if (sessionKey == null || sessionKey.isEmpty()) {
            return "default";
        }
        if (sessionKey.startsWith("native:")) {
            return sessionKey.substring(7);
        }
        return sessionKey;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
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

    private void sanitizePersistedAssistantContent(Message message) {
        if (message == null || !"assistant".equals(message.getRole())) {
            return;
        }

        String content = message.getContent();
        if (content == null || !content.contains(THINK_START)) {
            return;
        }

        StringBuilder thinkBuilder = new StringBuilder();
        StringBuilder contentBuilder = new StringBuilder();
        int cursor = 0;

        while (cursor < content.length()) {
            int thinkStart = content.indexOf(THINK_START, cursor);
            if (thinkStart < 0) {
                appendIfNotEmpty(contentBuilder, content.substring(cursor));
                break;
            }

            appendIfNotEmpty(contentBuilder, content.substring(cursor, thinkStart));

            int thinkContentStart = thinkStart + THINK_START.length();
            int thinkEnd = content.indexOf(THINK_END, thinkContentStart);
            if (thinkEnd < 0) {
                appendIfNotEmpty(contentBuilder, content.substring(thinkContentStart));
                break;
            }

            appendThinkIfNotEmpty(thinkBuilder, content.substring(thinkContentStart, thinkEnd));
            cursor = thinkEnd + THINK_END.length();
        }

        message.setThinkContent(toNullableString(thinkBuilder));
        message.setContent(toNullableString(contentBuilder));
    }

    private static void appendIfNotEmpty(StringBuilder builder, String text) {
        if (text != null && !text.isEmpty()) {
            builder.append(text);
        }
    }

    private static void appendThinkIfNotEmpty(StringBuilder builder, String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(trimmed);
    }

    private static String toNullableString(StringBuilder builder) {
        return builder.length() == 0 ? null : builder.toString();
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
            initialized = false;
        }
    }
}
