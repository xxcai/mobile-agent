package com.screenvision.sdk;

import com.screenvision.sdk.model.PageAnalysisResult;

public interface AnalyzeCallback {
    void onSuccess(PageAnalysisResult result);

    void onError(Throwable throwable);
}

