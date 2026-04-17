package com.hh.agent.android.viewcontext;

import android.app.Activity;

import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.web.WebViewJsBridge;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RealWebDomSnapshotProviderTest {

    @Before
    public void setUp() {
        AgentLogs.setLogger(new NoOpAgentLogger());
    }

    @After
    public void tearDown() {
        AgentLogs.resetLogger();
    }

    @Test
    public void getCurrentWebDomSnapshot_usesPayloadMetadataWithoutTouchingNullWebView() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.rawEvalResult = "{\"pageUrl\":\"https://example.test/form\",\"pageTitle\":\"Form Local Page\",\"tree\":{\"tag\":\"body\"}}";

        RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
        JSONObject json = new JSONObject(provider.getCurrentWebDomSnapshot("debug submit button").toJsonString());

        assertTrue(json.getBoolean("success"));
        assertEquals("json_web_dom", json.getString("observationMode"));
        assertEquals("https://example.test/form", json.getString("pageUrl"));
        assertEquals("Form Local Page", json.getString("pageTitle"));
        assertTrue(json.getString("snapshotId").startsWith("obs_"));
        assertEquals("web_dom", json.getJSONObject("uiTree").getString("source"));
        assertTrue(json.getString("pageSummary").contains("Web page"));
        assertEquals("WebDomObservationAdapter", json.getJSONObject("quality").getString("adapterName"));
        assertTrue(json.getJSONObject("raw").has("webDom"));

        ViewObservationSnapshot latestSnapshot = ViewObservationSnapshotRegistry.getLatestSnapshot();
        assertNotNull(latestSnapshot);
        assertEquals("web_dom", new JSONObject(latestSnapshot.uiTreeJson).getString("source"));
        assertTrue(latestSnapshot.pageSummary.contains("Web page"));
        assertEquals("WebDomObservationAdapter", new JSONObject(latestSnapshot.qualityJson).getString("adapterName"));
        assertTrue(new JSONObject(latestSnapshot.rawJson).has("webDom"));
    }

    @Test
    public void getCurrentWebDomSnapshot_reportsRequireWebViewFailureStage() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.requireException = new IllegalStateException("webview_not_found");

        RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
        JSONObject json = new JSONObject(provider.getCurrentWebDomSnapshot("debug submit button").toJsonString());

        assertFalse(json.getBoolean("success"));
        assertEquals("dom_capture_failed", json.getString("error"));
        assertEquals("require_webview", json.getString("failureStage"));
        assertEquals("webview_not_found", json.getString("message"));
    }

    @Test
    public void getCurrentWebDomSnapshot_reportsParsePayloadFailureStage() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.rawEvalResult = "\"not-json\"";

        RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
        JSONObject json = new JSONObject(provider.getCurrentWebDomSnapshot("debug submit button").toJsonString());

        assertFalse(json.getBoolean("success"));
        assertEquals("dom_capture_failed", json.getString("error"));
        assertEquals("parse_payload", json.getString("failureStage"));
        assertEquals("not-json", json.getString("decodedJsResult"));
    }

    @Test
    public void getCurrentWebDomSnapshot_reportsEvaluateFailureStage() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.evaluateException = new IllegalStateException("evaluate_failed");

        RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
        JSONObject json = new JSONObject(provider.getCurrentWebDomSnapshot("debug submit button").toJsonString());

        assertFalse(json.getBoolean("success"));
        assertEquals("dom_capture_failed", json.getString("error"));
        assertEquals("evaluate_javascript", json.getString("failureStage"));
        assertEquals("evaluate_failed", json.getString("message"));
    }

    @Test
    public void getCurrentWebDomSnapshot_usesBridgeFallbackAccessorsForPageMetadata() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.rawEvalResult = "{\"tree\":{\"tag\":\"body\"}}";
        bridge.fallbackPageUrl = "file:///android_asset/";
        bridge.fallbackPageTitle = "Form Local Page";

        RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
        JSONObject json = new JSONObject(provider.getCurrentWebDomSnapshot("debug submit button").toJsonString());

        assertTrue(json.getBoolean("success"));
        assertTrue(bridge.pageUrlRequested);
        assertTrue(bridge.pageTitleRequested);
        assertEquals("file:///android_asset/", json.getString("pageUrl"));
        assertEquals("Form Local Page", json.getString("pageTitle"));
    }

    @Test
    public void getCurrentWebDomSnapshot_outputMatchesWebDomAdapterExpectations() throws Exception {
        // RED phase: validate provider output structure matches what WebDomObservationAdapter expects
        FakeBridge bridge = new FakeBridge();
        bridge.rawEvalResult = "{\"pageUrl\":\"https://example.test/form\",\"pageTitle\":\"Test Form\",\"nodeCount\":3,\"maxDepthReached\":2,\"truncated\":false,\"tree\":{\"ref\":\"node-0\",\"tag\":\"body\",\"text\":\"\",\"bounds\":{\"x\":0,\"y\":0,\"width\":1080,\"height\":1920},\"children\":[{\"ref\":\"node-1\",\"tag\":\"button\",\"selector\":\"button#submit\",\"text\":\"Submit\",\"ariaLabel\":\"Submit button\",\"clickable\":true,\"inputable\":false,\"bounds\":{\"x\":12,\"y\":34,\"width\":120,\"height\":44},\"children\":[]}]}}";

        RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
        JSONObject result = new JSONObject(provider.getCurrentWebDomSnapshot("Submit").toJsonString());

        assertTrue("Provider must succeed", result.getBoolean("success"));
        
        // Validate webDom field exists and contains valid JSON
        assertNotNull("webDom field must exist", result.optString("webDom", null));
        JSONObject webDom = new JSONObject(result.getString("webDom"));
        
        // WebDomObservationAdapter expects these fields in the payload
        assertNotNull("tree must exist for adapter mapping", webDom.optJSONObject("tree"));
        
        JSONObject tree = webDom.getJSONObject("tree");
        assertTrue("tree must have 'tag' field for canonical tagName mapping", tree.has("tag"));
        assertEquals("body", tree.getString("tag"));
        
        // Adapter expects children array for traversal
        assertTrue("tree must have children array", tree.has("children"));
        JSONArray children = tree.getJSONArray("children");
        assertTrue("tree must have at least one child", children.length() > 0);
        
        // Validate actionable node structure that adapter will collect
        JSONObject actionableNode = children.getJSONObject(0);
        assertTrue("actionable node must have 'ref' for canonical mapping", actionableNode.has("ref"));
        assertTrue("actionable node must have 'tag' for canonical tagName", actionableNode.has("tag"));
        assertTrue("actionable node must have 'selector' for canonical mapping", actionableNode.has("selector"));
        assertTrue("actionable node must have 'clickable' for actionability check", actionableNode.has("clickable"));
        assertTrue("actionable node must have 'bounds' for coordinate extraction", actionableNode.has("bounds"));
        
        // Validate quality metrics that adapter uses
        assertTrue("nodeCount must exist for quality metrics", webDom.has("nodeCount"));
        assertTrue("maxDepthReached must exist for quality metrics", webDom.has("maxDepthReached"));
        assertTrue("truncated must exist for quality metrics", webDom.has("truncated"));
        
        // Validate metadata that adapter prefers over fallbacks
        assertTrue("pageUrl must exist in payload for adapter preference", webDom.has("pageUrl"));
        assertTrue("pageTitle must exist in payload for adapter preference", webDom.has("pageTitle"));
        assertEquals("https://example.test/form", webDom.getString("pageUrl"));
        assertEquals("Test Form", webDom.getString("pageTitle"));
    }

    @Test
    public void getCurrentWebDomSnapshot_emptyTreeStillValidForAdapter() throws Exception {
        // Validate that even with empty tree, provider output can be safely consumed by adapter
        FakeBridge bridge = new FakeBridge();
        bridge.rawEvalResult = "{\"pageUrl\":\"https://example.test/empty\",\"pageTitle\":\"Empty Page\",\"nodeCount\":1,\"maxDepthReached\":0,\"truncated\":false,\"tree\":{\"ref\":\"node-0\",\"tag\":\"body\",\"text\":\"\",\"bounds\":{\"x\":0,\"y\":0,\"width\":1080,\"height\":1920},\"children\":[]}}";

        RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
        JSONObject result = new JSONObject(provider.getCurrentWebDomSnapshot("nothing").toJsonString());

        assertTrue("Provider must succeed even with empty tree", result.getBoolean("success"));
        JSONObject webDom = new JSONObject(result.getString("webDom"));
        
        // Adapter must handle empty children gracefully
        JSONObject tree = webDom.getJSONObject("tree");
        assertTrue("tree must still have children field", tree.has("children"));
        JSONArray children = tree.getJSONArray("children");
        assertEquals("empty tree should have zero children", 0, children.length());
        
        // Quality metrics should still be present for adapter
        assertEquals(1, webDom.getInt("nodeCount"));
        assertEquals(0, webDom.getInt("maxDepthReached"));
        assertFalse(webDom.getBoolean("truncated"));
    }

    private static final class FakeBridge extends WebViewJsBridge {

        private String rawEvalResult;
        private Exception requireException;
        private Exception evaluateException;
        private String fallbackPageUrl;
        private String fallbackPageTitle;
        private boolean pageUrlRequested;
        private boolean pageTitleRequested;

        private FakeBridge() {
            super(null, null, null);
        }

        @Override
        public WebViewHandle requireWebView() throws Exception {
            if (requireException != null) {
                throw requireException;
            }
            return new WebViewHandle((Activity) null, null, 1, "test");
        }

        @Override
        public String evaluate(android.webkit.WebView webView, String script) throws Exception {
            if (evaluateException != null) {
                throw evaluateException;
            }
            return rawEvalResult;
        }

        @Override
        public String getCurrentPageUrl(WebViewHandle handle) {
            pageUrlRequested = true;
            return fallbackPageUrl;
        }

        @Override
        public String getCurrentPageTitle(WebViewHandle handle) {
            pageTitleRequested = true;
            return fallbackPageTitle;
        }
    }

    private static final class NoOpAgentLogger implements AgentLogger {
        @Override
        public void d(String tag, String message) {
        }

        @Override
        public void i(String tag, String message) {
        }

        @Override
        public void w(String tag, String message) {
        }

        @Override
        public void e(String tag, String message) {
        }

        @Override
        public void e(String tag, String message, Throwable throwable) {
        }
    }
}
