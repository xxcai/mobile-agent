package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
                        "查询某个已注册 shortcut 的详细定义（参数结构、示例和约束）。")
                .property("shortcut", ToolSchemaBuilder.string()
                        .description("要查询定义的 shortcut 名称。"), true)
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
                            "Shortcut '" + shortcutName + "' is not supported. "
                                    + "If this is a skill name, read skills/<skill_name>/SKILL.md with read_file "
                                    + "instead of calling describe_shortcut.")
                    .with("channel", CHANNEL_NAME)
                    .with("shortcut", shortcutName);
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
}
