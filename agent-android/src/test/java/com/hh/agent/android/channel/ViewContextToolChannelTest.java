package com.hh.agent.android.channel;

import com.hh.agent.android.viewcontext.RuntimeViewContextSourceResolver;
import com.hh.agent.android.viewcontext.ViewContextSourceSelection;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ViewContextToolChannelTest {

    @Test
    public void buildToolDefinitionExposesOnlyTargetHintForLlmInput() throws Exception {
        ViewContextToolChannel channel = new ViewContextToolChannel(fakeResolver("web_dom"));
        JSONObject schema = channel.buildToolDefinition();
        JSONObject properties = schema.getJSONObject("function")
                .getJSONObject("parameters")
                .getJSONObject("properties");

        assertTrue(properties.has("targetHint"));
        assertFalse(properties.has("source"));
        assertFalse(properties.has("includeMockWebDom"));
        assertFalse(properties.has("includeMockScreenshot"));
    }

    @Test
    public void executeUsesRuntimeResolvedSource() throws Exception {
        ViewContextToolChannel channel = new ViewContextToolChannel(fakeResolver("web_dom"));
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("targetHint", " contact ")).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("android_view_context_tool", result.getString("channel"));
        assertEquals("web_dom", result.getString("source"));
        assertEquals("contact", result.getString("targetHint"));
        assertEquals("FALLBACK_RESOLVED", result.getString("selectionStatus"));
        assertTrue(result.getString("webDom").contains("mock-root"));
    }

    @Test
    public void executeIgnoresIncomingSourceAndUsesRuntimeResolvedSource() throws Exception {
        ViewContextToolChannel channel = new ViewContextToolChannel(fakeResolver("web_dom"));
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("source", "native_xml")).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("web_dom", result.getString("source"));
    }

    @Test
    public void executeIncludesMatchedActivityClassNameWhenPolicyWins() throws Exception {
        ViewContextToolChannel channel = new ViewContextToolChannel(new RuntimeViewContextSourceResolver(
                null,
                null,
                null
        ) {
            @Override
            public ViewContextSourceSelection resolve() {
                return ViewContextSourceSelection.policyMatched(
                        "web_dom",
                        "com.hh.agent.BusinessWebActivity"
                );
            }
        });
        JSONObject result = new JSONObject(channel.execute(new JSONObject()).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("POLICY_MATCHED", result.getString("selectionStatus"));
        assertEquals("com.hh.agent.BusinessWebActivity", result.getString("matchedActivityClassName"));
    }

    private RuntimeViewContextSourceResolver fakeResolver(final String source) {
        return new RuntimeViewContextSourceResolver(null, null, null) {
            @Override
            public ViewContextSourceSelection resolve() {
                return ViewContextSourceSelection.fallbackResolved(source);
            }
        };
    }
}
