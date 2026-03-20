package com.hh.agent.android;

import android.content.Context;
import android.util.Log;
import com.hh.agent.android.channel.AndroidToolChannelExecutor;
import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.LegacyAndroidToolChannel;
import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.android.ui.ToolUiPolicyResolver;
import com.hh.agent.core.AndroidToolCallback;
import com.hh.agent.core.ToolExecutor;
import com.hh.agent.core.api.NativeMobileAgentApi;
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
        Log.i("AndroidToolManager", "Initializing AndroidToolManager");
        activeInstance = this;

        // Register callback with NativeMobileAgentApi
        NativeMobileAgentApi.getInstance().setToolCallback(this);
        Log.i("AndroidToolManager", "Registered AndroidToolCallback with NativeMobileAgentApi");
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
        Log.i("AndroidToolManager", "Registered tool: " + toolName);
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
        Log.i("AndroidToolManager", "Registered channel: " + channelName);
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
            Log.i("AndroidToolManager", "Tool not found for unregister: " + toolName);
            return false;
        }

        tools.remove(toolName);
        Log.i("AndroidToolManager", "Unregistered tool: " + toolName);

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
            Log.i("AndroidToolManager", "Registered tool (batch): " + toolName);
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
            Log.i("AndroidToolManager", "Unregistered tool (batch): " + toolName);
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

            return root.toString();
        } catch (Exception e) {
            Log.e("AndroidToolManager", "Failed to generate tools.json: " + e.getMessage());
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
            AndroidToolChannelExecutor channelExecutor = channels.get(toolName);
            if (channelExecutor == null) {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", "unsupported_tool_channel");
                error.put("message", "Tool channel '" + toolName + "' is not supported");
                return error.toString();
            }

            JSONObject params = new JSONObject(argsJson);
            return channelExecutor.execute(params);
        } catch (org.json.JSONException e) {
            JSONObject error = new JSONObject();
            try {
                error.put("success", false);
                error.put("error", "invalid_args");
                error.put("message", e.getMessage());
            } catch (org.json.JSONException ignored) {}
            return error.toString();
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            try {
                error.put("success", false);
                error.put("error", "execution_failed");
                error.put("message", e.getMessage());
            } catch (org.json.JSONException ignored) {}
            return error.toString();
        }
    }

}
