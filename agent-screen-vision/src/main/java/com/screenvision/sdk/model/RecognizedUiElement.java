package com.screenvision.sdk.model;

public final class RecognizedUiElement {
    private final UiElementType type;
    private final BoundingBox boundingBox;
    private final float confidence;
    private final String label;

    public RecognizedUiElement(UiElementType type, BoundingBox boundingBox, float confidence, String label) {
        this.type = type;
        this.boundingBox = boundingBox;
        this.confidence = confidence;
        this.label = label;
    }

    public UiElementType getType() {
        return type;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getLabel() {
        return label;
    }
}

