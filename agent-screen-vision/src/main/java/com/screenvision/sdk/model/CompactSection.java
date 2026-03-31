package com.screenvision.sdk.model;

public final class CompactSection {
    private final String id;
    private final CompactSectionType type;
    private final BoundingBox boundingBox;
    private final float importance;
    private final String summaryText;
    private final int collapsedItemCount;

    public CompactSection(
            String id,
            CompactSectionType type,
            BoundingBox boundingBox,
            float importance,
            String summaryText,
            int collapsedItemCount
    ) {
        this.id = id;
        this.type = type;
        this.boundingBox = boundingBox;
        this.importance = importance;
        this.summaryText = summaryText == null ? "" : summaryText;
        this.collapsedItemCount = Math.max(0, collapsedItemCount);
    }

    public String getId() {
        return id;
    }

    public CompactSectionType getType() {
        return type;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public float getImportance() {
        return importance;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public int getCollapsedItemCount() {
        return collapsedItemCount;
    }
}