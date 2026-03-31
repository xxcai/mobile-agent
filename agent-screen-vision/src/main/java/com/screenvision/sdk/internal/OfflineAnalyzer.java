package com.screenvision.sdk.internal;

import android.graphics.Bitmap;

import com.screenvision.sdk.AnalyzeCallback;

public interface OfflineAnalyzer {
    default void analyze(Bitmap bitmap, AnalyzeCallback callback) {
        analyze(bitmap, ElementAggregationMode.DISABLED, callback);
    }

    void analyze(Bitmap bitmap, ElementAggregationMode aggregationMode, AnalyzeCallback callback);

    void close();
}
