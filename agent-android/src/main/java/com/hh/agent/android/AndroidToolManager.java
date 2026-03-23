package com.hh.agent.android;

import android.content.Context;
import com.hh.agent.android.channel.AndroidToolChannelExecutor;
import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.LegacyAndroidToolChannel;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.android.ui.ToolUiPolicyResolver;
import com.hh.agent.core.tool.AndroidToolCallback;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;
import com.hh.agent.core.api.impl.NativeMobileAgentApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Android Tool Manager.
 * Loads tools configuration and routes tool calls to implementations.
 */
public class AndroidToolManager implements AndroidToolCallback {

    private static AndroidToolManager activeInstance;

    private Context context;
    private final Map<String, ToolExecutor> tools = new HashMap<>();
    private final Map<String, AndroidToolChannelExecutor> channels = new HashMap<>();

    public AndroidToolManager(Context context) {
        this.context = context;
        registerChannel(new LegacyAndroidToolChannel(tools));
        registerChannel(new GestureToolChannel());
    }

    /**
     * Initialize and load tools from configuration.
     * Note: Built-in tools are now registered via registerTool() from app layer.
     */
    public void initialize() {
        AgentLogs.info("AndroidToolManager", "initialize_start",
                "channel_count=" + channels.size() + " tool_count=" + tools.size());
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

    /**
     * Register a ToolExecutor to the tool manager.
     * This allows app layer to dynamically register custom tools at runtime.
     *
     * @param executor The ToolExecutor to register
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    public void registerTool(ToolExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ToolExecutor cannot be null");
        }

        String toolName = executor.getName();
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        // Check for duplicate tool registration
        if (tools.containsKey(toolName)) {
            throw new IllegalArgumentException("Tool with name '" + toolName + "' is already registered");
        }

        // Add the tool to the registry
        tools.put(toolName, executor);
        AgentLogs.info("AndroidToolManager", "tool_registered", "tool_name=" + toolName);
    }

    /**
     * Get all registered tools.
     * Returns a copy of the internal tools map to prevent external modification.
     *
     * @return Map of tool name to ToolExecutor (never null, may be empty)
     */
    public Map<String, ToolExecutor> getRegisteredTools() {
        return new HashMap<>(tools);
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
     * Unregister a tool by name.
     *
     * @param toolName The name of the tool to unregister
     * @return true if the tool was found and removed, false if tool did not exist
     */
    public boolean unregisterTool(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }

        if (!tools.containsKey(toolName)) {
            AgentLogs.warn("AndroidToolManager", "tool_unregister_skipped", "tool_name=" + toolName);
            return false;
        }

        tools.remove(toolName);
        AgentLogs.info("AndroidToolManager", "tool_unregistered", "tool_name=" + toolName);

        return true;
    }

    /**
     * Register multiple tools at once with atomicity.
     * Validates all tools first, then applies changes only if all validations pass.
     *
     * @param toolsToRegister Map of tool name to ToolExecutor to register
     * @return true if all tools were registered successfully
     * @throws IllegalArgumentException if validation fails (null tool, empty name, duplicate, or conflict with existing)
     */
    public boolean registerTools(Map<String, ToolExecutor> toolsToRegister) {
        if (toolsToRegister == null) {
            throw new IllegalArgumentException("Tools map cannot be null");
        }

        if (toolsToRegister.isEmpty()) {
            return true;
        }

        // Validate all tools first (atomic check)
        for (Map.Entry<String, ToolExecutor> entry : toolsToRegister.entrySet()) {
            String toolName = entry.getKey();
            ToolExecutor executor = entry.getValue();

            if (executor == null) {
                throw new IllegalArgumentException("ToolExecutor cannot be null");
            }
            if (toolName == null || toolName.trim().isEmpty()) {
                throw new IllegalArgumentException("Tool name cannot be null or empty");
            }
            if (tools.containsKey(toolName)) {
                throw new IllegalArgumentException("Tool with name '" + toolName + "' already exists");
            }
        }

        // All validations passed, now register all tools
        for (Map.Entry<String, ToolExecutor> entry : toolsToRegister.entrySet()) {
            String toolName = entry.getKey();
            ToolExecutor executor = entry.getValue();
            tools.put(toolName, executor);
            AgentLogs.info("AndroidToolManager", "tool_registered", "tool_name=" + toolName + " mode=batch");
        }

        return true;
    }

    /**
     * Unregister multiple tools at once with atomicity.
     * Validates all tool names exist first, then applies changes only if all validations pass.
     *
     * @param toolNames List of tool names to unregister
     * @return true if all tools were unregistered successfully
     * @throws IllegalArgumentException if any tool does not exist
     */
    public boolean unregisterTools(ArrayList<String> toolNames) {
        if (toolNames == null) {
            throw new IllegalArgumentException("Tool names list cannot be null");
        }

        if (toolNames.isEmpty()) {
            return true;
        }

        // Validate all tools exist first (atomic check)
        for (String toolName : toolNames) {
            if (toolName == null || toolName.trim().isEmpty()) {
                throw new IllegalArgumentException("Tool name cannot be null or empty");
            }
            if (!tools.containsKey(toolName)) {
                throw new IllegalArgumentException("Tool with name '" + toolName + "' does not exist");
            }
        }

        // All validations passed, now unregister all tools
        for (String toolName : toolNames) {
            tools.remove(toolName);
            AgentLogs.info("AndroidToolManager", "tool_unregistered", "tool_name=" + toolName + " mode=batch");
        }

        return true;
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
            AgentLogs.info("AndroidToolManager", "tools_json_generated", "channel_count=" + channels.size());
            return root.toString();
        } catch (Exception e) {
            AgentLogs.error("AndroidToolManager", "generate_tools_json_failed", "message=" + e.getMessage(), e);
            return "";
        }
    }

    private android.app.Activity getActivity() {
        if (context instanceof android.app.Activity) {
            return (android.app.Activity) context;
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

    @Override
    public String callTool(String toolName, String argsJson) {
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
