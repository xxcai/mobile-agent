package com.hh.agent.android.viewcontext;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the latest host-process observation snapshot for Step 03A.
 */
public final class ViewObservationSnapshotRegistry {

    private static final AtomicReference<ViewObservationSnapshot> LATEST_SNAPSHOT =
            new AtomicReference<>();
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
                null
        );
    }

    public static ViewObservationSnapshot createSnapshot(String activityClassName,
                                                         String source,
                                                         String interactionDomain,
                                                         String targetHint,
                                                         String nativeViewXml,
                                                         String webDom) {
        return createSnapshot(
                activityClassName,
                source,
                interactionDomain,
                targetHint,
                nativeViewXml,
                webDom,
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
                                                         String pageTitle) {
        ViewObservationSnapshot snapshot = new ViewObservationSnapshot(
                "obs_" + UUID.randomUUID().toString().replace("-", ""),
                activityClassName,
                source,
                interactionDomain,
                targetHint,
                System.currentTimeMillis(),
                true,
                nativeViewXml,
                webDom,
                pageUrl,
                pageTitle
        );
        LATEST_SNAPSHOT.set(snapshot);
        return snapshot;
    }

    public static ViewObservationSnapshot getLatestSnapshot() {
        return LATEST_SNAPSHOT.get();
    }
}
