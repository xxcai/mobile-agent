package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.ui.ToolUiDecision;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONException;
import org.json.JSONObject;

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
                                + "如果缺少某个 shortcut 的详细定义或参数结构，应先调用 describe_shortcut 按需查询，不要自行猜测业务路径。"
                                + "协议固定为 {\"shortcut\":\"能力名\",\"args\":{...}}。")
                .property("shortcut", ToolSchemaBuilder.string()
                        .description(buildShortcutChoicesDescription()), true)
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
                result.withJson("governance", buildGovernanceJson(definition).toString());
            }
        }
        return result;
    }

    @Override
    public boolean shouldExposeInnerToolInToolUi() {
        return true;
    }

    @Override
    public ToolUiDecision resolveInnerToolUiDecision(String argumentsJson) {
        String shortcutName = extractShortcutName(argumentsJson);
        if (shortcutName == null || shortcutName.isEmpty()) {
            return ToolUiDecision.hidden();
        }
        ShortcutExecutor executor = shortcutRuntime.find(shortcutName);
        if (executor == null) {
            return ToolUiDecision.hidden();
        }
        ShortcutDefinition definition = executor.getDefinition();
        if (definition == null) {
            return ToolUiDecision.hidden();
        }
        return ToolUiDecision.visible(definition.getTitle(), definition.getDescription());
    }

    private JSONObject buildGovernanceJson(ShortcutDefinition definition) {
        try {
            return new JSONObject()
                    .put("mode", "advisory")
                    .put("requiredSkill", definition.getRequiredSkill())
                    .put("message", "This shortcut should normally be selected via the "
                            + definition.getRequiredSkill() + " skill.");
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize governance metadata", e);
        }
    }

    private String buildShortcutChoicesDescription() {
        return "要运行的 shortcut 名称。当前顶层 schema 不再默认暴露 shortcut 列表或详细定义。"
                + "应优先依据 skill 选择；如果缺少定义或参数结构，应先调用 describe_shortcut 按需查询，不要自行猜测业务路径。";
    }

    private String buildArgsDescription() {
        return "传给 shortcut 的 JSON 参数对象。args 的字段结构由目标 shortcut 定义决定。"
                + "顶层 schema 不再默认展开所有 shortcut 的参数 schema/example；需要时应先调用 describe_shortcut 按需查询。";
    }

    private String extractShortcutName(String argumentsJson) {
        String normalized = normalizeArgumentsJson(argumentsJson);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        try {
            JSONObject payload = new JSONObject(normalized);
            String shortcutName = payload.optString("shortcut", "").trim();
            return shortcutName.isEmpty() ? null : shortcutName;
        } catch (JSONException e) {
            return null;
        }
    }

    private String normalizeArgumentsJson(String argumentsJson) {
        if (argumentsJson == null) {
            return null;
        }
        String normalized = argumentsJson.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() >= 2
                && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return normalized;
    }
}
