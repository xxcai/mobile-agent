package com.hh.agent.android.debug;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TaskRunIds {

    private static final String RUN_ID_TIME_PATTERN = "yyyyMMdd-HHmmss";
    private static final int DISPLAY_NAME_MAX_CODE_POINTS = 10;

    private TaskRunIds() {
    }

    public static String createSafeRunId(String displayName) {
        String timestamp = new SimpleDateFormat(RUN_ID_TIME_PATTERN, Locale.US).format(new Date());
        return timestamp + "-" + shortHash(displayName);
    }

    public static String sanitizeRunId(String runId) {
        String normalized = runId == null ? "" : runId.trim();
        normalized = normalized.replaceAll("[^A-Za-z0-9_-]", "_");
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        normalized = normalized.replaceAll("^[_-]+|[_-]+$", "");
        return normalized.isEmpty() ? createSafeRunId("") : normalized;
    }

    public static String buildDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        int endIndex = normalized.offsetByCodePoints(
                0,
                Math.min(normalized.codePointCount(0, normalized.length()), DISPLAY_NAME_MAX_CODE_POINTS));
        return normalized.substring(0, endIndex).trim();
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest((value == null ? "" : value.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 4; index++) {
                builder.append(String.format(Locale.US, "%02x", bytes[index] & 0xff));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "00000000";
        }
    }
}
