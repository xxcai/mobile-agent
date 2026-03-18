package com.hh.agent.android.presenter;

import android.content.Context;
import com.hh.agent.library.AgentEventListener;
import com.hh.agent.library.api.MobileAgentApi;
import com.hh.agent.library.model.Message;
import com.hh.agent.library.model.Session;
import com.hh.agent.android.WorkspaceManager;
import com.hh.agent.library.api.NativeMobileAgentApi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * NativeMobileAgentApi 适配器
 * 将 agent 模块的 Message/Session 模型转换为 lib 模块的模型
 */
public class NativeMobileAgentApiAdapter implements MobileAgentApi {

    private final NativeMobileAgentApi nativeApi;
    private static String configJson = "";

    public NativeMobileAgentApiAdapter() {
        this.nativeApi = NativeMobileAgentApi.getInstance();
    }

    /**
     * 从 assets 读取配置文件
     */
    public static void loadConfigFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("config.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            configJson = new String(buffer, "UTF-8");
            is.close();
        } catch (Exception e) {
            configJson = "";
        }
    }

    private Context context;

    /**
     * 清理 Context 引用，避免内存泄漏
     */
    public void clearContext() {
        this.context = null;
    }

    /**
     * 初始化 Native Agent（带 toolsJson）
     * @param toolsJson 工具定义 JSON 字符串
     * @param context Android Context
     */
    public void initialize(String toolsJson, Context context) {
        this.context = context;
        try {
            // 先尝试加载 native 库
            System.loadLibrary("icraw");

            // 初始化 workspace
            if (context != null) {
                WorkspaceManager workspaceManager = new WorkspaceManager(context);
                String workspacePath = workspaceManager.initialize();
                // 将 workspace 路径添加到配置中
                if (!workspacePath.isEmpty()) {
                    int lastBrace = configJson.lastIndexOf('}');
                    if (lastBrace > 0) {
                        String newField = ",\"workspacePath\":\"" + workspacePath + "\"";
                        configJson = configJson.substring(0, lastBrace) + newField + configJson.substring(lastBrace);
                    }
                }
            }

            // 初始化 native agent，传入 toolsJson
            nativeApi.initialize(toolsJson, configJson);
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException("Failed to load native library: " + e.getMessage(), e);
        }
    }

    @Override
    public Session createSession(String channel, String chatId) {
        // 不需要转换，返回 null 即可，getSession 会自动创建
        return null;
    }

    @Override
    public Message sendMessage(String content, String sessionKey) {
        // Not used - use sendMessageStream instead
        throw new UnsupportedOperationException("Use sendMessageStream instead");
    }

    @Override
    public Session getSession(String sessionKey) {
        com.hh.agent.library.model.Session agentSession = nativeApi.getSession(sessionKey);
        if (agentSession == null) {
            // 自动创建会话
            agentSession = nativeApi.createSession("default", sessionKey);
        }
        // 将 AgentSession 转换为 lib Session
        return convertSession(agentSession, sessionKey);
    }

    @Override
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        List<com.hh.agent.library.model.Message> agentMessages = nativeApi.getHistory(sessionKey, maxMessages);
        List<Message> messages = new ArrayList<>();
        for (com.hh.agent.library.model.Message agentMessage : agentMessages) {
            messages.add(convertMessage(agentMessage));
        }
        return messages;
    }

    @Override
    public void sendMessageStream(String content, String sessionKey, AgentEventListener listener) {
        nativeApi.sendMessageStream(content, sessionKey, listener);
    }

    /**
     * 将 agent 模块的 Message 转换为 lib 模块的 Message
     */
    private Message convertMessage(com.hh.agent.library.model.Message agentMessage) {
        if (agentMessage == null) {
            return null;
        }
        Message message = new Message();
        message.setId(agentMessage.getId());
        message.setRole(agentMessage.getRole());
        message.setContent(agentMessage.getContent());
        message.setTimestamp(agentMessage.getTimestamp());
        return message;
    }

    /**
     * 将 agent 模块的 Session 转换为 lib 模块的 Session
     */
    private Session convertSession(com.hh.agent.library.model.Session agentSession, String sessionKey) {
        if (agentSession == null) {
            return new Session(sessionKey);
        }
        Session session = new Session(sessionKey);
        // Session 模型可能没有消息列表，暂不转换
        return session;
    }
}
