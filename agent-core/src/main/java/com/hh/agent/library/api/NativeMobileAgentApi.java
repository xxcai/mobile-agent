package com.hh.agent.library.api;

import android.content.Context;
import java.util.ArrayList;
import com.hh.agent.library.AgentEventListener;
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
 *
 * 注意：会话持久化为 Mock 实现，后续 C++ 模块开发时实现
 */
public class NativeMobileAgentApi implements MobileAgentApi {

    private static NativeMobileAgentApi instance;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private boolean initialized = false;
    private AndroidToolCallback toolCallback;

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
     * 保存会话到 C++ 层 - Mock 空实现
     *
     * TODO: 后续 C++ 模块开发时，实现 nativeSaveSession() JNI 接口
     * 当前只打印日志，不实际保存
     *
     * @param session 要保存的会话
     */
    public synchronized void saveSession(Session session) {
        // TODO: 实现 C++ 持久化
        System.out.println("[NativeMobileAgentApi] saveSession: Mock - session NOT persisted, sessionKey=" + (session != null ? session.getKey() : "null"));
    }

    /**
     * 从 C++ 层加载会话 - Mock 空实现
     *
     * TODO: 后续 C++ 模块开发时，实现 nativeLoadSession() JNI 接口
     * 当前返回 null
     *
     * @param sessionKey 会话键
     * @return 总是返回 null
     */
    public synchronized Session loadSession(String sessionKey) {
        // TODO: 实现 C++ 持久化
        // Mock 返回一个包含历史消息的 Session
        Session session = new Session(sessionKey);
        Message msg1 = new Message();
        msg1.setRole("user");
        msg1.setContent("Hello");
        msg1.setTimestamp(System.currentTimeMillis() - 10000);
        session.addMessage(msg1);

        Message msg2 = new Message();
        msg2.setRole("assistant");
        msg2.setContent("Hi! I'm your AI assistant.");
        msg2.setTimestamp(System.currentTimeMillis() - 5000);
        session.addMessage(msg2);

        System.out.println("[NativeMobileAgentApi] loadSession: Mock returning session with " + session.getMessages().size() + " messages");
        return session;
    }

    /**
     * 从 C++ 层加载所有会话 - Mock 空实现
     *
     * TODO: 后续 C++ 模块开发时，实现 nativeLoadAllSessions() JNI 接口
     * 当前返回 0
     *
     * @return 总是返回 0
     */
    public synchronized int loadAllSessions() {
        // TODO: 实现 C++ 持久化
        // Mock: 加载假数据到 sessions map
        Session session = new Session("native:default");
        Message msg1 = new Message();
        msg1.setRole("user");
        msg1.setContent("Hello");
        msg1.setTimestamp(System.currentTimeMillis() - 10000);
        session.addMessage(msg1);

        Message msg2 = new Message();
        msg2.setRole("assistant");
        msg2.setContent("Hi! I'm your AI assistant.");
        msg2.setTimestamp(System.currentTimeMillis() - 5000);
        session.addMessage(msg2);

        sessions.put("native:default", session);
        System.out.println("[NativeMobileAgentApi] loadAllSessions: Mock loaded 1 session");
        return 1;
    }

    /**
     * 从 C++ 层加载会话 - Mock 空实现
     *
     * TODO: 后续 C++ 模块开发时，实现 nativeLoadSession() JNI 接口
     * 当前返回 null
     *
     * @param sessionKey 会话键
     * @return 总是返回 null
     */
    public synchronized Session loadSessionFromCore(String sessionKey) {
        // TODO: 实现 C++ 持久化
        // Mock 返回一个包含历史消息的 Session
        Session session = new Session(sessionKey);
        Message msg1 = new Message();
        msg1.setRole("user");
        msg1.setContent("Hello from C++");
        msg1.setTimestamp(System.currentTimeMillis() - 10000);
        session.addMessage(msg1);

        Message msg2 = new Message();
        msg2.setRole("assistant");
        msg2.setContent("Hi! This is loaded from C++ layer.");
        msg2.setTimestamp(System.currentTimeMillis() - 5000);
        session.addMessage(msg2);

        System.out.println("[NativeMobileAgentApi] loadSessionFromCore: Mock returning session");
        return session;
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
