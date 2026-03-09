package com.hh.agent.android.tool;

import com.hh.agent.library.ToolExecutor;

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
    public String execute(org.json.JSONObject args) {
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

            // Mock: Simply return success with formatted message
            return "{\"success\": true, \"result\": \"消息已发送给 " + contactId + ": " + message + "\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getDescription() {
        return "发送即时消息";
    }

    @Override
    public String getArgsDescription() {
        return "contact_id: 联系人ID, message: 消息内容";
    }

    @Override
    public String getArgsSchema() {
        return "{\"type\":\"object\",\"properties\":{\"contact_id\":{\"type\":\"string\",\"description\":\"联系人ID\"},\"message\":{\"type\":\"string\",\"description\":\"消息内容\"}},\"required\":[\"contact_id\",\"message\"]}";
    }
}
