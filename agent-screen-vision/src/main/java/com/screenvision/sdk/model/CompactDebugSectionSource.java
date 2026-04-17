package com.screenvision.sdk.model;

public final class CompactDebugSectionSource {
    private final String sectionId;
    private final CompactSectionType type;
    private final String source;
    private final BoundingBox boundingBox;

    public CompactDebugSectionSource(String sectionId, CompactSectionType type, String source, BoundingBox boundingBox) {
        this.sectionId = sectionId == null ? "" : sectionId;
        this.type = type == null ? CompactSectionType.SECONDARY : type;
        this.source = source == null ? "" : source;
        this.boundingBox = boundingBox;
    }

    public String getSectionId() {
        return sectionId;
    }

    public CompactSectionType getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }
}
