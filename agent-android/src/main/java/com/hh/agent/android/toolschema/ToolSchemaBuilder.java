package com.hh.agent.android.toolschema;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for OpenAI-compatible function tool schemas used by Android tool channels.
 */
public final class ToolSchemaBuilder {

    private ToolSchemaBuilder() {
    }

    public static FunctionToolBuilder function(String name, String description) {
        return new FunctionToolBuilder(name, description);
    }

    public static ValueSchemaBuilder string() {
        return new ValueSchemaBuilder("string");
    }

    public static ValueSchemaBuilder integer() {
        return new ValueSchemaBuilder("integer");
    }

    public static ValueSchemaBuilder bool() {
        return new ValueSchemaBuilder("boolean");
    }

    public static ObjectSchemaBuilder object() {
        return new ObjectSchemaBuilder();
    }

    public static final class FunctionToolBuilder {
        private final String name;
        private final String description;
        private final ObjectSchemaBuilder parameters = ToolSchemaBuilder.object();

        private FunctionToolBuilder(String name, String description) {
            this.name = requireText(name, "name");
            this.description = requireText(description, "description");
        }

        public FunctionToolBuilder property(String name, SchemaBuilder schema, boolean required) {
            parameters.property(name, schema, required);
            return this;
        }

        public JSONObject build() {
            JSONObject function = new JSONObject();
            put(function, "name", name);
            put(function, "description", description);
            put(function, "parameters", parameters.build());

            JSONObject tool = new JSONObject();
            put(tool, "type", "function");
            put(tool, "function", function);
            return tool;
        }
    }

    public abstract static class SchemaBuilder {
        protected String description;

        public SchemaBuilder description(String description) {
            this.description = requireText(description, "description");
            return this;
        }

        protected void applyDescription(JSONObject target) {
            if (description != null) {
                put(target, "description", description);
            }
        }

        abstract JSONObject build();
    }

    public static final class ValueSchemaBuilder extends SchemaBuilder {
        private final String type;
        private final List<Object> enumValues = new ArrayList<>();
        private Object defaultValue;

        private ValueSchemaBuilder(String type) {
            this.type = requireText(type, "type");
        }

        @Override
        public ValueSchemaBuilder description(String description) {
            super.description(description);
            return this;
        }

        public ValueSchemaBuilder enumValues(String... values) {
            if (values == null) {
                return this;
            }
            for (String value : values) {
                enumValues.add(requireText(value, "enum value"));
            }
            return this;
        }

        public ValueSchemaBuilder defaultValue(String value) {
            this.defaultValue = value;
            return this;
        }

        public ValueSchemaBuilder defaultValue(Number value) {
            this.defaultValue = value;
            return this;
        }

        public ValueSchemaBuilder defaultValue(Boolean value) {
            this.defaultValue = value;
            return this;
        }

        @Override
        JSONObject build() {
            JSONObject schema = new JSONObject();
            put(schema, "type", type);
            applyDescription(schema);
            if (!enumValues.isEmpty()) {
                JSONArray enumArray = new JSONArray();
                for (Object enumValue : enumValues) {
                    enumArray.put(enumValue);
                }
                put(schema, "enum", enumArray);
            }
            if (defaultValue != null) {
                put(schema, "default", defaultValue);
            }
            return schema;
        }
    }

    public static final class ObjectSchemaBuilder extends SchemaBuilder {
        private final LinkedHashMap<String, SchemaEntry> properties = new LinkedHashMap<>();

        private ObjectSchemaBuilder() {
        }

        @Override
        public ObjectSchemaBuilder description(String description) {
            super.description(description);
            return this;
        }

        public ObjectSchemaBuilder property(String name, SchemaBuilder schema, boolean required) {
            String normalizedName = requireText(name, "property name");
            if (schema == null) {
                throw new IllegalArgumentException("schema cannot be null");
            }
            if (properties.containsKey(normalizedName)) {
                throw new IllegalArgumentException("Duplicate property: " + normalizedName);
            }
            properties.put(normalizedName, new SchemaEntry(schema, required));
            return this;
        }

        @Override
        JSONObject build() {
            JSONObject schema = new JSONObject();
            put(schema, "type", "object");
            applyDescription(schema);

            JSONObject propertiesJson = new JSONObject();
            JSONArray requiredJson = new JSONArray();
            for (Map.Entry<String, SchemaEntry> entry : properties.entrySet()) {
                put(propertiesJson, entry.getKey(), entry.getValue().schema.build());
                if (entry.getValue().required) {
                    requiredJson.put(entry.getKey());
                }
            }
            put(schema, "properties", propertiesJson);
            put(schema, "required", requiredJson);
            return schema;
        }
    }

    private static final class SchemaEntry {
        private final SchemaBuilder schema;
        private final boolean required;

        private SchemaEntry(SchemaBuilder schema, boolean required) {
            this.schema = schema;
            this.required = required;
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value.trim();
    }

    private static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to put key '" + key + "' into schema json", exception);
        }
    }
}
