package com.hh.agent.android.route.manifest;

public final class RouteManifestParam {
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

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDescription() {
        return description;
    }

    public RouteManifestEncoding getEncoding() {
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
