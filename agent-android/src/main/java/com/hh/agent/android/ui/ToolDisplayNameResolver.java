package com.hh.agent.android.ui;

import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.LegacyAndroidToolChannel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a user-facing tool display name from the top-level tool name and raw arguments.
 */
public final class ToolDisplayNameResolver {

    private static final Pattern FUNCTION_PATTERN =
            Pattern.compile("\"function\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ACTION_PATTERN =
            Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");

    private ToolDisplayNameResolver() {}

    public static String resolve(String toolName, String argumentsJson) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return null;
        }

        String normalizedToolName = toolName.trim();
        if (LegacyAndroidToolChannel.CHANNEL_NAME.equals(normalizedToolName)) {
            return resolveLegacyAndroidTool(argumentsJson);
        }
        if (GestureToolChannel.CHANNEL_NAME.equals(normalizedToolName)) {
            return resolveGestureTool(argumentsJson);
        }
        return normalizedToolName;
    }

    private static String resolveLegacyAndroidTool(String argumentsJson) {
        return extractStringField(argumentsJson, FUNCTION_PATTERN);
    }

    private static String resolveGestureTool(String argumentsJson) {
        String action = extractStringField(argumentsJson, ACTION_PATTERN);
        if (action == null || action.isEmpty()) {
            return null;
        }
        return "gesture_" + action;
    }

    private static String extractStringField(String argumentsJson, Pattern pattern) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = pattern.matcher(argumentsJson);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
