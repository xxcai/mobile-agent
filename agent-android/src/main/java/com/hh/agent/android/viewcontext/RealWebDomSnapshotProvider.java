package com.hh.agent.android.viewcontext;

import android.app.Activity;

import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.log.AgentLogs;
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
        WebViewJsBridge.WebViewHandle handle = null;
        Activity activity = null;
        String raw = null;
        String decoded = null;
        try {
            handle = jsBridge.requireWebView();
            activity = handle.activity;
            AgentLogs.info("RealWebDomSnapshotProvider", "collect_start",
                    "activity=" + activityClassName(activity)
                            + " candidates=" + handle.candidateCount
                            + " target_hint=" + targetHint
                            + " selection_reason=" + handle.selectionReason);

            raw = jsBridge.evaluate(handle.webView, WebDomScriptFactory.buildSnapshotScript());
            AgentLogs.info("RealWebDomSnapshotProvider", "evaluate_complete",
                    "raw_length=" + safeLength(raw));

            decoded = WebViewJsBridge.decodeJsResult(raw);
            AgentLogs.info("RealWebDomSnapshotProvider", "decode_complete",
                    "decoded_length=" + safeLength(decoded));

            JSONObject payload = new JSONObject(decoded);
            String fallbackPageUrl = jsBridge.getCurrentPageUrl(handle);
            String fallbackPageTitle = jsBridge.getCurrentPageTitle(handle);
            String pageUrl = safeString(payload.optString("pageUrl", fallbackPageUrl));
            String pageTitle = safeString(payload.optString("pageTitle", fallbackPageTitle));

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
            String failureStage = determineFailureStage(handle, raw, decoded);
            AgentLogs.warn("RealWebDomSnapshotProvider", "collect_failed",
                    "stage=" + failureStage
                            + " activity=" + activityClassName(activity)
                            + " candidates=" + candidateCount(handle)
                            + " selection_reason=" + selectionReason(handle)
                            + " message=" + exception.getMessage());
            return ToolResult.error("dom_capture_failed", exception.getMessage())
                    .with("channel", ViewContextToolChannel.CHANNEL_NAME)
                    .with("source", SOURCE_WEB_DOM)
                    .with("interactionDomain", INTERACTION_DOMAIN_WEB)
                    .with("targetHint", targetHint)
                    .with("failureStage", failureStage)
                    .with("activityClassName", activityClassName(activity))
                    .with("webViewCandidateCount", candidateCount(handle))
                    .with("webViewSelectionReason", selectionReason(handle))
                    .with("rawJsResult", raw)
                    .with("decodedJsResult", decoded);
        }
    }

    private String determineFailureStage(WebViewJsBridge.WebViewHandle handle, String raw, String decoded) {
        if (handle == null) {
            return "require_webview";
        }
        if (raw == null) {
            return "evaluate_javascript";
        }
        if (decoded == null) {
            return "decode_js_result";
        }
        return "parse_payload";
    }

    private String activityClassName(Activity activity) {
        return activity != null ? activity.getClass().getName() : null;
    }

    private int candidateCount(WebViewJsBridge.WebViewHandle handle) {
        return handle != null ? handle.candidateCount : 0;
    }

    private String selectionReason(WebViewJsBridge.WebViewHandle handle) {
        return handle != null ? handle.selectionReason : null;
    }

    private int safeLength(String value) {
        return value != null ? value.length() : 0;
    }

    private String safeString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
