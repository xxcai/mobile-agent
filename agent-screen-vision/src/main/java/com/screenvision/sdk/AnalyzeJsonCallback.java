package com.screenvision.sdk;

public interface AnalyzeJsonCallback {
    void onSuccess(String jsonResult);

    void onError(Throwable throwable);
}
