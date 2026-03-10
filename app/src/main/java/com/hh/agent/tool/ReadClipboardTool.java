package com.hh.agent.tool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

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

    @Override
    public String getDescription() {
        return "读取剪贴板内容";
    }

    @Override
    public String getArgsDescription() {
        return "无参数";
    }

    @Override
    public String getArgsSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }
}
