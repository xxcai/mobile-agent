package com.hh.agent.tool;

import com.hh.agent.core.ToolDefinition;
import com.hh.agent.core.ToolExecutor;

import org.json.JSONObject;

import java.util.Arrays;

/**
 * SearchContacts tool implementation.
 * Searches for contacts by name query.
 * Mock implementation with test data:
 * - Query "张三" returns duplicate contacts (multiple matches)
 * - Query "李四" returns single contact
 * - Other queries return empty array
 */
public class SearchContactsTool implements ToolExecutor {

    @Override
    public String getName() {
        return "search_contacts";
    }

    @Override
    public ToolDefinition getDefinition() {
        try {
            return new ToolDefinition(
                    "查找联系人",
                    "按姓名或关键字搜索联系人",
                    Arrays.asList("查找张三", "搜索联系人李四", "找一下王五是不是联系人"),
                    new JSONObject()
                            .put("type", "object")
                            .put("properties", new JSONObject()
                                    .put("query", new JSONObject()
                                            .put("type", "string")
                                            .put("description", "联系人姓名或搜索关键字")))
                            .put("required", new org.json.JSONArray().put("query")),
                    new JSONObject().put("query", "张三")
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool definition for search_contacts", e);
        }
    }

    @Override
    public String execute(JSONObject args) {
        try {
            if (!args.has("query")) {
                return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"query\"}";
            }

            String query = args.getString("query");

            Thread.sleep(5000);

            // Mock data: duplicate names and unique name scenarios
            if ("张三".equals(query)) {
                // Scenario 1: Multiple matches - return all for user selection
                org.json.JSONArray contacts = new org.json.JSONArray();
                org.json.JSONObject contact1 = new org.json.JSONObject();
                contact1.put("name", "张三");
                contact1.put("id", "001");
                contact1.put("department", "技术部");
                contacts.put(contact1);

                org.json.JSONObject contact2 = new org.json.JSONObject();
                contact2.put("name", "张三");
                contact2.put("id", "002");
                contact2.put("department", "市场部");
                contacts.put(contact2);

                return "{\"success\": true, \"result\": " + contacts.toString() + "}";
            } else if ("李四".equals(query)) {
                // Scenario 2: Single match - use directly
                org.json.JSONArray contacts = new org.json.JSONArray();
                org.json.JSONObject contact = new org.json.JSONObject();
                contact.put("name", "李四");
                contact.put("id", "003");
                contact.put("department", "产品部");
                contacts.put(contact);

                return "{\"success\": true, \"result\": " + contacts.toString() + "}";
            } else {
                // No matches
                return "{\"success\": true, \"result\": []}";
            }
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}
