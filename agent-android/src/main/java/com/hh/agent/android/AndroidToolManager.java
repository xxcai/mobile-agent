package com.hh.agent.android;

import android.content.Context;
import android.util.Log;
import com.hh.agent.android.tool.ShowToastTool;
import com.hh.agent.android.tool.DisplayNotificationTool;
import com.hh.agent.android.tool.ReadClipboardTool;
import com.hh.agent.android.tool.TakeScreenshotTool;
import com.hh.agent.android.tool.SearchContactsTool;
import com.hh.agent.android.tool.SendImMessageTool;
import com.hh.agent.library.AndroidToolCallback;
import com.hh.agent.library.NativeAgent;
import com.hh.agent.library.ToolExecutor;
import com.hh.agent.library.api.NativeMobileAgentApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
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
     */
    public void initialize() {
        Log.i("AndroidToolManager", "Initializing AndroidToolManager");

        // Register built-in tools
        tools.put("show_toast", new ShowToastTool(getActivity()));
        tools.put("display_notification", new DisplayNotificationTool(getActivity()));
        tools.put("read_clipboard", new ReadClipboardTool(getActivity()));
        tools.put("take_screenshot", new TakeScreenshotTool(getActivity()));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());
        Log.i("AndroidToolManager", "Registered 6 tools: show_toast, display_notification, read_clipboard, take_screenshot, search_contacts, send_im_message");

        // Load tools.json from assets
        loadToolsConfig();
        Log.i("AndroidToolManager", "Loaded tools config, version: " + configVersion);

        // Register callback with NativeMobileAgentApi
        NativeMobileAgentApi.getInstance().setToolCallback(this);
        Log.i("AndroidToolManager", "Registered AndroidToolCallback with NativeMobileAgentApi");
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
