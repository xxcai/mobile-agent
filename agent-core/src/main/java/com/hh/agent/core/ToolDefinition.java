package com.hh.agent.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured tool metadata used to describe a business tool to the model.
 */
public final class ToolDefinition {

    private final String title;
    private final String description;
    private final List<String> intentExamples;
    private final String argsSchemaJson;
    private final String argsExampleJson;

    private ToolDefinition(String title,
                           String description,
                           List<String> intentExamples,
                           String argsSchemaJson,
                           String argsExampleJson) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title cannot be null or empty");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("description cannot be null or empty");
        }
        if (intentExamples == null) {
            throw new IllegalArgumentException("intentExamples cannot be null");
        }
        if (argsSchemaJson == null || argsSchemaJson.trim().isEmpty()) {
            throw new IllegalArgumentException("argsSchemaJson cannot be null or empty");
        }
        if (argsExampleJson == null || argsExampleJson.trim().isEmpty()) {
            throw new IllegalArgumentException("argsExampleJson cannot be null or empty");
        }

        this.title = title.trim();
        this.description = description.trim();
        this.intentExamples = Collections.unmodifiableList(new ArrayList<>(intentExamples));
        this.argsSchemaJson = argsSchemaJson;
        this.argsExampleJson = argsExampleJson;
    }

    public static Builder builder(String title, String description) {
        return new Builder(title, description);
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
        return parseJson(argsSchemaJson);
    }

    public JSONObject getArgsExample() {
        return parseJson(argsExampleJson);
    }

    public String getArgsSchemaJsonString() {
        return argsSchemaJson;
    }

    public String getArgsExampleJsonString() {
        return argsExampleJson;
    }

    private static JSONObject parseJson(String source) {
        try {
            return new JSONObject(source);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to copy JSONObject", e);
        }
    }

    public static final class Builder {

        private final String title;
        private final String description;
        private final List<String> intentExamples = new ArrayList<>();
        private final LinkedHashMap<String, ParamSpec> params = new LinkedHashMap<>();

        private Builder(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public Builder intentExamples(String... examples) {
            if (examples == null) {
                return this;
            }
            for (String example : examples) {
                String normalized = normalizeOptionalText(example);
                if (normalized != null) {
                    intentExamples.add(normalized);
                }
            }
            return this;
        }

        public Builder stringParam(String name, String description, boolean required) {
            return addParam(name, description, "string", required, null);
        }

        public Builder stringParam(String name, String description, boolean required, String example) {
            return addParam(name, description, "string", required, example);
        }

        public Builder intParam(String name, String description, boolean required) {
            return addParam(name, description, "integer", required, null);
        }

        public Builder intParam(String name, String description, boolean required, Number example) {
            return addParam(name, description, "integer", required, example);
        }

        public Builder boolParam(String name, String description, boolean required) {
            return addParam(name, description, "boolean", required, null);
        }

        public Builder boolParam(String name, String description, boolean required, Boolean example) {
            return addParam(name, description, "boolean", required, example);
        }

        public ToolDefinition build() {
            validateText(title, "title");
            validateText(description, "description");

            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            JsonObject argsExample = new JsonObject();

            for (Map.Entry<String, ParamSpec> entry : params.entrySet()) {
                String name = entry.getKey();
                ParamSpec spec = entry.getValue();

                JsonObject property = new JsonObject();
                property.addProperty("type", spec.type);
                property.addProperty("description", spec.description);
                properties.add(name, property);

                if (spec.required) {
                    required.add(name);
                }
                if (spec.example != null) {
                    addExample(argsExample, name, spec.example);
                }
            }

            JsonObject argsSchema = new JsonObject();
            argsSchema.addProperty("type", "object");
            argsSchema.add("properties", properties);
            argsSchema.add("required", required);

            return new ToolDefinition(
                    title,
                    description,
                    intentExamples,
                    argsSchema.toString(),
                    argsExample.toString()
            );
        }

        private Builder addParam(
                String name,
                String description,
                String type,
                boolean required,
                Object example
        ) {
            String normalizedName = validateText(name, "param name");
            String normalizedDescription = validateText(description, "param description");
            if (params.containsKey(normalizedName)) {
                throw new IllegalArgumentException("Duplicate param name: " + normalizedName);
            }
            params.put(normalizedName, new ParamSpec(type, normalizedDescription, required, example));
            return this;
        }

        private static String validateText(String value, String fieldName) {
            String normalized = normalizeOptionalText(value);
            if (normalized == null) {
                throw new IllegalArgumentException(fieldName + " cannot be null or empty");
            }
            return normalized;
        }

        private static String normalizeOptionalText(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private static void addExample(JsonObject argsExample, String name, Object example) {
            if (example instanceof String) {
                argsExample.addProperty(name, (String) example);
            } else if (example instanceof Number) {
                argsExample.addProperty(name, (Number) example);
            } else if (example instanceof Boolean) {
                argsExample.addProperty(name, (Boolean) example);
            } else {
                throw new IllegalArgumentException("Unsupported example type for param '" + name + "'");
            }
        }
    }

    private static final class ParamSpec {
        private final String type;
        private final String description;
        private final boolean required;
        private final Object example;

        private ParamSpec(String type, String description, boolean required, Object example) {
            this.type = type;
            this.description = description;
            this.required = required;
            this.example = example;
        }
    }
}
