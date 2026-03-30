package com.hh.agent.shortcut;

import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

public class SendImMessageShortcut implements ShortcutExecutor {

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder("send_im_message", "发送消息", "向指定联系人或会话发送文本消息")
                .domain("im")
                .requiredSkill("im_sender")
                .stringParam("contact_id", "联系人ID", true, "003")
                .stringParam("message", "消息内容", true, "明天下午3点开会")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            if (!args.has("contact_id")) {
                return ToolResult.error("missing_required_param")
                        .with("param", "contact_id");
            }
            if (!args.has("message")) {
                return ToolResult.error("missing_required_param")
                        .with("param", "message");
            }

            String contactId = args.getString("contact_id");
            String message = args.getString("message");

            if (!"001".equals(contactId) && !"002".equals(contactId) && !"003".equals(contactId)) {
                return ToolResult.error("business_target_not_accessible",
                                "The requested contact cannot be reached by send_im_message directly")
                        .with("targetType", "contact_or_conversation")
                        .with("contact_id", contactId)
                        .with("fallbackSuggested", true);
            }

            return ToolResult.success()
                    .with("result", "消息已发送给 " + contactId + ": " + message);
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }
}
