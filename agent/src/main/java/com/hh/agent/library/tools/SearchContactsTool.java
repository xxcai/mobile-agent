package com.hh.agent.library.tools;

import com.hh.agent.library.ToolExecutor;

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
    public String execute(org.json.JSONObject args) {
        try {
            if (!args.has("query")) {
                return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"query\"}";
            }

            String query = args.getString("query");

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
