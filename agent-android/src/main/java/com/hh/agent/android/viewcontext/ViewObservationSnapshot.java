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
    public final String nativeViewXml;
    public final String webDom;
    public final String pageUrl;
    public final String pageTitle;

    public ViewObservationSnapshot(String snapshotId,
                                   String activityClassName,
                                   String source,
                                   String interactionDomain,
                                   String targetHint,
                                   long createdAtEpochMs,
                                   boolean currentTurnOnly,
                                   String nativeViewXml,
                                   String webDom,
                                   String pageUrl,
                                   String pageTitle) {
        this.snapshotId = snapshotId;
        this.activityClassName = activityClassName;
        this.source = source;
        this.interactionDomain = interactionDomain;
        this.targetHint = targetHint;
        this.createdAtEpochMs = createdAtEpochMs;
        this.currentTurnOnly = currentTurnOnly;
        this.nativeViewXml = nativeViewXml;
        this.webDom = webDom;
        this.pageUrl = pageUrl;
        this.pageTitle = pageTitle;
    }
}
