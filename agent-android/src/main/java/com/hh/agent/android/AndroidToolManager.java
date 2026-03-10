package com.hh.agent.android;

import android.content.Context;
import android.util.Log;
import com.hh.agent.library.AndroidToolCallback;
import com.hh.agent.library.ToolExecutor;
import com.hh.agent.library.api.NativeMobileAgentApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Android Tool Manager.
 * Loads tools configuration and routes tool calls to implementations.
 */
public class AndroidToolManager implements AndroidToolCallback {

    private Context context;
    private final Map<String, ToolExecutor> tools = new HashMap<>();
    private int configVersion = 0;

    public AndroidToolManager(Context context) {
        this.context = context;
    }

    /**
     * Initialize and load tools from configuration.
     * Note: Built-in tools are now registered via registerTool() from app layer.
     */
    public void initialize() {
        Log.i("AndroidToolManager", "Initializing AndroidToolManager");

        // Load tools.json from assets
        loadToolsConfig();
        Log.i("AndroidToolManager", "Loaded tools config, version: " + configVersion);

        // Register callback with NativeMobileAgentApi
        NativeMobileAgentApi.getInstance().setToolCallback(this);
        Log.i("AndroidToolManager", "Registered AndroidToolCallback with NativeMobileAgentApi");

        // Generate and set tools.json dynamically
        String toolsJson = generateToolsJson();
        NativeMobileAgentApi.getInstance().setToolsJson(toolsJson);
        Log.i("AndroidToolManager", "Generated and set tools.json to native layer");
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

        // Generate and push updated tools.json to native layer
        String toolsJson = generateToolsJson();
        NativeMobileAgentApi.getInstance().setToolsJson(toolsJson);
        Log.i("AndroidToolManager", "Generated and pushed tools.json after registering: " + toolName);
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

        // Generate and push updated tools.json to native layer
        String toolsJson = generateToolsJson();
        NativeMobileAgentApi.getInstance().setToolsJson(toolsJson);
        Log.i("AndroidToolManager", "Generated and pushed tools.json after unregistering: " + toolName);

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
    public boolean registerTools(HashMap<String, ToolExecutor> toolsToRegister) {
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

        // Generate and push updated tools.json to native layer (single push)
        String toolsJson = generateToolsJson();
        NativeMobileAgentApi.getInstance().setToolsJson(toolsJson);
        Log.i("AndroidToolManager", "Generated and pushed tools.json after batch registering " + toolsToRegister.size() + " tools");

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

        // Generate and push updated tools.json to native layer (single push)
        String toolsJson = generateToolsJson();
        NativeMobileAgentApi.getInstance().setToolsJson(toolsJson);
        Log.i("AndroidToolManager", "Generated and pushed tools.json after batch unregistering " + toolNames.size() + " tools");

        return true;
    }

    /**
     * 动态生成 tools.json
     * 遍历已注册的工具，收集每个 Tool 的信息拼接为 call_android_tool 格式的 JSON
     */
    private String generateToolsJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);

            JSONArray toolsArray = new JSONArray();

            // Build description listing all available tools
            StringBuilder descriptionBuilder = new StringBuilder("调用 Android 设备功能。可用功能:\n");

            // Collect tool names for enum
            JSONArray toolNames = new JSONArray();

            for (Map.Entry<String, ToolExecutor> entry : tools.entrySet()) {
                ToolExecutor executor = entry.getValue();
                String toolName = executor.getName();
                toolNames.put(toolName);

                // Append to description
                descriptionBuilder.append("- ").append(toolName).append(": ")
                        .append(executor.getDescription())
                        .append(", 参数: ")
                        .append(executor.getArgsDescription())
                        .append("\n");
            }

            // Create the call_android_tool function definition
            JSONObject functionObj = new JSONObject();
            functionObj.put("name", "call_android_tool");
            functionObj.put("description", descriptionBuilder.toString().trim());

            // Create parameters schema
            JSONObject params = new JSONObject();
            params.put("type", "object");

            JSONObject properties = new JSONObject();

            // function parameter (enum of tool names)
            JSONObject functionParam = new JSONObject();
            functionParam.put("type", "string");
            functionParam.put("description", "要调用的功能名称");
            functionParam.put("enum", toolNames);
            properties.put("function", functionParam);

            // args parameter (generic object)
            JSONObject argsParam = new JSONObject();
            argsParam.put("type", "object");
            argsParam.put("description", "功能参数，JSON 对象格式");
            properties.put("args", argsParam);

            params.put("properties", properties);

            JSONArray required = new JSONArray();
            required.put("function");
            required.put("args");
            params.put("required", required);

            functionObj.put("parameters", params);

            // Wrap in tool format
            JSONObject toolWrapper = new JSONObject();
            toolWrapper.put("type", "function");
            toolWrapper.put("function", functionObj);

            toolsArray.put(toolWrapper);
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
    }

    private void loadToolsConfig() {
        try {
            InputStream is = context.getAssets().open("tools.json");
            byte[] buffer = new byte[1024];
            int bytesRead = is.read(buffer);
            String jsonStr = new String(buffer, 0, bytesRead);
            is.close();

            JSONObject config = new JSONObject(jsonStr);
            configVersion = config.optInt("version", 1);

            // Load tool configurations from JSON (for future validation)
            JSONArray toolsArray = config.optJSONArray("tools");
            if (toolsArray != null) {
                for (int i = 0; i < toolsArray.length(); i++) {
                    JSONObject tool = toolsArray.getJSONObject(i);
                    // Currently we only support built-in tools
                    // Future: dynamic tool loading
                }
            }
        } catch (Exception e) {
            // If tools.json not found, use built-in tools only
        }
    }

    @Override
    public String callTool(String toolName, String argsJson) {
        try {
            JSONObject args = new JSONObject(argsJson);

            ToolExecutor executor = tools.get(toolName);
            if (executor == null) {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", "tool_not_found");
                error.put("message", "Tool '" + toolName + "' not found");
                return error.toString();
            }

            return executor.execute(args);
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

    /**
     * Get the configuration version.
     */
    public int getConfigVersion() {
        return configVersion;
    }
}
