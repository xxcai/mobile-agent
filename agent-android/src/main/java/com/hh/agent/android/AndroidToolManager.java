package com.hh.agent.android;

import android.app.Activity;
import android.content.Context;
import com.hh.agent.android.channel.AndroidToolChannelExecutor;
import com.hh.agent.android.channel.DescribeShortcutChannel;
import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.ShortcutRuntimeChannel;
import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.channel.WebActionToolChannel;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.selection.CandidateSelectionResolver;
import com.hh.agent.android.selection.CandidateSelectionStateStore;
import com.hh.agent.android.selection.ResolveCandidateSelectionShortcut;
import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.android.ui.ToolUiPolicyResolver;
import com.hh.agent.core.tool.AndroidToolCallback;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import com.hh.agent.core.api.impl.NativeMobileAgentApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Android Tool Manager.
 * Loads tools configuration and routes tool calls to implementations.
 */
public class AndroidToolManager implements AndroidToolCallback {

    private static AndroidToolManager activeInstance;

    private Context context;
    private final ShortcutRuntime shortcutRuntime;
    private final CandidateSelectionStateStore candidateSelectionStateStore;
    private final ToolProfilePolicy toolProfilePolicy;
    private final Map<String, AndroidToolChannelExecutor> channels = new HashMap<>();

    public AndroidToolManager(Context context) {
        this(context, new ShortcutRuntime(), null, new ToolProfilePolicy(AgentRuntimeProfiles.FULL));
    }

    public AndroidToolManager(Context context, ToolProfilePolicy toolProfilePolicy) {
        this(context, new ShortcutRuntime(), null, toolProfilePolicy);
    }

    AndroidToolManager(Context context,
                       ShortcutRuntime shortcutRuntime,
                       Collection<? extends AndroidToolChannelExecutor> initialChannels) {
        this(context, shortcutRuntime, initialChannels, new ToolProfilePolicy(AgentRuntimeProfiles.FULL));
    }

    AndroidToolManager(Context context,
                       ShortcutRuntime shortcutRuntime,
                       Collection<? extends AndroidToolChannelExecutor> initialChannels,
                       ToolProfilePolicy toolProfilePolicy) {
        this.context = context;
        this.shortcutRuntime = shortcutRuntime != null ? shortcutRuntime : new ShortcutRuntime();
        this.candidateSelectionStateStore = new CandidateSelectionStateStore();
        this.toolProfilePolicy = toolProfilePolicy != null
                ? toolProfilePolicy
                : new ToolProfilePolicy(AgentRuntimeProfiles.FULL);
        registerBuiltinShortcuts();
        if (initialChannels == null) {
            registerDefaultChannels();
            return;
        }
        for (AndroidToolChannelExecutor channel : initialChannels) {
            registerChannel(channel);
        }
    }

    private void registerDefaultChannels() {
        if (AgentRuntimeProfiles.FULL.equals(toolProfilePolicy.getProfile())) {
            registerChannel(new ShortcutRuntimeChannel(shortcutRuntime, candidateSelectionStateStore));
            registerChannel(new DescribeShortcutChannel(shortcutRuntime));
        }
        registerChannel(new GestureToolChannel());
        registerChannel(new WebActionToolChannel());
        registerChannel(context == null
                ? ViewContextToolChannel.createForJvmTests()
                : new ViewContextToolChannel());
    }

    private void registerBuiltinShortcuts() {
        shortcutRuntime.register(new ResolveCandidateSelectionShortcut(
                candidateSelectionStateStore,
                new CandidateSelectionResolver()));
    }

    /**
     * Initialize and load tools from configuration.
     * Note: Default app-owned business capabilities should now be registered via shortcut APIs.
     */
    public void initialize() {
        AgentLogs.info("AndroidToolManager", "initialize_start",
                "channel_count=" + channels.size()
                        + " shortcut_count=" + shortcutRuntime.getRegisteredShortcuts().size());
        activeInstance = this;

        // Register callback with NativeMobileAgentApi
        NativeMobileAgentApi.getInstance().setToolCallback(this);
        AgentLogs.info("AndroidToolManager", "callback_registered", null);
    }

    public static ToolUiDecision resolveToolUiDecision(String toolName, String argumentsJson) {
        if (activeInstance == null) {
            return ToolUiDecision.hidden();
        }
        return activeInstance.buildToolUiPolicyResolver().resolve(toolName, argumentsJson);
    }

