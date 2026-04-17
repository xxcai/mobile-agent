package com.hh.agent.android.webaction;

import com.hh.agent.android.channel.WebActionToolChannel;
import com.hh.agent.android.viewcontext.ViewObservationSnapshotRegistry;
import com.hh.agent.android.web.WebViewJsBridge;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RealWebActionExecutorTest {

    @Test
    public void execute_rejectsStaleSnapshot() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "https://example.test/form";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "web",
                "submit",
                null,
                "{}",
                "https://example.test/form",
                "Mock Page"
        );

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("ref", "node-1")
                .put("observation", new JSONObject().put("snapshotId", "obs_old"))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertEquals(false, json.getBoolean("success"));
        assertEquals("stale_web_observation", json.getString("error"));
        assertEquals(WebActionToolChannel.CHANNEL_NAME, json.getString("channel"));
        assertEquals("web", json.getString("domain"));
    }

    @Test
    public void execute_rejectsWrongInteractionDomain() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "https://example.test/form";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        String snapshotId = ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "native",
                "submit",
                null,
                "{}",
                null,
                null
        ).snapshotId;

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("ref", "node-1")
                .put("observation", new JSONObject().put("snapshotId", snapshotId))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertEquals(false, json.getBoolean("success"));
        assertEquals("stale_web_observation", json.getString("error"));
    }

    @Test
    public void execute_rejectsPageUrlMismatch() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "https://example.test/other";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        String snapshotId = ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "web",
                "submit",
                null,
                "{}",
                "https://example.test/form",
                "Mock Page"
        ).snapshotId;

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("ref", "node-1")
                .put("observation", new JSONObject().put("snapshotId", snapshotId))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertEquals(false, json.getBoolean("success"));
        assertEquals("stale_web_observation", json.getString("error"));
    }

    @Test
    public void execute_rejectsActivityMismatch() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "https://example.test/form";
        bridge.activityClassName = "com.hh.agent.OtherActivity";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        String snapshotId = ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "web",
                "submit",
                null,
                "{}",
                "https://example.test/form",
                "Mock Page"
        ).snapshotId;

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("ref", "node-1")
                .put("observation", new JSONObject().put("snapshotId", snapshotId))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertEquals(false, json.getBoolean("success"));
        assertEquals("stale_web_observation", json.getString("error"));
    }

    @Test
    public void execute_rejectsRefMissWithoutSelectorFallback() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "https://example.test/form";
        bridge.rawEvalResult = "\"{\\\"ok\\\":false,\\\"error\\\":\\\"element_not_found\\\"}\"";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        String snapshotId = ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "web",
                "submit",
                null,
                "{}",
                "https://example.test/form",
                "Mock Page"
        ).snapshotId;

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("ref", "node-1")
                .put("selector", "#submit")
                .put("observation", new JSONObject().put("snapshotId", snapshotId))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertEquals(false, json.getBoolean("success"));
        assertEquals("stale_web_observation", json.getString("error"));
    }

    @Test
    public void execute_evalJsWrapsRawResult() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "https://example.test/form";
        bridge.rawEvalResult = "\"hello\"";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        String snapshotId = ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "web",
                "submit",
                null,
                "{}",
                "https://example.test/form",
                "Mock Page"
        ).snapshotId;

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "eval_js")
                .put("script", "document.title")
                .put("observation", new JSONObject().put("snapshotId", snapshotId))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertTrue(json.getBoolean("success"));
        assertEquals(false, json.getBoolean("mock"));
        assertEquals("Real web action executor completed the request", json.getString("message"));
        assertEquals(snapshotId, json.getString("observationSnapshotId"));
        assertEquals("string", json.getString("valueType"));
        assertEquals("hello", json.getString("value"));
    }

    @Test
    public void execute_clickUsesPayloadRefWhenSelectorFallbackSucceeds() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "https://example.test/form";
        bridge.rawEvalResult = "\"{\\\"ok\\\":true,\\\"ref\\\":\\\"node-2\\\",\\\"tag\\\":\\\"BUTTON\\\"}\"";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        String snapshotId = ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "web",
                "submit",
                null,
                "{}",
                "https://example.test/form",
                "Mock Page"
        ).snapshotId;

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("ref", "stale-node")
                .put("selector", "#submit")
                .put("observation", new JSONObject().put("snapshotId", snapshotId))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertTrue(json.getBoolean("success"));
        assertEquals("node-2", json.getString("ref"));
        assertEquals(snapshotId, json.getString("observationSnapshotId"));
        assertEquals(false, json.getBoolean("mock"));
        assertEquals("native_injection", json.getString("tapMode"));
    }

    @Test
    public void execute_acceptsAssetSnapshotWhenCurrentWebViewUrlIsAboutBlank() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.pageUrl = "about:blank";
        bridge.rawEvalResult = "\"{\\\"ok\\\":true,\\\"ref\\\":\\\"node-8\\\",\\\"tag\\\":\\\"BUTTON\\\"}\"";
        RealWebActionExecutor executor = new RealWebActionExecutor(bridge);
        String snapshotId = ViewObservationSnapshotRegistry.createSnapshot(
                "com.hh.agent.BusinessWebActivity",
                "web_dom",
                "web",
                "submit",
                null,
                "{}",
                "file:///android_asset/",
                "Form Local Page"
        ).snapshotId;

        ToolResult result = executor.execute(WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("selector", "#debug-submit")
                .put("observation", new JSONObject().put("snapshotId", snapshotId))));
        JSONObject json = new JSONObject(result.toJsonString());

        assertTrue(json.getBoolean("success"));
        assertEquals("android_web_action_tool", json.getString("channel"));
        assertEquals("web", json.getString("domain"));
        assertEquals("click", json.getString("action"));
        assertEquals("native_injection", json.getString("tapMode"));
    }

    private static final class FakeBridge extends WebViewJsBridge {

        private String rawEvalResult;
        private String pageUrl;
        private String activityClassName = "com.hh.agent.BusinessWebActivity";

        private FakeBridge() {
            super(null, null, null);
        }

        @Override
        public WebViewHandle requireWebView() {
            return new WebViewHandle(null, null, 1, "test");
        }

        @Override
        public String evaluate(android.webkit.WebView webView, String script) {
            return rawEvalResult;
        }

        @Override
        public String getCurrentActivityClassName(WebViewHandle handle) {
            return activityClassName;
        }

        @Override
        public String getCurrentPageUrl(WebViewHandle handle) {
            return pageUrl;
        }

        @Override
        public NativeTapResult performNormalizedTap(WebViewHandle handle, double normalizedX, double normalizedY) {
            return NativeTapResult.success(
                    true,
                    1080,
                    1920,
                    (float) normalizedX,
                    (float) normalizedY,
                    540,
                    960,
                    540,
                    960,
                    true,
                    true
            );
        }
    }
}
