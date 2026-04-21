package com.hh.agent.android.viewcontext;

/**
 * In-memory observation snapshot metadata for observation-bound execution.
 */
public final class ViewObservationSnapshot {

    public final String snapshotId;
    public final String activityClassName;
    public final String source;
    public final String interactionDomain;
    public final String targetHint;
    public final long createdAtEpochMs;
    public final boolean currentTurnOnly;
    public final String uiTreeJson;
    public final String screenElementsJson;
    public final String pageSummary;
    public final String qualityJson;
    public final String rawJson;
    public final String nativeViewXml;
    public final String webDom;
    public final String pageUrl;
    public final String pageTitle;
    public final String visualObservationJson;
    public final String screenSnapshot;
    public final String hybridObservationJson;

    public ViewObservationSnapshot(String snapshotId,
                                   String activityClassName,
                                   String source,
                                   String interactionDomain,
                                   String targetHint,
                                   long createdAtEpochMs,
                                   boolean currentTurnOnly,
                                   String uiTreeJson,
                                   String screenElementsJson,
                                   String pageSummary,
                                   String qualityJson,
                                   String rawJson,
                                   String nativeViewXml,
                                   String webDom,
                                   String pageUrl,
                                   String pageTitle,
                                   String visualObservationJson,
                                   String screenSnapshot,
                                   String hybridObservationJson) {
        this.snapshotId = snapshotId;
        this.activityClassName = activityClassName;
        this.source = source;
        this.interactionDomain = interactionDomain;
        this.targetHint = targetHint;
        this.createdAtEpochMs = createdAtEpochMs;
        this.currentTurnOnly = currentTurnOnly;
        this.uiTreeJson = uiTreeJson;
        this.screenElementsJson = screenElementsJson;
        this.pageSummary = pageSummary;
        this.qualityJson = qualityJson;
        this.rawJson = rawJson;
        this.nativeViewXml = nativeViewXml;
        this.webDom = webDom;
        this.pageUrl = pageUrl;
        this.pageTitle = pageTitle;
        this.visualObservationJson = visualObservationJson;
        this.screenSnapshot = screenSnapshot;
        this.hybridObservationJson = hybridObservationJson;
    }
}
