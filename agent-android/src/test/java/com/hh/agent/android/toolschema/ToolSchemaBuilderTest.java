package com.hh.agent.android.toolschema;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolSchemaBuilderTest {

    @Test
    public void buildsFunctionToolSchemaWithNestedObjectAndMetadata() throws Exception {
        JSONObject schema = ToolSchemaBuilder.function(
                        "android_gesture_tool",
                        "Execute gestures inside the current host activity.")
                .property("action", ToolSchemaBuilder.string()
                        .description("Gesture action type.")
                        .enumValues("tap", "swipe"), true)
                .property("amount", ToolSchemaBuilder.string()
                        .description("Swipe amount.")
                        .enumValues("small", "medium", "large")
                        .defaultValue("medium"), false)
                .property("observation", ToolSchemaBuilder.object()
                        .description("Observation reference.")
                        .property("snapshotId", ToolSchemaBuilder.string()
                                .description("Snapshot id."), true)
                        .property("referencedBounds", ToolSchemaBuilder.string()
                                .description("Observed bounds."), false), false)
                .build();

        assertEquals("function", schema.getString("type"));

        JSONObject function = schema.getJSONObject("function");
        assertEquals("android_gesture_tool", function.getString("name"));
        assertEquals("Execute gestures inside the current host activity.",
                function.getString("description"));

        JSONObject parameters = function.getJSONObject("parameters");
        assertEquals("object", parameters.getString("type"));

        JSONObject properties = parameters.getJSONObject("properties");
        JSONObject action = properties.getJSONObject("action");
        assertEquals("string", action.getString("type"));
        assertEquals("Gesture action type.", action.getString("description"));
        assertEquals(2, action.getJSONArray("enum").length());

        JSONObject amount = properties.getJSONObject("amount");
        assertEquals("medium", amount.getString("default"));

        JSONObject observation = properties.getJSONObject("observation");
        assertEquals("object", observation.getString("type"));
        assertEquals("Observation reference.", observation.getString("description"));

        JSONObject observationProperties = observation.getJSONObject("properties");
        assertTrue(observationProperties.has("snapshotId"));
        assertTrue(observationProperties.has("referencedBounds"));

        JSONArray parameterRequired = parameters.getJSONArray("required");
        assertEquals(1, parameterRequired.length());
        assertEquals("action", parameterRequired.getString(0));

        JSONArray observationRequired = observation.getJSONArray("required");
        assertEquals(1, observationRequired.length());
        assertEquals("snapshotId", observationRequired.getString(0));
    }

    @Test
    public void emptyRequiredArrayIsStillPresentForObjects() throws Exception {
        JSONObject objectSchema = ToolSchemaBuilder.object()
                .property("source", ToolSchemaBuilder.string().description("Source"), false)
                .build();

        assertTrue(objectSchema.has("required"));
        assertFalse(objectSchema.getJSONArray("required").length() > 0);
    }
}
