package com.screenvision.sdk.model;

public final class RecognizedTextBlock {
    private final String text;
    private final BoundingBox boundingBox;
    private final float confidence;

    public RecognizedTextBlock(String text, BoundingBox boundingBox, float confidence) {
        this.text = text;
        this.boundingBox = boundingBox;
        this.confidence = confidence;
    }

    public String getText() {
        return text;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public float getConfidence() {
        return confidence;
    }
}

