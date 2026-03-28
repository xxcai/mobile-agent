package com.hh.agent.android.route;

public final class NativeRouteRecord {
    private final String uri;
    private final String module;
    private final String title;
    private final String description;

    public NativeRouteRecord(String uri, String module, String title, String description) {
        this.uri = requireText(uri, "uri");
        this.module = requireText(module, "module");
        this.title = requireText(title, "title");
        this.description = normalizeText(description);
    }

    public String getUri() {
        return uri;
    }

    public String getModule() {
        return module;
    }

    public String getTitle() {
        return title;
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
