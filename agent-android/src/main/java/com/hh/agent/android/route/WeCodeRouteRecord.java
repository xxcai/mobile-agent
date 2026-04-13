package com.hh.agent.android.route;

public final class WeCodeRouteRecord {
    private final String uri;
    private final String title;
    private final String description;

    public WeCodeRouteRecord(String uri, String title, String description) {
        this.uri = requireText(uri, "uri");
        this.title = requireText(title, "title");
        this.description = normalizeText(description);
    }

    public String getUri() {
        return uri;
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
