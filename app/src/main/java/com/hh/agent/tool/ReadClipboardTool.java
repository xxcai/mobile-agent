package com.hh.agent.tool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.hh.agent.core.ToolDefinition;
import com.hh.agent.core.ToolExecutor;
import com.hh.agent.core.ToolResult;
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
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder("读取剪贴板", "读取当前剪贴板中的文本内容")
                .intentExamples("看看剪贴板里是什么", "读取当前复制的内容")
                .build();
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
