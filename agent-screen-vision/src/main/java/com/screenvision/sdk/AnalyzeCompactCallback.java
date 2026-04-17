package com.screenvision.sdk;

import com.screenvision.sdk.model.CompactPageAnalysisResult;

public interface AnalyzeCompactCallback {
    void onSuccess(CompactPageAnalysisResult result);

    void onError(Throwable throwable);
}