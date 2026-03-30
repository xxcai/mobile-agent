package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

import java.util.List;

/**
 * Top-level channel that routes stable shortcut names through the shortcut runtime.
 */
public class ShortcutRuntimeChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "run_shortcut";

    private final ShortcutRuntime shortcutRuntime;

    public ShortcutRuntimeChannel(ShortcutRuntime shortcutRuntime) {
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
                        "运行已注册的 shortcut runtime 原子动作。"
                                + "适用于宿主业务能力编排；由 skill 决定何时选择哪个 shortcut。"
                                + "协议固定为 {\"shortcut\":\"能力名\",\"args\":{...}}。")
                .property("shortcut", ToolSchemaBuilder.string()
                        .description(buildShortcutChoicesDescription())
                        .enumValues(getShortcutNames()), true)
                .property("args", ToolSchemaBuilder.object()
                        .description(buildArgsDescription()), true)
                .build();
    }

    @Override
    public ToolResult execute(JSONObject params) {
        String shortcutName = params.optString("shortcut", "").trim();
        if (shortcutName.isEmpty()) {
            return ToolResult.error("invalid_args", "run_shortcut requires a non-empty 'shortcut' field")
                    .with("channel", CHANNEL_NAME);
        }

        JSONObject args = params.optJSONObject("args");
        if (args == null) {
            return ToolResult.error("invalid_args", "run_shortcut requires an 'args' object")
                    .with("channel", CHANNEL_NAME)
                    .with("shortcut", shortcutName);
        }

        ToolResult result = shortcutRuntime.execute(shortcutName, args);
        ShortcutExecutor executor = shortcutRuntime.find(shortcutName);
        ShortcutDefinition definition = executor != null ? executor.getDefinition() : null;

        if (result == null) {
            return ToolResult.error("execution_failed", "Shortcut returned null result")
                    .with("channel", CHANNEL_NAME)
                    .with("shortcut", shortcutName);
        }

        result.with("channel", CHANNEL_NAME)
                .with("shortcut", shortcutName);
        if (definition != null) {
            if (definition.getDomain() != null) {
                result.with("domain", definition.getDomain());
            }
            if (definition.getRequiredSkill() != null) {
                result.with("requiredSkill", definition.getRequiredSkill());
            }
        }
        return result;
    }

    private String[] getShortcutNames() {
        List<ShortcutDefinition> definitions = shortcutRuntime.listDefinitions();
        String[] names = new String[definitions.size()];
        for (int index = 0; index < definitions.size(); index++) {
            names[index] = definitions.get(index).getName();
        }
        return names;
    }

    private String buildShortcutChoicesDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("要运行的 shortcut 名称，只能从 enum 列表中选择。")
                .append("优先依据 skill 指导选择，不要自行猜测业务路径：\n");

        for (ShortcutDefinition definition : shortcutRuntime.listDefinitions()) {
            builder.append("- ").append(definition.getName())
                    .append(": ").append(definition.getDescription());
            if (definition.getRequiredSkill() != null) {
                builder.append("；requiredSkill=").append(definition.getRequiredSkill());
            }
            if (definition.getDomain() != null) {
                builder.append("；domain=").append(definition.getDomain());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String buildArgsDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("传给 shortcut 的 JSON 参数对象。args 的字段结构由所选 shortcut 决定。")
                .append("最小可用样例如下：\n");

        for (ShortcutDefinition definition : shortcutRuntime.listDefinitions()) {
            builder.append("- ").append(definition.getName())
                    .append(": schema=")
                    .append(definition.getArgsSchema())
                    .append("；example=")
                    .append(definition.getArgsExample())
                    .append('\n');
        }
        return builder.toString().trim();
    }
}
