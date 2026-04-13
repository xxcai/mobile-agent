package com.hh.agent.shortcut;

import com.hh.agent.android.selection.CandidateSelection;
import com.hh.agent.android.selection.CandidateSelectionItem;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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
            Thread.sleep(5000);

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
                return successWithCandidates(contacts);
            }

            if ("李四".equals(query)) {
                JSONArray contacts = new JSONArray();
                contacts.put(new JSONObject()
                        .put("name", "李四")
                        .put("id", "003")
                        .put("department", "产品部"));
                return ToolResult.success()
                        .withJson("result", contacts.toString())
                        .withJson("resolvedContact", contacts.getJSONObject(0).toString());
            }

            return ToolResult.success().withJson("result", "[]");
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }

    private ToolResult successWithCandidates(JSONArray contacts) {
        return ToolResult.success()
                .withJson("result", contacts.toString())
                .with("candidateCount", contacts.length())
                .withJson("candidateSelection", buildCandidateSelection(contacts).toJson().toString());
    }

    private CandidateSelection buildCandidateSelection(JSONArray contacts) {
        List<CandidateSelectionItem> items = new ArrayList<>();
        for (int index = 0; index < contacts.length(); index++) {
            JSONObject contact = contacts.optJSONObject(index);
            if (contact == null) {
                continue;
            }
            String name = contact.optString("name", null);
            String department = contact.optString("department", null);
            String label = buildLabel(name, department);
            items.add(new CandidateSelectionItem.Builder(index + 1, label)
                    .stableKey(contact.optString("id", null))
                    .alias(name)
                    .alias(department)
                    .alias(label)
                    .payload(buildPayload(contact, name, department))
                    .build());
        }
        return CandidateSelection.indexed("contact", items);
    }

    private JSONObject buildPayload(JSONObject contact, String name, String department) {
        try {
            return new JSONObject()
                    .put("contact_id", contact.optString("id", null))
                    .put("name", name)
                    .put("department", department);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize contact candidate payload", exception);
        }
    }

    private String buildLabel(String name, String department) {
        if (department == null || department.trim().isEmpty()) {
            return name == null ? "" : name;
        }
        return (name == null ? "" : name) + "（" + department.trim() + "）";
    }
}
