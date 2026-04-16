package com.hh.agent.android.viewcontext;

public final class UnifiedViewObservation {
    public final String source;
    public final String interactionDomain;
    public final String activityClassName;
    public final String targetHint;
    public final String pageUrl;
    public final String pageTitle;
    public final String pageSummary;
    public final String uiTreeJson;
    public final String screenElementsJson;
    public final String qualityJson;
    public final String rawJson;

    public UnifiedViewObservation(String source,
                                  String interactionDomain,
                                  String activityClassName,
                                  String targetHint,
                                  String pageUrl,
                                  String pageTitle,
                                  String pageSummary,
                                  String uiTreeJson,
                                  String screenElementsJson,
                                  String qualityJson,
                                  String rawJson) {
        this.source = source;
        this.interactionDomain = interactionDomain;
        this.activityClassName = activityClassName;
        this.targetHint = targetHint;
        this.pageUrl = pageUrl;
        this.pageTitle = pageTitle;
        this.pageSummary = pageSummary;
        this.uiTreeJson = uiTreeJson;
        this.screenElementsJson = screenElementsJson;
        this.qualityJson = qualityJson;
        this.rawJson = rawJson;
    }
}
