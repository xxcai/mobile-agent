package com.hh.agent.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured tool metadata used to describe a business tool to the model.
 */
public final class ToolDefinition {

    private final String title;
    private final String description;
    private final List<String> intentExamples;
    private final JSONObject argsSchema;
    private final JSONObject argsExample;

    public ToolDefinition(String title,
                          String description,
                          List<String> intentExamples,
                          JSONObject argsSchema,
                          JSONObject argsExample) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title cannot be null or empty");
        }
        if (intentExamples == null || intentExamples.isEmpty()) {
            throw new IllegalArgumentException("intentExamples cannot be null or empty");
        }
        if (argsSchema == null) {
            throw new IllegalArgumentException("argsSchema cannot be null");
        }
        if (argsExample == null) {
            throw new IllegalArgumentException("argsExample cannot be null");
        }

        this.title = title.trim();
        this.description = normalizeNullableText(description);
        this.intentExamples = Collections.unmodifiableList(new ArrayList<>(intentExamples));
        this.argsSchema = copyJson(argsSchema);
        this.argsExample = copyJson(argsExample);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getIntentExamples() {
        return intentExamples;
    }

    public JSONObject getArgsSchema() {
        return copyJson(argsSchema);
    }

    public JSONObject getArgsExample() {
        return copyJson(argsExample);
    }

    private static JSONObject copyJson(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to copy JSONObject", e);
        }
    }

    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
