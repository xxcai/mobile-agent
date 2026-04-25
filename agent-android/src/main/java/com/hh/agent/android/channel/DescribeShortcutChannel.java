package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovery channel for querying a registered shortcut definition on demand.
 */
public class DescribeShortcutChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "describe_shortcut";

    private final ShortcutRuntime shortcutRuntime;

    public DescribeShortcutChannel(ShortcutRuntime shortcutRuntime) {
        if (shortcutRuntime == null) {
            throw new IllegalArgumentException("ShortcutRuntime cannot be null");
        }
        this.shortcutRuntime = shortcutRuntime;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        return ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "按需查询某个已注册 shortcut 的详细定义。"
                                + "shortcut 字段必须是工具 schema enum 中的精确值；"
                                + "不要发明、翻译、改写单复数、缩写或根据自然语言推断 shortcut 名称。")
                .property("shortcut", ToolSchemaBuilder.string()
                        .description("要查询定义的 shortcut 名称。只能使用 enum 中的精确值。")
                        .enumValues(getShortcutNames()), true)
                .build();
    }

    @Override
    public ToolResult execute(JSONObject params) {
        String shortcutName = params.optString("shortcut", "").trim();
        if (shortcutName.isEmpty()) {
            return ToolResult.error("invalid_args", "describe_shortcut requires a non-empty 'shortcut' field")
                    .with("channel", CHANNEL_NAME);
        }
        ShortcutExecutor executor = shortcutRuntime.find(shortcutName);
        if (executor == null) {
            return ToolResult.error("shortcut_not_supported",
                            "Shortcut '" + shortcutName + "' 未注册。不要发明 shortcut 名称；"
                                    + "请使用匹配 SKILL.md 中明确列出的 shortcut，或 validShortcuts 中的精确值。")
                    .with("channel", CHANNEL_NAME)
                    .with("shortcut", shortcutName)
                    .with("requestedShortcut", shortcutName)
                    .with("failureType", "capability_boundary")
                    .with("suggestedNextAction", "choose_registered_shortcut_from_skill")
                    .withJson("validShortcuts", buildValidShortcutsJson().toString());
        }

        ShortcutDefinition definition = executor.getDefinition();
        return ToolResult.success()
                .with("channel", CHANNEL_NAME)
                .with("shortcut", shortcutName)
                .withJson("definition", buildDefinitionJson(definition).toString());
    }

    private JSONObject buildDefinitionJson(ShortcutDefinition definition) {
        try {
            JSONObject definitionJson = new JSONObject();
            definitionJson.put("name", definition.getName());
            definitionJson.put("title", definition.getTitle());
            definitionJson.put("description", definition.getDescription());
            if (definition.getDomain() != null) {
                definitionJson.put("domain", definition.getDomain());
            }
            if (definition.getRequiredSkill() != null) {
                definitionJson.put("requiredSkill", definition.getRequiredSkill());
            }
            if (definition.getRisk() != null) {
                definitionJson.put("risk", definition.getRisk());
            }
            definitionJson.put("argsSchema", definition.getArgsSchema());
            definitionJson.put("argsExample", definition.getArgsExample());
            if (!definition.getTips().isEmpty()) {
                definitionJson.put("tips", new JSONArray(definition.getTips()));
            }
            return definitionJson;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize shortcut definition: " + definition.getName(), e);
        }
    }

    private String[] getShortcutNames() {
        List<String> names = new ArrayList<>();
        for (ShortcutDefinition definition : shortcutRuntime.listDefinitions()) {
            if (definition != null && definition.getName() != null && !definition.getName().trim().isEmpty()) {
                names.add(definition.getName());
            }
        }
        return names.toArray(new String[0]);
    }

    private JSONArray buildValidShortcutsJson() {
        JSONArray shortcuts = new JSONArray();
        for (ShortcutDefinition definition : shortcutRuntime.listDefinitions()) {
            if (definition != null && definition.getName() != null) {
                shortcuts.put(definition.getName());
            }
        }
        return shortcuts;
    }
}
