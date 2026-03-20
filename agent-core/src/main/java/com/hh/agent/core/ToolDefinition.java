package com.hh.agent.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured tool metadata used to describe a business tool to the model.
 */
public final class ToolDefinition {

    private final String summary;
    private final List<String> intentExamples;
    private final JSONObject argsSchema;
    private final JSONObject argsExample;

    public ToolDefinition(String summary,
                          List<String> intentExamples,
                          JSONObject argsSchema,
                          JSONObject argsExample) {
        if (summary == null || summary.trim().isEmpty()) {
            throw new IllegalArgumentException("summary cannot be null or empty");
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

        this.summary = summary;
        this.intentExamples = Collections.unmodifiableList(new ArrayList<>(intentExamples));
        this.argsSchema = copyJson(argsSchema);
        this.argsExample = copyJson(argsExample);
    }

    public String getSummary() {
        return summary;
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
}
