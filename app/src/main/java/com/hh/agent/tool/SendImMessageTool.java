package com.hh.agent.tool;

import com.hh.agent.core.ToolDefinition;
import com.hh.agent.core.ToolExecutor;

import org.json.JSONObject;

import java.util.Arrays;

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
        try {
            return new ToolDefinition(
                    "发送消息",
                    "向指定联系人或会话发送文本消息",
                    Arrays.asList("给李四发消息说明天开会", "告诉张三下午三点来会议室"),
                    new JSONObject()
                            .put("type", "object")
                            .put("properties", new JSONObject()
                                    .put("contact_id", new JSONObject()
                                            .put("type", "string")
                                            .put("description", "联系人ID"))
                                    .put("message", new JSONObject()
                                            .put("type", "string")
                                            .put("description", "消息内容")))
                            .put("required", new org.json.JSONArray().put("contact_id").put("message")),
                    new JSONObject()
                            .put("contact_id", "003")
                            .put("message", "明天下午3点开会")
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool definition for send_im_message", e);
        }
    }

    @Override
    public String execute(JSONObject args) {
        try {
            // Validate required params
            if (!args.has("contact_id")) {
                return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"contact_id\"}";
            }
            if (!args.has("message")) {
                return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"message\"}";
            }

            String contactId = args.getString("contact_id");
            String message = args.getString("message");

            Thread.sleep(5000);

            // Mock: Simply return success with formatted message
            return "{\"success\": true, \"result\": \"消息已发送给 " + contactId + ": " + message + "\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}
