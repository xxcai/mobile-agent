package com.hh.agent.android.viewcontext;

import android.app.Activity;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.thread.MainThreadRunner;
import com.hh.agent.core.tool.ToolResult;

/**
 * Mock implementation used to keep the web-domain observation contract stable before real DOM capture lands.
 */
public final class MockWebDomSnapshotProvider implements WebDomSnapshotProvider {

    private static final String INTERACTION_DOMAIN_WEB = "web";
    private static final String SOURCE_WEB_DOM = "web_dom";
    private static final String OBSERVATION_SCOPE_CURRENT_TURN = "current_turn";
    private static final String OBSERVATION_MODE_MOCK_WEBVIEW_DOM = "mock_webview_dom";
    private static final String MOCK_WEB_DOM =
            "<html><body><div id=\"mock-root\"><button data-action=\"open-contact\">张三</button></div></body></html>";
    private static final long WEBVIEW_ACCESS_TIMEOUT_MS = 1500L;

    private final StableForegroundActivityProvider stableForegroundActivityProvider;
    private final WebViewFinder webViewFinder;
    private final MainThreadRunner mainThreadRunner;

    public MockWebDomSnapshotProvider(StableForegroundActivityProvider stableForegroundActivityProvider,
                                      WebViewFinder webViewFinder,
                                      MainThreadRunner mainThreadRunner) {
        this.stableForegroundActivityProvider = stableForegroundActivityProvider;
        this.webViewFinder = webViewFinder;
        this.mainThreadRunner = mainThreadRunner;
    }

    @Override
    public ToolResult getCurrentWebDomSnapshot(String targetHint) {
        Activity activity;
        WebViewFinder.WebViewFindResult findResult;
        String pageUrl = null;
        String pageTitle = null;
        try {
            activity = mainThreadRunner.call(
                    () -> stableForegroundActivityProvider.getStableForegroundActivity(),
                    WEBVIEW_ACCESS_TIMEOUT_MS
            );
            findResult = mainThreadRunner.call(
                    () -> webViewFinder.findPrimaryWebView(activity),
                    WEBVIEW_ACCESS_TIMEOUT_MS
            );
            if (findResult != null) {
                final WebView webView = findResult.webView;
                pageUrl = mainThreadRunner.call(
                        () -> safeString(webView.getUrl()),
                        WEBVIEW_ACCESS_TIMEOUT_MS
                );
                pageTitle = mainThreadRunner.call(
                        () -> safeString(webView.getTitle()),
                        WEBVIEW_ACCESS_TIMEOUT_MS
                );
            }
        } catch (Exception exception) {
            AgentLogs.warn("MockWebDomSnapshotProvider", "collect_failed", "message=" + exception.getMessage());
            return ToolResult.error("dom_capture_failed", exception.getMessage())
                    .with("channel", ViewContextToolChannel.CHANNEL_NAME)
                    .with("source", SOURCE_WEB_DOM)
                    .with("interactionDomain", INTERACTION_DOMAIN_WEB)
                    .with("mock", true)
                    .with("targetHint", targetHint);
        }

        String activityClassName = activity != null ? activity.getClass().getName() : null;
        AgentLogs.info("MockWebDomSnapshotProvider", "collect_start",
                "activity=" + activityClassName
                        + " candidates=" + (findResult != null ? findResult.candidateCount : 0)
                        + " target_hint=" + targetHint);

        ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.createSnapshot(
                activityClassName,
                SOURCE_WEB_DOM,
                INTERACTION_DOMAIN_WEB,
                targetHint,
                null,
                MOCK_WEB_DOM
        );
        AgentLogs.info("MockWebDomSnapshotProvider", "collect_complete",
                "snapshot_id=" + snapshot.snapshotId
                        + " page_url=" + pageUrl
                        + " selection_reason="
                        + (findResult != null ? findResult.selectionReason : "no_visible_webview"));

        return ToolResult.success()
                .with("channel", ViewContextToolChannel.CHANNEL_NAME)
                .with("source", SOURCE_WEB_DOM)
                .with("interactionDomain", INTERACTION_DOMAIN_WEB)
                .with("mock", true)
                .with("targetHint", targetHint)
                .with("activityClassName", activityClassName)
                .with("observationMode", OBSERVATION_MODE_MOCK_WEBVIEW_DOM)
                .with("snapshotId", snapshot.snapshotId)
                .with("snapshotCreatedAtEpochMs", snapshot.createdAtEpochMs)
                .with("snapshotScope", OBSERVATION_SCOPE_CURRENT_TURN)
                .with("snapshotCurrentTurnOnly", snapshot.currentTurnOnly)
                .with("pageUrl", pageUrl)
                .with("pageTitle", pageTitle)
                .with("nativeViewXml", (String) null)
                .with("webDom", MOCK_WEB_DOM)
                .with("webDomFormat", "html")
                .with("screenSnapshot", (String) null)
                .with("webViewCandidateCount", findResult != null ? findResult.candidateCount : 0)
                .with("webViewSelectionReason", findResult != null ? findResult.selectionReason : "no_visible_webview");
    }

    public static WebDomSnapshotProvider createDefault() {
        return new MockWebDomSnapshotProvider(
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return InProcessViewHierarchyDumper.getCurrentStableForegroundActivity();
                    }
                },
                new WebViewFinder(),
                new MainThreadRunner()
        );
    }

    @Nullable
    private String safeString(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
