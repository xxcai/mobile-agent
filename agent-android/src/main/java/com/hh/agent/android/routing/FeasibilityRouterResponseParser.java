package com.hh.agent.android.routing;

import com.hh.agent.core.tool.ToolExecutor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FeasibilityRouterResponseParser {

    private static final Pattern BOOLEAN_FIELD_PATTERN =
            Pattern.compile("\"business_path_feasible\"\\s*:\\s*(true|false|\"true\"|\"false\")");
    private static final Pattern ENTRY_FUNCTION_PATTERN =
            Pattern.compile("\"entry_function\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern FALLBACK_MODE_PATTERN =
            Pattern.compile("\"fallback_mode\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern REASON_PATTERN =
            Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]*)\"");

    private FeasibilityRouterResponseParser() {
    }

    static BusinessPathFeasibilityDecision parse(String rawResponse,
                                                 Map<String, ToolExecutor> tools) {
        String normalizedJson = extractJsonObject(rawResponse);
        if (normalizedJson == null) {
            return null;
        }

        try {
            boolean businessPathFeasible = readBoolean(normalizedJson);
            String entryFunction = readString(normalizedJson, ENTRY_FUNCTION_PATTERN, "none");
            String fallbackModeRaw = readString(normalizedJson, FALLBACK_MODE_PATTERN, "direct_ui_path");
            String reason = readString(normalizedJson, REASON_PATTERN, "");

            FeasibilityFallbackMode fallbackMode = parseFallbackMode(fallbackModeRaw);
            if (fallbackMode == null) {
                return null;
            }

            if (businessPathFeasible) {
                if (entryFunction.isEmpty() || "none".equals(entryFunction)) {
                    return null;
                }
                if (tools == null || !tools.containsKey(entryFunction)) {
                    return null;
                }
                if (fallbackMode == FeasibilityFallbackMode.DIRECT_UI_PATH) {
                    return null;
                }
                return BusinessPathFeasibilityDecision.businessFirst(
                        entryFunction,
                        fallbackMode,
                        reason.isEmpty() ? "llm_router_selected_business_path" : reason);
            }

            if (!"none".equals(entryFunction) && !entryFunction.isEmpty()) {
                return null;
            }
            if (fallbackMode != FeasibilityFallbackMode.DIRECT_UI_PATH) {
                return null;
            }
            return BusinessPathFeasibilityDecision.directUi(
                    reason.isEmpty() ? "llm_router_selected_direct_ui_path" : reason);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static FeasibilityFallbackMode parseFallbackMode(String raw) {
        for (FeasibilityFallbackMode mode : FeasibilityFallbackMode.values()) {
            if (mode.getWireValue().equals(raw)) {
                return mode;
            }
        }
        return null;
    }

    private static boolean readBoolean(String json) {
        Matcher matcher = BOOLEAN_FIELD_PATTERN.matcher(json);
        if (!matcher.find()) {
            return false;
        }
        String value = matcher.group(1);
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(value.replace("\"", "").trim());
    }

    private static String readString(String json, Pattern pattern, String defaultValue) {
        if (json == null || pattern == null) {
            return defaultValue;
        }
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        String text = matcher.group(1);
        return text != null ? text.trim() : defaultValue;
    }

    private static String extractJsonObject(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        String trimmed = rawResponse.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("```")) {
            int firstBrace = trimmed.indexOf('{');
            int lastBrace = trimmed.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return trimmed.substring(start, end + 1);
    }
}
