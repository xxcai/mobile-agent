package com.hh.agent.library.api;

import java.io.InputStream;
import java.io.BufferedReader;
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
     * 初始化 Native Agent
     *
     * @param toolsStream InputStream for tools.json (required for tool definitions)
     * @param configPath 配置文件路径
     */
    public synchronized void initialize(InputStream toolsStream, String configPath) {
        if (!initialized) {
            try {
                int result = NativeAgent.nativeInitialize(configPath);
                if (result != 0) {
                    throw new RuntimeException("Native agent initialization failed with code: " + result);
                }
                initialized = true;

                // Load tools.json from InputStream and pass to native layer
                if (toolsStream != null) {
                    try {
                        String toolsJson = loadToolsFromAssets(toolsStream);
                        if (toolsJson != null && !toolsJson.isEmpty()) {
                            NativeAgent.nativeSetToolsSchema(toolsJson);
                            System.out.println("[NativeMobileAgentApi] Successfully loaded and passed tools.json to native layer");
                        } else {
                            System.out.println("[NativeMobileAgentApi] tools.json is empty, skipping native registration");
                        }
                    } catch (Exception e) {
                        // Log but don't fail initialization
                        System.out.println("[NativeMobileAgentApi] Failed to load tools.json: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize native agent: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Load tools.json from InputStream
     */
    private String loadToolsFromAssets(InputStream toolsStream) {
        try {
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(toolsStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            System.err.println("[NativeMobileAgentApi] Error reading tools.json: " + e.getMessage());
            return null;
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
