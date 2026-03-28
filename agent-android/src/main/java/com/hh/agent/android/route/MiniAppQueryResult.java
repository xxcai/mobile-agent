package com.hh.agent.android.route;

public final class MiniAppQueryResult {
    private final String uri;
    private final String appName;
    private final String description;

    public MiniAppQueryResult(String uri, String appName, String description) {
        this.uri = requireText(uri, "uri");
        this.appName = requireText(appName, "appName");
        this.description = normalizeText(description);
    }

    public String getUri() {
        return uri;
    }

    public String getAppName() {
        return appName;
    }

    public String getDescription() {
        return description;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
