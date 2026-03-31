package com.screenvision.sdk.model;

public final class CompactUiElement {
    private final String id;
    private final String sectionId;
    private final String itemId;
    private final UiElementType type;
    private final String label;
    private final BoundingBox boundingBox;
    private final float confidence;
    private final float importance;
    private final CompactElementRole role;

    public CompactUiElement(
            String id,
            String sectionId,
            String itemId,
            UiElementType type,
            String label,
            BoundingBox boundingBox,
            float confidence,
            float importance,
            CompactElementRole role
    ) {
        this.id = id;
        this.sectionId = sectionId;
        this.itemId = itemId == null ? "" : itemId;
        this.type = type;
        this.label = label == null ? "" : label;
        this.boundingBox = boundingBox;
        this.confidence = confidence;
        this.importance = importance;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getItemId() {
        return itemId;
    }

    public UiElementType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public float getConfidence() {
        return confidence;
    }

    public float getImportance() {
        return importance;
    }

    public CompactElementRole getRole() {
        return role;
    }
}