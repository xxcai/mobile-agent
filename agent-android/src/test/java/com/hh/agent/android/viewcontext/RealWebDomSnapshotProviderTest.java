package com.hh.agent.android.viewcontext;

import android.app.Activity;

import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.web.WebViewJsBridge;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
