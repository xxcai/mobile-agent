package com.hh.agent.android.route;

public final class NativeRouteRegistryEntry {
    private final String uri;
    private final String module;
    private final String description;

    public NativeRouteRegistryEntry(String uri, String module) {
        this(uri, module, null);
    }

    public NativeRouteRegistryEntry(String uri, String module, String description) {
        this.uri = requireText(uri, "uri");
        this.module = requireText(module, "module");
        this.description = normalizeText(description);
    }

    public String getUri() {
        return uri;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value.trim();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
