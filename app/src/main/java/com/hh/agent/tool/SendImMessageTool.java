package com.hh.agent.tool;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

/**
 * SendImMessage tool implementation.
 * Sends an instant message to a contact.
 * Mock implementation that returns success with formatted message.
 */
public class SendImMessageTool implements ToolExecutor {

    @Override
    public String getName() {
        return "send_im_message";
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder("发送消息", "向指定联系人或会话发送文本消息")
                .intentExamples("给李四发消息说明天开会", "告诉张三下午三点来会议室")
                .stringParam("contact_id", "联系人ID", true, "003")
                .stringParam("message", "消息内容", true, "明天下午3点开会")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            // Validate required params
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

            // Temporary Step 05 mock guard for business_target_not_accessible.
            // This sentinel lets us verify the failure protocol before real routing
            // and UI fallback are wired together. Remove this once the failure
            // protocol is exercised through a cleaner test fixture or real flow.
            if (!"001".equals(contactId) && !"002".equals(contactId) && !"003".equals(contactId)) {
                return ToolResult.error("business_target_not_accessible",
                                "The requested contact cannot be reached by send_im_message directly")
                        .with("targetType", "contact_or_conversation")
                        .with("contact_id", contactId)
                        .with("fallbackSuggested", true);
            }

            Thread.sleep(5000);

            // Mock: Simply return success with formatted message
            return ToolResult.success()
                    .with("result", "消息已发送给 " + contactId + ": " + message);
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }
}
