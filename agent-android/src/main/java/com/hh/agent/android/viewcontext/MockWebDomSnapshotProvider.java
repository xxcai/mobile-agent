package com.hh.agent.android.viewcontext;

import android.app.Activity;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.log.AgentLogs;
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

    private final StableForegroundActivityProvider stableForegroundActivityProvider;
    private final WebViewFinder webViewFinder;

    public MockWebDomSnapshotProvider(StableForegroundActivityProvider stableForegroundActivityProvider,
                                      WebViewFinder webViewFinder) {
        this.stableForegroundActivityProvider = stableForegroundActivityProvider;
        this.webViewFinder = webViewFinder;
    }

    @Override
    public ToolResult getCurrentWebDomSnapshot(String targetHint) {
        Activity activity = stableForegroundActivityProvider.getStableForegroundActivity();
        @Nullable WebViewFinder.WebViewFindResult findResult = webViewFinder.findPrimaryWebView(activity);
        String activityClassName = activity != null ? activity.getClass().getName() : null;
        String pageUrl = findResult != null ? safeString(findResult.webView.getUrl()) : null;
        String pageTitle = findResult != null ? safeString(findResult.webView.getTitle()) : null;
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
                new WebViewFinder()
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
