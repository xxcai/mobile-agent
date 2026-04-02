package com.hh.agent.android.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NativeRouteRegistryEntry {
    private final String uri;
    private final String module;
    private final String description;
    private final List<String> keywords;

    public NativeRouteRegistryEntry(String uri, String module) {
        this(uri, module, null, Collections.emptyList());
    }

    public NativeRouteRegistryEntry(String uri, String module, String description) {
        this(uri, module, description, Collections.emptyList());
    }

    public NativeRouteRegistryEntry(String uri, String module, String description, List<String> keywords) {
        this.uri = requireText(uri, "uri");
        this.module = requireText(module, "module");
        this.description = normalizeText(description);
        this.keywords = Collections.unmodifiableList(new ArrayList<>(
                keywords == null ? Collections.emptyList() : keywords));
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

    public List<String> getKeywords() {
        return keywords;
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
