package com.hh.agent.core.shortcut;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured shortcut metadata used by the shortcut runtime channel.
 */
public final class ShortcutDefinition {

    private final String name;
    private final String title;
    private final String description;
    private final String risk;
    private final String domain;
    private final String requiredSkill;
    private final List<String> tips;
    private final String argsSchemaJson;
    private final String argsExampleJson;

    private ShortcutDefinition(String name,
                               String title,
                               String description,
                               String risk,
                               String domain,
                               String requiredSkill,
                               List<String> tips,
                               String argsSchemaJson,
                               String argsExampleJson) {
        this.name = requireText(name, "name");
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");
        this.risk = normalizeOptionalText(risk);
        this.domain = normalizeOptionalText(domain);
        this.requiredSkill = normalizeOptionalText(requiredSkill);
        this.tips = Collections.unmodifiableList(new ArrayList<>(tips));
        this.argsSchemaJson = requireText(argsSchemaJson, "argsSchemaJson");
        this.argsExampleJson = requireText(argsExampleJson, "argsExampleJson");
    }

    public static Builder builder(String name, String title, String description) {
        return new Builder(name, title, description);
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRisk() {
        return risk;
    }

    public String getDomain() {
        return domain;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public List<String> getTips() {
        return tips;
    }

    public JSONObject getArgsSchema() {
        return parseJson(argsSchemaJson, "argsSchemaJson");
    }

    public JSONObject getArgsExample() {
        return parseJson(argsExampleJson, "argsExampleJson");
    }

    public String getArgsSchemaJsonString() {
        return argsSchemaJson;
    }

    public String getArgsExampleJsonString() {
        return argsExampleJson;
    }

    public static final class Builder {
        private final String name;
        private final String title;
        private final String description;
        private final List<String> tips = new ArrayList<>();
        private final LinkedHashMap<String, ParamSpec> params = new LinkedHashMap<>();
        private String risk;
        private String domain;
        private String requiredSkill;

        private Builder(String name, String title, String description) {
            this.name = name;
            this.title = title;
            this.description = description;
        }

        public Builder risk(String risk) {
            this.risk = normalizeOptionalText(risk);
            return this;
        }

        public Builder domain(String domain) {
            this.domain = normalizeOptionalText(domain);
            return this;
        }

        public Builder requiredSkill(String requiredSkill) {
            this.requiredSkill = normalizeOptionalText(requiredSkill);
            return this;
        }

        public Builder tips(String... values) {
            if (values == null) {
                return this;
            }
            for (String value : values) {
                String normalized = normalizeOptionalText(value);
                if (normalized != null) {
                    tips.add(normalized);
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

        public ShortcutDefinition build() {
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            JsonObject argsExample = new JsonObject();

            for (Map.Entry<String, ParamSpec> entry : params.entrySet()) {
                String paramName = entry.getKey();
                ParamSpec spec = entry.getValue();

                JsonObject property = new JsonObject();
                property.addProperty("type", spec.type);
                property.addProperty("description", spec.description);
                properties.add(paramName, property);

                if (spec.required) {
                    required.add(paramName);
                }
                if (spec.example != null) {
                    addExample(argsExample, paramName, spec.example);
                }
            }

            JsonObject argsSchema = new JsonObject();
            argsSchema.addProperty("type", "object");
            argsSchema.add("properties", properties);
            argsSchema.add("required", required);

            return new ShortcutDefinition(
                    name,
                    title,
                    description,
                    risk,
                    domain,
                    requiredSkill,
                    tips,
                    argsSchema.toString(),
                    argsExample.toString()
            );
        }

        private Builder addParam(String name,
                                 String description,
                                 String type,
                                 boolean required,
                                 Object example) {
            String normalizedName = requireText(name, "param name");
            String normalizedDescription = requireText(description, "param description");
            if (params.containsKey(normalizedName)) {
                throw new IllegalArgumentException("Duplicate param name: " + normalizedName);
            }
            params.put(normalizedName, new ParamSpec(type, normalizedDescription, required, example));
            return this;
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

    private static String requireText(String value, String fieldName) {
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

    private static JSONObject parseJson(String json, String fieldName) {
        try {
            return new JSONObject(json);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to parse " + fieldName, exception);
        }
    }
}
