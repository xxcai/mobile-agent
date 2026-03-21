package com.hh.agent.tool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.hh.agent.core.ToolDefinition;
import com.hh.agent.core.ToolExecutor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * ReadClipboard tool implementation.
 * Reads text content from the Android clipboard.
 */
public class ReadClipboardTool implements ToolExecutor {

    private final Context context;

    public ReadClipboardTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "read_clipboard";
    }

    @Override
    public ToolDefinition getDefinition() {
        try {
            return new ToolDefinition(
                    "读取剪贴板",
                    "读取当前剪贴板中的文本内容",
                    Arrays.asList("看看剪贴板里是什么", "读取当前复制的内容"),
                    new JSONObject()
                            .put("type", "object")
                            .put("properties", new JSONObject())
                            .put("required", new JSONArray()),
                    new JSONObject()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool definition for read_clipboard", e);
        }
    }

    @Override
    public String execute(JSONObject args) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);

            if (clipboardManager == null) {
                return "{\"success\": false, \"error\": \"clipboard_unavailable\"}";
            }

            if (!clipboardManager.hasPrimaryClip()) {
                return "{\"success\": true, \"content\": \"\"}";
            }

            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return "{\"success\": true, \"content\": \"\"}";
            }

            CharSequence text = clip.getItemAt(0).getText();
            String content = (text != null) ? text.toString() : "";

            return "{\"success\": true, \"content\": \"" + escapeJson(content) + "\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
