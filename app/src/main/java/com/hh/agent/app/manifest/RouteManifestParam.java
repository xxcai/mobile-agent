package com.hh.agent.app.manifest;

final class RouteManifestParam {
    private final String name;
    private final boolean required;
    private final String description;
    private final RouteManifestEncoding encoding;

    RouteManifestParam(String name, boolean required, String description, RouteManifestEncoding encoding) {
        this.name = requireText(name, "name");
        this.required = required;
        this.description = normalizeText(description);
        this.encoding = encoding;
    }

    String getName() {
        return name;
    }

    boolean isRequired() {
        return required;
    }

    String getDescription() {
        return description;
    }

    RouteManifestEncoding getEncoding() {
        return encoding;
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
