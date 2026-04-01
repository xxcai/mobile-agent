package com.hh.agent.app.manifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RouteManifestRoute {
    private final String path;
    private final String description;
    private final List<RouteManifestParam> params;

    RouteManifestRoute(String path, String description, List<RouteManifestParam> params) {
        this.path = requireText(path, "path");
        this.description = normalizeText(description);
        this.params = Collections.unmodifiableList(new ArrayList<>(params == null ? Collections.emptyList() : params));
    }

    String getPath() {
        return path;
    }

    String getDescription() {
        return description;
    }

    List<RouteManifestParam> getParams() {
        return params;
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
