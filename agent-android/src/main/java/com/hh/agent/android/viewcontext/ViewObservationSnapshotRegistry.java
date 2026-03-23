package com.hh.agent.android.viewcontext;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the latest host-process observation snapshot for Step 03A.
 */
public final class ViewObservationSnapshotRegistry {

    private static final AtomicReference<ViewObservationSnapshot> LATEST_SNAPSHOT =
            new AtomicReference<>();

    private ViewObservationSnapshotRegistry() {
    }

    public static ViewObservationSnapshot createSnapshot(String activityClassName,
                                                         String source,
                                                         String targetHint,
                                                         String nativeViewXml) {
        ViewObservationSnapshot snapshot = new ViewObservationSnapshot(
                "obs_" + UUID.randomUUID().toString().replace("-", ""),
                activityClassName,
                source,
                targetHint,
                System.currentTimeMillis(),
                true,
                nativeViewXml
        );
        LATEST_SNAPSHOT.set(snapshot);
        return snapshot;
    }

    public static ViewObservationSnapshot getLatestSnapshot() {
        return LATEST_SNAPSHOT.get();
    }
}
