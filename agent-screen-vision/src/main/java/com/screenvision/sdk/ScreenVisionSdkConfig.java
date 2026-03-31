package com.screenvision.sdk;

public final class ScreenVisionSdkConfig {
    private final RecognitionLanguage recognitionLanguage;
    private final RecognitionMode recognitionMode;
    private final float uiConfidenceThreshold;
    private final int maxImageSidePx;

    private ScreenVisionSdkConfig(Builder builder) {
        this.recognitionLanguage = builder.recognitionLanguage;
        this.recognitionMode = builder.recognitionMode;
        this.uiConfidenceThreshold = builder.uiConfidenceThreshold;
        this.maxImageSidePx = builder.maxImageSidePx;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public RecognitionLanguage getRecognitionLanguage() {
        return recognitionLanguage;
    }

    public RecognitionMode getRecognitionMode() {
        return recognitionMode;
    }

    public float getUiConfidenceThreshold() {
        return uiConfidenceThreshold;
    }

    public int getMaxImageSidePx() {
        return maxImageSidePx;
    }

    public static final class Builder {
        private RecognitionLanguage recognitionLanguage = RecognitionLanguage.CHINESE_SIMPLIFIED;
        private RecognitionMode recognitionMode = RecognitionMode.TEXT_AND_UI;
        private float uiConfidenceThreshold = 0.32f;
        private int maxImageSidePx = 1920;

        private Builder() {
        }

        public Builder setRecognitionLanguage(RecognitionLanguage recognitionLanguage) {
            this.recognitionLanguage = recognitionLanguage == null
                    ? RecognitionLanguage.CHINESE_SIMPLIFIED
                    : recognitionLanguage;
            return this;
        }

        public Builder setRecognitionMode(RecognitionMode recognitionMode) {
            this.recognitionMode = recognitionMode == null
                    ? RecognitionMode.TEXT_AND_UI
                    : recognitionMode;
            return this;
        }

        public Builder setUiConfidenceThreshold(float uiConfidenceThreshold) {
            this.uiConfidenceThreshold = Math.max(0f, Math.min(1f, uiConfidenceThreshold));
            return this;
        }

        public Builder setMaxImageSidePx(int maxImageSidePx) {
            this.maxImageSidePx = Math.max(256, maxImageSidePx);
            return this;
        }

        public ScreenVisionSdkConfig build() {
            return new ScreenVisionSdkConfig(this);
        }
    }
}
