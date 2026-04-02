package com.hh.agent.android.viewcontext;

import android.app.Activity;

import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.web.WebDomScriptFactory;
import com.hh.agent.android.web.WebViewJsBridge;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

public final class RealWebDomSnapshotProvider implements WebDomSnapshotProvider {

    private static final String INTERACTION_DOMAIN_WEB = "web";
    private static final String SOURCE_WEB_DOM = "web_dom";
    private static final String OBSERVATION_SCOPE_CURRENT_TURN = "current_turn";

    private final WebViewJsBridge jsBridge;

    public RealWebDomSnapshotProvider(WebViewJsBridge jsBridge) {
        this.jsBridge = jsBridge;
    }

    public static WebDomSnapshotProvider createDefault() {
        return new RealWebDomSnapshotProvider(WebViewJsBridge.createDefault());
    }

    @Override
    public ToolResult getCurrentWebDomSnapshot(String targetHint) {
        try {
            WebViewJsBridge.WebViewHandle handle = jsBridge.requireWebView();
            Activity activity = handle.activity;
            String raw = jsBridge.evaluate(handle.webView, WebDomScriptFactory.buildSnapshotScript());
            String decoded = WebViewJsBridge.decodeJsResult(raw);
            JSONObject payload = new JSONObject(decoded);
            String pageUrl = safeString(payload.optString("pageUrl", handle.webView.getUrl()));
            String pageTitle = safeString(payload.optString("pageTitle", handle.webView.getTitle()));

            ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.createSnapshot(
                    activity != null ? activity.getClass().getName() : null,
                    SOURCE_WEB_DOM,
                    INTERACTION_DOMAIN_WEB,
                    targetHint,
                    null,
                    payload.toString(),
                    pageUrl,
                    pageTitle
            );

            return ToolResult.success()
                    .with("channel", ViewContextToolChannel.CHANNEL_NAME)
                    .with("source", SOURCE_WEB_DOM)
                    .with("interactionDomain", INTERACTION_DOMAIN_WEB)
                    .with("mock", false)
                    .with("targetHint", targetHint)
                    .with("activityClassName", activity != null ? activity.getClass().getName() : null)
                    .with("observationMode", "json_web_dom")
                    .with("snapshotId", snapshot.snapshotId)
                    .with("snapshotCreatedAtEpochMs", snapshot.createdAtEpochMs)
                    .with("snapshotScope", OBSERVATION_SCOPE_CURRENT_TURN)
                    .with("snapshotCurrentTurnOnly", snapshot.currentTurnOnly)
                    .with("pageUrl", pageUrl)
                    .with("pageTitle", pageTitle)
                    .with("nativeViewXml", (String) null)
                    .with("webDom", payload.toString())
                    .with("webDomFormat", "json")
                    .with("screenSnapshot", (String) null)
                    .with("webViewCandidateCount", handle.candidateCount)
                    .with("webViewSelectionReason", handle.selectionReason);
        } catch (Exception exception) {
            return ToolResult.error("dom_capture_failed", exception.getMessage())
                    .with("channel", ViewContextToolChannel.CHANNEL_NAME)
                    .with("source", SOURCE_WEB_DOM)
                    .with("interactionDomain", INTERACTION_DOMAIN_WEB)
                    .with("targetHint", targetHint);
        }
    }

    private String safeString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
