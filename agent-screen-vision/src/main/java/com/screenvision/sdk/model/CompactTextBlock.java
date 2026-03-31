package com.screenvision.sdk.model;

public final class CompactTextBlock {
    private final String id;
    private final String sectionId;
    private final String itemId;
    private final String text;
    private final BoundingBox boundingBox;
    private final float confidence;
    private final float importance;
    private final CompactElementRole role;

    public CompactTextBlock(
            String id,
            String sectionId,
            String itemId,
            String text,
            BoundingBox boundingBox,
            float confidence,
            float importance,
            CompactElementRole role
    ) {
        this.id = id;
        this.sectionId = sectionId;
        this.itemId = itemId == null ? "" : itemId;
        this.text = text == null ? "" : text;
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

    public String getText() {
        return text;
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