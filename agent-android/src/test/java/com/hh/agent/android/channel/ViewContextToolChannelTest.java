package com.hh.agent.android.channel;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ViewContextToolChannelTest {

    private final ViewContextToolChannel channel = new ViewContextToolChannel();

    @Test
    public void buildToolDefinitionAggregatesSourceSpecificProperties() throws Exception {
        JSONObject schema = channel.buildToolDefinition();
        JSONObject properties = schema.getJSONObject("function")
                .getJSONObject("parameters")
                .getJSONObject("properties");

        assertTrue(properties.has("source"));
        assertTrue(properties.has("targetHint"));
        assertTrue(properties.has("includeMockWebDom"));
        assertTrue(properties.has("includeMockScreenshot"));

        JSONObject source = properties.getJSONObject("source");
        assertEquals(4, source.getJSONArray("enum").length());
        assertEquals("native_xml", source.getJSONArray("enum").getString(0));
        assertEquals("web_dom", source.getJSONArray("enum").getString(1));
        assertEquals("screen_snapshot", source.getJSONArray("enum").getString(2));
        assertEquals("all", source.getJSONArray("enum").getString(3));
    }

    @Test
    public void executeRoutesWebDomThroughSourceDefinition() throws Exception {
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("source", "web_dom")
                .put("targetHint", " contact ")).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("android_view_context_tool", result.getString("channel"));
        assertEquals("web_dom", result.getString("source"));
        assertEquals("contact", result.getString("targetHint"));
        assertTrue(result.getString("webDom").contains("mock-root"));
    }

    @Test
    public void executeRoutesScreenSnapshotThroughSourceDefinition() throws Exception {
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("source", "screen_snapshot")).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("screen_snapshot", result.getString("source"));
        assertEquals("mock://screen/current/native-xml-validation",
                result.getString("screenSnapshot"));
        assertFalse(result.has("nativeViewXml") && !result.isNull("nativeViewXml"));
    }

    @Test
    public void executeRejectsUnknownSource() throws Exception {
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("source", "unknown")).toJsonString());

        assertFalse(result.getBoolean("success"));
        assertEquals("invalid_args", result.getString("error"));
        assertTrue(result.getString("allowedSources").contains("native_xml"));
    }
}
