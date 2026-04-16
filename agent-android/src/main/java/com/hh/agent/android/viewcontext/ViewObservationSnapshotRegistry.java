package com.hh.agent.android.viewcontext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the latest host-process observation snapshot for observation-bound execution.
 */
public final class ViewObservationSnapshotRegistry {

    private static final AtomicReference<ViewObservationSnapshot> LATEST_SNAPSHOT =
            new AtomicReference<>();
    private static final Map<String, ViewObservationSnapshot> SNAPSHOT_MAP = new HashMap<>();
    private static final String DOMAIN_NATIVE = "native";

    private ViewObservationSnapshotRegistry() {
    }

    public static ViewObservationSnapshot createSnapshot(String activityClassName,
                                                         String source,
                                                         String targetHint,
                                                         String nativeViewXml) {
        return createSnapshot(
                activityClassName,
                source,
                DOMAIN_NATIVE,
                targetHint,
                nativeViewXml,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ViewObservationSnapshot createSnapshot(String activityClassName,
                                                         String source,
                                                         String targetHint,
                                                         String nativeViewXml,
                                                         String visualObservationJson,
                                                         String screenSnapshot) {
        return createSnapshot(
                activityClassName,
                source,
                DOMAIN_NATIVE,
                targetHint,
                nativeViewXml,
                null,
                null,
                null,
                visualObservationJson,
                screenSnapshot,
                null
        );
    }

    public static ViewObservationSnapshot createSnapshot(String activityClassName,
                                                         String source,
                                                         String targetHint,
                                                         String nativeViewXml,
                                                         String visualObservationJson,
                                                         String screenSnapshot,
                                                         String hybridObservationJson) {
        return createSnapshot(
                activityClassName,
                source,
                DOMAIN_NATIVE,
                targetHint,
                nativeViewXml,
                null,
                null,
                null,
                visualObservationJson,
                screenSnapshot,
                hybridObservationJson
        );
    }


    public static ViewObservationSnapshot createSnapshot(String activityClassName,
                                                         String source,
                                                         String interactionDomain,
                                                         String targetHint,
                                                         String nativeViewXml,
                                                         String webDom,
                                                         String pageUrl,
                                                         String pageTitle) {
        return createSnapshot(
                activityClassName,
                source,
                interactionDomain,
                targetHint,
                nativeViewXml,
                webDom,
                pageUrl,
                pageTitle,
                null,
                null,
                null
        );
    }

    public static ViewObservationSnapshot createSnapshot(String activityClassName,
                                                         String source,
                                                         String interactionDomain,
                                                         String targetHint,
                                                         String nativeViewXml,
                                                         String webDom,
                                                         String pageUrl,
                                                         String pageTitle,
                                                         String visualObservationJson,
                                                         String screenSnapshot,
                                                         String hybridObservationJson) {
        ViewObservationSnapshot snapshot = new ViewObservationSnapshot(
                "obs_" + UUID.randomUUID().toString().replace("-", ""),
                activityClassName,
                source,
                interactionDomain != null ? interactionDomain : DOMAIN_NATIVE,
                targetHint,
                System.currentTimeMillis(),
                true,
                nativeViewXml,
                webDom,
                pageUrl,
                pageTitle,
                visualObservationJson,
                screenSnapshot,
                hybridObservationJson
        );
        LATEST_SNAPSHOT.set(snapshot);
        SNAPSHOT_MAP.put(snapshot.snapshotId, snapshot);
        return snapshot;
    }

    public static ViewObservationSnapshot createSnapshotWithProjection(String activityClassName,
                                                                        String source,
                                                                        String interactionDomain,
                                                                        String targetHint,
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
        ViewObservationSnapshot snapshot = new ViewObservationSnapshot(
                "obs_" + UUID.randomUUID().toString().replace("-", ""),
                activityClassName,
                source,
                interactionDomain != null ? interactionDomain : DOMAIN_NATIVE,
                targetHint,
                System.currentTimeMillis(),
                true,
                nativeViewXml,
                webDom,
                pageUrl,
                pageTitle,
                visualObservationJson,
                screenSnapshot,
                hybridObservationJson,
                tree,
                nodes,
                nodesFormat
        );
        LATEST_SNAPSHOT.set(snapshot);
        SNAPSHOT_MAP.put(snapshot.snapshotId, snapshot);
        return snapshot;
    }

    public static ViewObservationSnapshot getLatestSnapshot() {
        return LATEST_SNAPSHOT.get();
    }

    public static ViewObservationSnapshot findSnapshotById(String snapshotId) {
        if (snapshotId == null || snapshotId.isEmpty()) {
            return null;
        }
        ViewObservationSnapshot snapshot = SNAPSHOT_MAP.get(snapshotId);
        if (snapshot != null) {
            return snapshot;
        }
        ViewObservationSnapshot latest = LATEST_SNAPSHOT.get();
        if (latest != null && snapshotId.equals(latest.snapshotId)) {
            return latest;
        }
        return null;
    }

    public static void clearSnapshots() {
        SNAPSHOT_MAP.clear();
        LATEST_SNAPSHOT.set(null);
    }
}
