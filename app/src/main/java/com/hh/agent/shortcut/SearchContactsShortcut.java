package com.hh.agent.shortcut;

import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class SearchContactsShortcut implements ShortcutExecutor {

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder("search_contacts", "查找联系人", "按姓名或关键字搜索联系人")
                .domain("im")
                .requiredSkill("im_sender")
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

            if ("张三".equals(query)) {
                JSONArray contacts = new JSONArray();
                contacts.put(new JSONObject()
                        .put("name", "张三")
                        .put("id", "001")
                        .put("department", "技术部"));
                contacts.put(new JSONObject()
                        .put("name", "张三")
                        .put("id", "002")
                        .put("department", "市场部"));
                return ToolResult.success().withJson("result", contacts.toString());
            }

            if ("李四".equals(query)) {
                JSONArray contacts = new JSONArray();
                contacts.put(new JSONObject()
                        .put("name", "李四")
                        .put("id", "003")
                        .put("department", "产品部"));
                return ToolResult.success().withJson("result", contacts.toString());
            }

            return ToolResult.success().withJson("result", "[]");
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }
}
