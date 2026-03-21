package com.hh.agent.tool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.hh.agent.core.ToolDefinition;
import com.hh.agent.core.ToolExecutor;
import com.hh.agent.core.ToolResult;
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
    public ToolResult execute(JSONObject args) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);

            if (clipboardManager == null) {
                return ToolResult.error("clipboard_unavailable");
            }

            if (!clipboardManager.hasPrimaryClip()) {
                return ToolResult.success().with("content", "");
            }

            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return ToolResult.success().with("content", "");
            }

            CharSequence text = clip.getItemAt(0).getText();
            String content = (text != null) ? text.toString() : "";

            return ToolResult.success().with("content", content);
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }
}
