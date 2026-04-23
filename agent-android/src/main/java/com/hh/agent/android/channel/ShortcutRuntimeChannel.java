package com.hh.agent.android.channel;

import com.hh.agent.android.selection.CandidateSelectionStateStore;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.ui.ToolUiDecision;
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
 * Top-level channel that routes stable shortcut names through the shortcut runtime.
 */
public class ShortcutRuntimeChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "run_shortcut";

    private final ShortcutRuntime shortcutRuntime;
    private final CandidateSelectionStateStore selectionStateStore;

    public ShortcutRuntimeChannel(ShortcutRuntime shortcutRuntime) {
        this(shortcutRuntime, null);
    }

    public ShortcutRuntimeChannel(ShortcutRuntime shortcutRuntime,
                                  CandidateSelectionStateStore selectionStateStore) {
        if (shortcutRuntime == null) {
            throw new IllegalArgumentException("ShortcutRuntime cannot be null");
        }
        this.shortcutRuntime = shortcutRuntime;
        this.selectionStateStore = selectionStateStore;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        return ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "运行已注册的 shortcut 原子动作。"
                                + "shortcut 字段必须是工具 schema enum 中的精确值；"
                                + "不要发明、翻译、改写单复数、缩写或根据自然语言推断 shortcut 名称。"
                                + "协议固定为 {\"shortcut\":\"名称\",\"args\":{...}}。")
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
        JSONObject effectiveArgs = copyJson(args);
        String sessionKey = params.optString("_sessionKey", null);
        if (sessionKey != null && !sessionKey.trim().isEmpty()) {
            try {
                effectiveArgs.put("_sessionKey", sessionKey.trim());
            } catch (Exception exception) {
                return ToolResult.error("invalid_args", "Failed to inject session context into shortcut args")
                        .with("channel", CHANNEL_NAME)
                        .with("shortcut", shortcutName);
            }
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

        ToolResult result = shortcutRuntime.execute(shortcutName, effectiveArgs);
        ShortcutDefinition definition = executor.getDefinition();

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
        maybePersistCandidateSelection(params, result);
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
        return "要运行的 shortcut 名称。只能使用 enum 中的精确值；"
                + "不要使用 skill 名、自然语言能力名或近义名称。"
                + "如果上一次结果返回 validShortcuts，必须从其中选择一个完全一致的值。";
    }

    private String buildArgsDescription() {
        return "传给 shortcut 的 JSON 参数对象。字段名必须严格使用匹配 SKILL.md、"
                + "明确引用的 reference 文件或 describe_shortcut 返回定义中的字段；"
                + "不要把 query/name/keyword 等近义字段互相替换。";
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

    private void maybePersistCandidateSelection(JSONObject params, ToolResult result) {
        if (selectionStateStore == null || result == null) {
            return;
        }
        String sessionKey = params.optString("_sessionKey", null);
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(result.toJsonString());
            JSONObject candidateSelection = root.optJSONObject("candidateSelection");
            if (candidateSelection != null && candidateSelection.length() > 0) {
                selectionStateStore.save(sessionKey, candidateSelection);
            }
        } catch (Exception ignored) {
        }
    }

    private JSONObject copyJson(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to copy shortcut args", exception);
        }
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
