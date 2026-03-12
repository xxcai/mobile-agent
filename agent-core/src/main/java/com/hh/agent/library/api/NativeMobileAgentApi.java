package com.hh.agent.library.api;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import com.hh.agent.library.AndroidToolCallback;
import com.hh.agent.library.NativeAgent;
import com.hh.agent.library.model.Message;
import com.hh.agent.library.model.Session;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NativeMobileAgentApi 实现
 * 使用 NativeAgent JNI 调用本地 C++ Agent 引擎
 */
public class NativeMobileAgentApi implements MobileAgentApi {

    private static NativeMobileAgentApi instance;
    private static final String PREFS_NAME = "agent_sessions";
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private boolean initialized = false;
    private AndroidToolCallback toolCallback;
    private SharedPreferences prefs;
    private Gson gson;

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
        if (prefs == null && context != null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            gson = new Gson();
            System.out.println("[NativeMobileAgentApi] Context initialized for persistence");
        }
    }

    /**
     * 保存会话到本地存储
     *
     * @param session 要保存的会话
     */
    public synchronized void saveSession(Session session) {
        if (prefs == null || gson == null || session == null) {
            System.out.println("[NativeMobileAgentApi] Cannot save session: context not initialized");
            return;
        }
        String json = gson.toJson(session);
        prefs.edit().putString(session.getKey(), json).apply();
        System.out.println("[NativeMobileAgentApi] Session saved: " + session.getKey());
    }

    /**
     * 从本地存储加载会话
     *
     * @param sessionKey 会话键
     * @return 会话对象，不存在则返回 null
     */
    public synchronized Session loadSession(String sessionKey) {
        if (prefs == null || gson == null) {
            System.out.println("[NativeMobileAgentApi] Cannot load session: context not initialized");
            return null;
        }
        String json = prefs.getString(sessionKey, null);
        if (json == null) {
            return null;
        }
        Session session = gson.fromJson(json, Session.class);
        System.out.println("[NativeMobileAgentApi] Session loaded: " + sessionKey);
        return session;
    }

    /**
     * 从本地存储加载所有会话
     *
     * @return 会话数量
     */
    public synchronized int loadAllSessions() {
        if (prefs == null || gson == null) {
            System.out.println("[NativeMobileAgentApi] Cannot load sessions: context not initialized");
            return 0;
        }
        Map<String, ?> allSessions = prefs.getAll();
        int count = 0;
        for (String key : allSessions.keySet()) {
            String json = (String) allSessions.get(key);
            Session session = gson.fromJson(json, Session.class);
            if (session != null) {
                sessions.put(key, session);
                count++;
            }
        }
        System.out.println("[NativeMobileAgentApi] Loaded " + count + " sessions from storage");
        return count;
    }

    /**
     * 设置 Android Tool 回调实现
     * 由 app 模块调用，注册 AndroidToolManager 实例
     *
     * @param callback 实现 AndroidToolCallback 接口的实例
     */
    public synchronized void setToolCallback(AndroidToolCallback callback) {
        this.toolCallback = callback;
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
            // 自动创建会话
            session = createSession("default", sessionKey);
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

        // 持久化会话
        saveSession(session);

        return assistantMessage;
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        Session session = sessions.get(sessionKey);
        if (session == null) {
            return new ArrayList<>();
        }

        List<Message> messages = session.getMessages();
        if (messages.size() <= maxMessages) {
            return new ArrayList<>(messages);
        }

        return new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
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
