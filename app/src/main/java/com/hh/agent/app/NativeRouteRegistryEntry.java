package com.hh.agent.app;

final class NativeRouteRegistryEntry {
    private final String uri;
    private final String module;
    private final String description;

    NativeRouteRegistryEntry(String uri, String module) {
        this(uri, module, null);
    }

    NativeRouteRegistryEntry(String uri, String module, String description) {
        this.uri = requireText(uri, "uri");
        this.module = requireText(module, "module");
        this.description = normalizeText(description);
    }

    String getUri() {
        return uri;
    }

    String getModule() {
        return module;
    }

    String getDescription() {
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