    public void registerShortcut(ShortcutExecutor executor) {
        shortcutRuntime.register(executor);
        AgentLogs.info("AndroidToolManager", "shortcut_registered",
                "shortcut_name=" + executor.getDefinition().getName());
    }

    public Map<String, ShortcutExecutor> getRegisteredShortcuts() {
        return shortcutRuntime.getRegisteredShortcuts();
    }

    public void registerShortcuts(Collection<? extends ShortcutExecutor> shortcutExecutors) {
        if (shortcutExecutors == null) {
            throw new IllegalArgumentException("Shortcut executors cannot be null");
        }
        for (ShortcutExecutor executor : shortcutExecutors) {
            registerShortcut(executor);
        }
    }

    /**
     * Register a top-level tool channel executor.
     *
     * @param channelExecutor The channel executor to register
     */
    public void registerChannel(AndroidToolChannelExecutor channelExecutor) {
        if (channelExecutor == null) {
            throw new IllegalArgumentException("Channel executor cannot be null");
        }

        String channelName = channelExecutor.getChannelName();
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new IllegalArgumentException("Channel name cannot be null or empty");
        }
        if (channels.containsKey(channelName)) {
            throw new IllegalArgumentException("Channel with name '" + channelName + "' is already registered");
        }

        channels.put(channelName, channelExecutor);
        AgentLogs.info("AndroidToolManager", "channel_registered", "channel_name=" + channelName);
    }

    /**
     * Returns a copy of registered channels for debugging/tests.
     */
    public Map<String, AndroidToolChannelExecutor> getRegisteredChannels() {
        return new HashMap<>(channels);
    }

    public ToolUiPolicyResolver buildToolUiPolicyResolver() {
        return new ToolUiPolicyResolver(getRegisteredChannels());
    }


    /**
     * 动态生成 tools.json
     * 聚合所有已注册的顶层 tool channels。
     */
    public String generateToolsJsonString() {
        return generateToolsJson();
    }

    private String generateToolsJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);

            JSONArray toolsArray = new JSONArray();

            for (AndroidToolChannelExecutor channelExecutor : channels.values()) {
                toolsArray.put(channelExecutor.buildToolDefinition());
            }
            root.put("tools", toolsArray);
            AgentLogs.info("AndroidToolManager", "tools_json_generated",
                    "channel_count=" + channels.size()
                            + " profile=" + toolProfilePolicy.getProfile());
            return root.toString();
        } catch (Exception e) {
            AgentLogs.error("AndroidToolManager", "generate_tools_json_failed", "message=" + e.getMessage(), e);
            return "";
        }
    }

    private Activity getActivity() {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        throw new IllegalStateException("Context must be an Activity");
    }

    /**
     * 清理 Context 引用，避免内存泄漏
     */
    public void clearContext() {
        this.context = null;
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    public String callTool(String toolName, String argsJson) {
        return callTool(toolName, argsJson, null);
    }

    @Override
    public String callTool(String toolName, String argsJson, String sessionKey) {
        try {
            AgentLogs.info("AndroidToolManager", "tool_call_start", "channel=" + toolName);
            AndroidToolChannelExecutor channelExecutor = channels.get(toolName);
            if (channelExecutor == null) {
                AgentLogs.warn("AndroidToolManager", "tool_channel_unsupported", "channel=" + toolName);
                return ToolResult.error(
                        "unsupported_tool_channel",
                        "Tool channel '" + toolName + "' is not supported"
                ).toJsonString();
            }

            JSONObject params = new JSONObject(argsJson);
            if (sessionKey != null && !sessionKey.trim().isEmpty()) {
                params.put("_sessionKey", sessionKey.trim());
            }
            ToolResult result = channelExecutor.execute(params);
            String resultJson = result.toJsonString();
            boolean resultSuccess = resultJson.contains("\"success\":true");
            AgentLogs.info("AndroidToolManager", "tool_call_complete",
                    "channel=" + toolName + " result_success=" + resultSuccess);
            return resultJson;
        } catch (org.json.JSONException e) {
            AgentLogs.warn("AndroidToolManager", "tool_call_invalid_args", "channel=" + toolName + " message=" + e.getMessage());
            return ToolResult.error("invalid_args", e.getMessage()).toJsonString();
        } catch (Exception e) {
            AgentLogs.error("AndroidToolManager", "tool_call_failed", "channel=" + toolName + " message=" + e.getMessage(), e);
            return ToolResult.error("execution_failed", e.getMessage()).toJsonString();
        }
    }

}
