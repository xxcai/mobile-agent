package com.hh.agent.library.api;

import android.content.Context;
import com.hh.agent.library.AndroidToolManager;
import com.hh.agent.library.NativeAgent;
import com.hh.agent.library.model.Message;
import com.hh.agent.library.model.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NativeNanobotApi 实现
 * 使用 NativeAgent JNI 调用本地 C++ Agent 引擎
 */
public class NativeNanobotApi implements NanobotApi {

    private static NativeNanobotApi instance;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private boolean initialized = false;
    private AndroidToolManager toolManager;

    private NativeNanobotApi() {
    }

    /**
     * 获取单例实例
     */
    public static synchronized NativeNanobotApi getInstance() {
        if (instance == null) {
            instance = new NativeNanobotApi();
        }
        return instance;
    }

    /**
     * 初始化 Native Agent
     *
     * @param context Android Context (required for Android tools and assets)
     * @param configPath 配置文件路径
     */
    public synchronized void initialize(Context context, String configPath) {
        if (!initialized) {
            try {
                int result = NativeAgent.nativeInitialize(configPath);
                if (result != 0) {
                    throw new RuntimeException("Native agent initialization failed with code: " + result);
                }
                initialized = true;

                // Load tools.json from assets and pass to native layer
                if (context != null) {
                    try {
                        String toolsJson = loadToolsFromAssets(context);
                        if (toolsJson != null && !toolsJson.isEmpty()) {
                            NativeAgent.nativeSetToolsSchema(toolsJson);
                            android.util.Log.i("NativeNanobotApi", "Successfully loaded and passed tools.json to native layer");
                        } else {
                            android.util.Log.w("NativeNanobotApi", "tools.json is empty, skipping native registration");
                        }
                    } catch (Exception e) {
                        // Log but don't fail initialization
                        android.util.Log.w("NativeNanobotApi", "Failed to load tools.json: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize native agent: " + e.getMessage(), e);
            }
        }

        // Initialize Android Tool Manager
        if (toolManager == null && context != null) {
            toolManager = new AndroidToolManager(context);
            toolManager.initialize();
        }
    }

    /**
     * Load tools.json from assets
     */
    private String loadToolsFromAssets(Context context) {
        try {
            java.io.InputStream is = context.getAssets().open("tools.json");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (java.io.IOException e) {
            android.util.Log.e("NativeNanobotApi", "Error reading tools.json: " + e.getMessage());
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
