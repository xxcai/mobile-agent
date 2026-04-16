package com.hh.agent.android.harness;

import android.text.TextUtils;

import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.android.log.AgentLogs;

public class OrchestratorHarness {
    private static final String TAG = "OrchestratorHarness";

    private final Map<String, ShortcutExecutor> shortcutRegistry;
    private final Resolver resolver;
    private final ResultStore resultStore;
    private AndroidToolManager toolManager;

    private String currentSkill;
    private JSONObject currentContext;

    private OrchestratorHarness() {
        this.shortcutRegistry = new HashMap<>();
        this.resolver = new Resolver();
        this.resultStore = new ResultStore();
        this.currentSkill = null;
        this.currentContext = new JSONObject();
    }

    public static OrchestratorHarness create() {
        return new OrchestratorHarness();
    }

    public OrchestratorHarness registerShortcut(String name, ShortcutExecutor executor) {
        shortcutRegistry.put(name, executor);
        return this;
    }

    public OrchestratorHarness loadShortcuts(Map<String, ShortcutExecutor> shortcuts) {
        shortcutRegistry.putAll(shortcuts);
        return this;
    }

    public OrchestratorHarness setToolManager(AndroidToolManager toolManager) {
        this.toolManager = toolManager;
        return this;
    }

    public HarnessResult execute(String skillName, JSONObject args) {
        HarnessResult result = new HarnessResult();

        try {
            if (TextUtils.isEmpty(skillName)) {
                return result.fail("skill_name_required", "Skill name is required");
            }

            SkillContext skillContext = resolver.resolve(skillName);
            if (skillContext == null) {
                return result.fail("skill_not_found", "Skill not found: " + skillName);
            }

            if (!skillContext.isValid()) {
                AgentLogs.error(TAG, "skill_validation_failed",
                    new JSONObject().put("skill", skillName).put("errors", skillContext.getValidationErrors()).toString());
                return result.fail("skill_validation_failed", "Skill validation failed: " + skillContext.getValidationErrors());
            }

            currentSkill = skillName;

            if (skillContext.isDeterministic()) {
                AgentLogs.info(TAG, "execution_mode_selected",
                    new JSONObject().put("skill", skillName).put("mode", "planned_fast_execute").put("executor", "native").toString());
                return validateAndDelegate(skillContext, args);
            } else {
                AgentLogs.info(TAG, "execution_mode_selected",
                    new JSONObject().put("skill", skillName).put("mode", "shortcut").toString());
                return executeWithShortcut(skillContext, args);
            }

        } catch (Exception e) {
            AgentLogs.error(TAG, "execution_exception", "skill=" + skillName + " message=" + e.getMessage(), e);
            return result.fail("execution_exception", e.getMessage());
        }
    }

    private HarnessResult validateAndDelegate(SkillContext skillContext, JSONObject args) throws JSONException {
        List<ExecutionHint> hints = skillContext.getExecutionHints();
        if (hints == null || hints.isEmpty()) {
            AgentLogs.warn(TAG, "no_execution_hints", "skill=" + skillContext.getName());
            return new HarnessResult().fail("no_execution_hints", "No execution hints for deterministic skill");
        }

        AgentLogs.info(TAG, "skill_validated_for_native",
            new JSONObject()
                .put("skill", skillContext.getName())
                .put("steps", hints.size())
                .put("mode", "planned_fast_execute")
                .put("executor", "native_agent_loop")
                .toString());

        resultStore.recordSkillValidation(skillContext.getName(), hints.size(), args);

        return new HarnessResult().success(ToolResult.success()
            .with("skill", skillContext.getName())
            .with("mode", "planned_fast_execute")
            .with("executor", "native")
            .with("validated", true));
    }

    private HarnessResult executeWithShortcut(SkillContext skillContext, JSONObject args) {
        HarnessResult result = new HarnessResult();

        JSONObject enrichedArgs = enrichArgs(args, skillContext);

        GateDecision gate = checkGates(skillContext, enrichedArgs);
        if (!gate.canProceed) {
            return result.fail(gate.errorCode, gate.errorMessage);
        }

        String shortcutName = skillContext.getPrimaryShortcut();
        if (TextUtils.isEmpty(shortcutName)) {
            return result.fail("no_shortcut", "No shortcut defined for skill: " + skillContext.getName());
        }

        ShortcutExecutor executor = shortcutRegistry.get(shortcutName);
        if (executor == null) {
            return result.fail("shortcut_not_found", "Shortcut not found: " + shortcutName);
        }

        ToolResult toolResult = executor.execute(enrichedArgs);

        resultStore.record(skillContext.getName(), shortcutName, enrichedArgs, toolResult);

        String resultJson = toolResult.toJsonString();
        if (resultJson.contains("\"success\":true")) {
            return result.success(toolResult);
        } else {
            return result.fail("execution_failed", resultJson);
        }
    }

    private JSONObject enrichArgs(JSONObject args, SkillContext skillContext) {
        JSONObject enriched = new JSONObject();

        try {
            if (args != null) {
                java.util.Iterator<String> keys = args.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    enriched.put(key, args.get(key));
                }
            }

            if (currentContext != null && currentContext.length() > 0) {
                java.util.Iterator<String> contextKeys = currentContext.keys();
                while (contextKeys.hasNext()) {
                    String key = contextKeys.next();
                    if (!enriched.has(key)) {
                        enriched.put(key, currentContext.get(key));
                    }
                }
            }
        } catch (JSONException ignored) {
        }

        return enriched;
    }

    private GateDecision checkGates(SkillContext skillContext, JSONObject args) {
        GateDecision decision = new GateDecision();
        decision.canProceed = true;

        String[] requiredParams = skillContext.getRequiredParams();
        if (requiredParams != null && requiredParams.length > 0) {
            for (String param : requiredParams) {
                if (!args.has(param) || TextUtils.isEmpty(args.optString(param))) {
                    decision.canProceed = false;
                    decision.errorCode = "missing_required_param";
                    decision.errorMessage = "Missing required parameter: " + param;
                    return decision;
                }
            }
        }

        String[] dependencies = skillContext.getDependencies();
        if (dependencies != null && dependencies.length > 0) {
            for (String dep : dependencies) {
                if (!resultStore.hasSuccessfulExecution(dep)) {
                    decision.canProceed = false;
                    decision.errorCode = "dependency_not_satisfied";
                    decision.errorMessage = "Dependency skill not executed: " + dep;
                    return decision;
                }
            }
        }

        return decision;
    }

    public OrchestratorHarness setContext(String key, Object value) {
        try {
            currentContext.put(key, value);
        } catch (JSONException ignored) {
        }
        return this;
    }

    public OrchestratorHarness clearContext() {
        currentContext = new JSONObject();
        return this;
    }

    public ResultStore getResultStore() {
        return resultStore;
    }

    public static class HarnessResult {
        private boolean success;
        private String errorCode;
        private String errorMessage;
        private ToolResult toolResult;

        public HarnessResult success(ToolResult toolResult) {
            this.success = true;
            this.toolResult = toolResult;
            return this;
        }

        public HarnessResult fail(String errorCode, String errorMessage) {
            this.success = false;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            return this;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public ToolResult getToolResult() {
            return toolResult;
        }
    }

    private static class GateDecision {
        boolean canProceed;
        String errorCode;
        String errorMessage;
    }
}
