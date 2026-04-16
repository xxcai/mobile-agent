package com.hh.agent.android.viewcontext;

import org.json.JSONObject;

/**
 * In-memory observation snapshot metadata for observation-bound execution.
 * <p>
 * Unified observation structure uses tree/nodes/nodesFormat for all domains.
 * Legacy fields (nativeViewXml, webDom, hybridObservationJson) are kept for backward compatibility.
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
    public final String visualObservationJson;
    public final String screenSnapshot;
    public final String hybridObservationJson;
    public final String tree;
    public final String nodes;
    public final String nodesFormat;

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
                                   String pageTitle,
                                   String visualObservationJson,
                                   String screenSnapshot,
                                   String hybridObservationJson) {
        this(snapshotId, activityClassName, source, interactionDomain, targetHint,
                createdAtEpochMs, currentTurnOnly, nativeViewXml, webDom, pageUrl, pageTitle,
                visualObservationJson, screenSnapshot, hybridObservationJson, null, null, null);
    }

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
                                   String pageTitle,
                                   String visualObservationJson,
                                   String screenSnapshot,
                                   String hybridObservationJson,
                                   String tree,
                                   String nodes,
                                   String nodesFormat) {
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
        this.visualObservationJson = visualObservationJson;
        this.screenSnapshot = screenSnapshot;
        this.hybridObservationJson = hybridObservationJson;
        this.tree = tree;
        this.nodes = nodes;
        this.nodesFormat = nodesFormat;
    }

    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("snapshotId", snapshotId);
            json.put("activityClassName", activityClassName);
            json.put("source", source);
            json.put("interactionDomain", interactionDomain);
            json.put("targetHint", targetHint);
            json.put("createdAtEpochMs", createdAtEpochMs);
            json.put("currentTurnOnly", currentTurnOnly);
            json.put("tree", tree);
            json.put("nodes", nodes);
            json.put("nodesFormat", nodesFormat);
            json.put("pageUrl", pageUrl);
            json.put("pageTitle", pageTitle);
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public ViewObservationProjection toProjection() {
        return ViewObservationProjectionFacade.project(
                determineImplementationKey(),
                tree != null ? tree : "",
                nodes != null ? nodes : "",
                nodesFormat != null ? nodesFormat : ""
        );
    }

    private String determineImplementationKey() {
        if ("web".equals(interactionDomain)) {
            return WebProjectionStrategy.IMPLEMENTATION_KEY;
        }
        if (hybridObservationJson != null && !hybridObservationJson.isEmpty()) {
            return NativeAccessibilityProjectionStrategy.IMPLEMENTATION_KEY;
        }
        return NativeViewXmlProjectionStrategy.IMPLEMENTATION_KEY;
    }
}
