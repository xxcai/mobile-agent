package com.screenvision.sdk;

public final class CompactAnalysisOptions {
    private final int maxSections;
    private final int maxControls;
    private final int maxTexts;
    private final int maxTextChars;
    private final int denseSectionItemCap;
    private final boolean enableTaskAwareBoost;
    private final boolean enableDebugMetadata;

    private CompactAnalysisOptions(Builder builder) {
        this.maxSections = builder.maxSections;
        this.maxControls = builder.maxControls;
        this.maxTexts = builder.maxTexts;
        this.maxTextChars = builder.maxTextChars;
        this.denseSectionItemCap = builder.denseSectionItemCap;
        this.enableTaskAwareBoost = builder.enableTaskAwareBoost;
        this.enableDebugMetadata = builder.enableDebugMetadata;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public int getMaxSections() {
        return maxSections;
    }

    public int getMaxControls() {
        return maxControls;
    }

    public int getMaxTexts() {
        return maxTexts;
    }

    public int getMaxTextChars() {
        return maxTextChars;
    }

    public int getDenseSectionItemCap() {
        return denseSectionItemCap;
    }

    public boolean isEnableTaskAwareBoost() {
        return enableTaskAwareBoost;
    }

    public boolean isEnableDebugMetadata() {
        return enableDebugMetadata;
    }

    public static final class Builder {
        private int maxSections = 6;
        private int maxControls = 28;
        private int maxTexts = 36;
        private int maxTextChars = 800;
        private int denseSectionItemCap = 3;
        private boolean enableTaskAwareBoost = true;
        private boolean enableDebugMetadata = false;

        private Builder() {
        }

        public Builder setMaxSections(int maxSections) {
            this.maxSections = Math.max(1, maxSections);
            return this;
        }

        public Builder setMaxControls(int maxControls) {
            this.maxControls = Math.max(1, maxControls);
            return this;
        }

        public Builder setMaxTexts(int maxTexts) {
            this.maxTexts = Math.max(1, maxTexts);
            return this;
        }

        public Builder setMaxTextChars(int maxTextChars) {
            this.maxTextChars = Math.max(64, maxTextChars);
            return this;
        }

        public Builder setDenseSectionItemCap(int denseSectionItemCap) {
            this.denseSectionItemCap = Math.max(1, denseSectionItemCap);
            return this;
        }

        public Builder setEnableTaskAwareBoost(boolean enableTaskAwareBoost) {
            this.enableTaskAwareBoost = enableTaskAwareBoost;
            return this;
        }

        public Builder setEnableDebugMetadata(boolean enableDebugMetadata) {
            this.enableDebugMetadata = enableDebugMetadata;
            return this;
        }

        public CompactAnalysisOptions build() {
            return new CompactAnalysisOptions(this);
        }
    }
}
