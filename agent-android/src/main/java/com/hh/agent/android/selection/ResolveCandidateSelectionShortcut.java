package com.hh.agent.android.selection;

import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

public final class ResolveCandidateSelectionShortcut implements ShortcutExecutor {
    private final CandidateSelectionStateStore stateStore;
    private final CandidateSelectionResolver resolver;

    public ResolveCandidateSelectionShortcut(CandidateSelectionStateStore stateStore,
                                             CandidateSelectionResolver resolver) {
        if (stateStore == null) {
            throw new IllegalArgumentException("stateStore cannot be null");
        }
        this.stateStore = stateStore;
        this.resolver = resolver == null ? new CandidateSelectionResolver() : resolver;
    }

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder(
                        "resolve_candidate_selection",
                        "解析候选选择",
                        "根据当前会话最近一次候选列表，把第一个、前者或差异词解析成稳定结果")
                .domain("selection")
                .requiredSkill("agent_shared")
                .argsSchema("{\"type\":\"object\",\"properties\":{\"selectionText\":{\"type\":\"string\",\"description\":\"本轮用户对候选的选择表达，例如 第一个、前者、技术部那个\"},\"domain\":{\"type\":\"string\",\"description\":\"可选，候选域，例如 contact 或 route\"}},\"required\":[\"selectionText\"]}")
                .argsExample("{\"selectionText\":\"第一个\",\"domain\":\"contact\"}")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        String sessionKey = args.optString("_sessionKey", null);
        JSONObject selection = stateStore.get(sessionKey);
        if (selection == null) {
            return ToolResult.error("candidate_selection_not_available",
                            "No candidate selection state is available for the current session")
                    .with("sessionKey", sessionKey);
        }
        String normalizedSelectionText = normalizeSelectionText(args);
        if (normalizedSelectionText == null) {
            return ToolResult.error("invalid_candidate_selection_args",
                            "Use selectionText to describe the user's candidate choice")
                    .with("sessionKey", sessionKey)
                    .withJson("expectedArgs", buildExpectedArgs(args.optString("domain", null)).toString())
                    .withJson("argsExample", buildArgsExample(args.optString("domain", null)).toString());
        }
        JSONObject resolved = resolver.resolve(
                selection,
                normalizedSelectionText,
                args.optString("domain", null));
        if (resolved == null) {
            return ToolResult.error("candidate_selection_not_resolved",
                            "Current input could not be resolved against the latest candidate selection")
                    .with("selectionText", normalizedSelectionText)
                    .withJson("candidateSelection", selection.toString());
        }
        return ToolResult.success()
                .withJson("candidateSelection", selection.toString())
                .withJson("resolvedCandidate", resolved.toString())
                .withJson("payload", resolved.optJSONObject("payload") != null
                        ? resolved.optJSONObject("payload").toString()
                        : new JSONObject().toString());
    }

    private String normalizeSelectionText(JSONObject args) {
        String selectionText = normalizeText(args.optString("selectionText", null));
        if (selectionText != null) {
            return selectionText;
        }
        int ordinal = args.optInt("candidate_index", -1);
        if (ordinal <= 0) {
            ordinal = args.optInt("selection_index", -1);
        }
        if (ordinal <= 0) {
            ordinal = args.optInt("index", -1);
        }
        if (ordinal <= 0) {
            ordinal = args.optInt("ordinal", -1);
        }
        if (ordinal == 1) {
            return "第一个";
        }
        if (ordinal == 2) {
            return "第二个";
        }
        if (ordinal == 3) {
            return "第三个";
        }
        return null;
    }

    private JSONObject buildExpectedArgs(String domain) {
        try {
            return new JSONObject()
                    .put("selectionText", "第一个")
                    .put("domain", normalizeText(domain) == null ? "contact" : normalizeText(domain));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build expectedArgs", exception);
        }
    }

    private JSONObject buildArgsExample(String domain) {
        String normalizedDomain = normalizeText(domain);
        try {
            if ("route".equals(normalizedDomain)) {
                return new JSONObject()
                        .put("selectionText", "login 那个")
                        .put("domain", "route");
            }
            return new JSONObject()
                    .put("selectionText", "技术部那个")
                    .put("domain", "contact");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build argsExample", exception);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
