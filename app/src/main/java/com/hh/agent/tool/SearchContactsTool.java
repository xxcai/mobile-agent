package com.hh.agent.tool;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

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
        return ToolDefinition.builder("查找联系人", "按姓名或关键字搜索联系人")
                .intentExamples("查找张三", "搜索联系人李四", "找一下王五是不是联系人")
                .stringParam("query", "联系人姓名或搜索关键字", true, "张三")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            if (!args.has("query")) {
                return ToolResult.error("missing_required_param")
                        .with("param", "query");
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

                return ToolResult.success().withJson("result", contacts.toString());
            } else if ("李四".equals(query)) {
                // Scenario 2: Single match - use directly
                org.json.JSONArray contacts = new org.json.JSONArray();
                org.json.JSONObject contact = new org.json.JSONObject();
                contact.put("name", "李四");
                contact.put("id", "003");
                contact.put("department", "产品部");
                contacts.put(contact);

                return ToolResult.success().withJson("result", contacts.toString());
            } else {
                // No matches
                return ToolResult.success().withJson("result", "[]");
            }
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }
}
