package com.hh.agent.android.channel;

import com.hh.agent.android.viewcontext.RuntimeViewContextSourceResolver;
import com.hh.agent.android.viewcontext.WebDomSnapshotProvider;
import com.hh.agent.android.viewcontext.ViewContextSourceSelection;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ViewContextToolChannelTest {

    @Test
    public void buildToolDefinitionExposesOnlyTargetHintForLlmInput() throws Exception {
        ViewContextToolChannel channel = channelWithMockWebDom(fakeResolver("web_dom"));
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
        ViewContextToolChannel channel = channelWithMockWebDom(fakeResolver("web_dom"));
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("targetHint", " contact ")).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("android_view_context_tool", result.getString("channel"));
        assertEquals("web_dom", result.getString("source"));
        assertEquals("web", result.getString("interactionDomain"));
        assertEquals("contact", result.getString("targetHint"));
        assertEquals("FALLBACK_RESOLVED", result.getString("selectionStatus"));
        assertEquals("mock_webview_dom", result.getString("observationMode"));
        assertEquals("html", result.getString("webDomFormat"));
        assertTrue(result.getString("webDom").contains("mock-root"));
    }

    @Test
    public void executeIgnoresIncomingSourceAndUsesRuntimeResolvedSource() throws Exception {
        ViewContextToolChannel channel = channelWithMockWebDom(fakeResolver("web_dom"));
        JSONObject result = new JSONObject(channel.execute(new JSONObject()
                .put("source", "native_xml")).toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("web_dom", result.getString("source"));
    }

    @Test
    public void executeIncludesMatchedActivityClassNameWhenPolicyWins() throws Exception {
        ViewContextToolChannel channel = channelWithMockWebDom(new RuntimeViewContextSourceResolver(
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

    private ViewContextToolChannel channelWithMockWebDom(RuntimeViewContextSourceResolver resolver) {
        Map<String, ViewContextSourceHandler> handlers = new LinkedHashMap<>();
        handlers.put(ViewContextToolChannel.SOURCE_NATIVE_XML, new NativeXmlViewContextSourceHandler());
        handlers.put(ViewContextToolChannel.SOURCE_WEB_DOM, new WebDomViewContextSourceHandler(new WebDomSnapshotProvider() {
            @Override
            public ToolResult getCurrentWebDomSnapshot(String targetHint) {
                return ToolResult.success()
                        .with("channel", ViewContextToolChannel.CHANNEL_NAME)
                        .with("source", ViewContextToolChannel.SOURCE_WEB_DOM)
                        .with("interactionDomain", "web")
                        .with("mock", true)
                        .with("targetHint", targetHint)
                        .with("observationMode", "mock_webview_dom")
                        .with("snapshotId", "obs_web_mock")
                        .with("snapshotCreatedAtEpochMs", 1L)
                        .with("snapshotScope", "current_turn")
                        .with("snapshotCurrentTurnOnly", true)
                        .with("nativeViewXml", (String) null)
                        .with("webDom", "<html><body><div id=\"mock-root\"></div></body></html>")
                        .with("webDomFormat", "html")
                        .with("screenSnapshot", (String) null);
            }
        }));
        return new ViewContextToolChannel(resolver, handlers);
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
