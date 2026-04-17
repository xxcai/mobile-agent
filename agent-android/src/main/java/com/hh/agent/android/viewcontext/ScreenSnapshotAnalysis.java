package com.hh.agent.android.viewcontext;

import androidx.annotation.Nullable;

/**
 * Structured result produced from a screenshot-based analyzer.
 */
public final class ScreenSnapshotAnalysis {

    public final String activityClassName;
    public final String observationMode;
    @Nullable
    public final String screenSnapshotRef;
    @Nullable
    public final String compactObservationJson;
    @Nullable
    public final String rawObservationJson;
    public final int imageWidth;
    public final int imageHeight;

    public ScreenSnapshotAnalysis(String activityClassName,
                                  String observationMode,
                                  @Nullable String screenSnapshotRef,
                                  @Nullable String compactObservationJson,
                                  @Nullable String rawObservationJson,
                                  int imageWidth,
                                  int imageHeight) {
        if (activityClassName == null || activityClassName.trim().isEmpty()) {
            throw new IllegalArgumentException("activityClassName cannot be empty");
        }
        if (observationMode == null || observationMode.trim().isEmpty()) {
            throw new IllegalArgumentException("observationMode cannot be empty");
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("image size must be positive");
        }
        this.activityClassName = activityClassName;
        this.observationMode = observationMode;
        this.screenSnapshotRef = screenSnapshotRef;
        this.compactObservationJson = compactObservationJson;
        this.rawObservationJson = rawObservationJson;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }
}
