package com.hh.agent.android.routing;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;

import org.json.JSONObject;

import java.util.Map;

final class FeasibilityRouterPromptFactory {

    private FeasibilityRouterPromptFactory() {
    }

    static String buildSystemPrompt(Map<String, ToolExecutor> tools) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a feasibility router for Android agent business tools.\n");
        builder.append("Decide whether the user request is worth trying through a business tool before any UI path.\n");
        builder.append("Return JSON only. Do not include markdown fences or extra text.\n");
        builder.append("JSON schema:\n");
        builder.append("{\"business_path_feasible\":true|false,");
        builder.append("\"entry_function\":\"tool_name|none\",");
        builder.append("\"fallback_mode\":\"no_ui_fallback|ui_fallback_on_structured_failure|direct_ui_path\",");
        builder.append("\"reason\":\"short_reason\"}\n");
        builder.append("Rules:\n");
        builder.append("1. If the goal depends on visible UI elements, positions, buttons, cards, or current screen structure, set business_path_feasible=false and fallback_mode=direct_ui_path.\n");
        builder.append("2. If the goal can be stably completed by a business path, set business_path_feasible=true and choose entry_function as the first business function to call on that path.\n");
        builder.append("3. For ambiguous targets like current chat, this page, here, current conversation, do not assume a stable business entity unless the tool summary clearly supports it.\n");
        builder.append("4. fallback_mode describes whether UI fallback is allowed after a business-path attempt fails, not whether fallback is usually needed.\n");
        builder.append("5. If a business path is worth trying first and UI interaction would still be a valid recovery after structured failure, use ui_fallback_on_structured_failure even if the business path usually succeeds.\n");
        builder.append("6. Use no_ui_fallback only when a business-path failure should be returned directly and should not switch to UI interaction.\n");
        builder.append("7. For send-message style goals that may need search_contacts before send_im_message, prefer ui_fallback_on_structured_failure because UI recovery is still allowed if the business path cannot access the final target.\n");
        builder.append("8. If no business tool is a clear fit, set entry_function=none and fallback_mode=direct_ui_path.\n");
        builder.append("9. entry_function means the first business function to call, not necessarily the final goal function. Example: to send a message to Zhang San, entry_function can be search_contacts if searching is needed before send_im_message.\n");
        builder.append("Available business tools:\n");
        builder.append(buildToolsSummary(tools));
        return builder.toString();
    }

    private static String buildToolsSummary(Map<String, ToolExecutor> tools) {
        if (tools == null || tools.isEmpty()) {
            return "- none\n";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, ToolExecutor> entry : tools.entrySet()) {
            ToolExecutor executor = entry.getValue();
            if (executor == null || executor.getDefinition() == null) {
                continue;
            }
            ToolDefinition definition = executor.getDefinition();
            builder.append("- name: ").append(entry.getKey()).append('\n');
            builder.append("  title: ").append(definition.getTitle()).append('\n');
            builder.append("  description: ").append(definition.getDescription()).append('\n');
            builder.append("  intent_examples: ").append(definition.getIntentExamples()).append('\n');
            builder.append("  required_args: ").append(extractRequiredArgs(definition)).append('\n');
        }
        return builder.toString();
    }

    private static String extractRequiredArgs(ToolDefinition definition) {
        try {
            JSONObject argsSchema = definition.getArgsSchema();
            return argsSchema.optJSONArray("required") != null
                    ? argsSchema.optJSONArray("required").toString()
                    : "[]";
        } catch (Exception ignored) {
            return "[]";
        }
    }
}
