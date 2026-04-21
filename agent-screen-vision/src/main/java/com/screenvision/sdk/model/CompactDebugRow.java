package com.screenvision.sdk.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompactDebugRow {
    private final String sectionId;
    private final String itemId;
    private final int rowIndex;
    private final BoundingBox boundingBox;
    private final String anchorType;
    private final List<String> anchorIds;
    private final boolean collapsed;

    public CompactDebugRow(
            String sectionId,
            String itemId,
            int rowIndex,
            BoundingBox boundingBox,
            String anchorType,
            List<String> anchorIds,
            boolean collapsed
    ) {
        this.sectionId = sectionId == null ? "" : sectionId;
        this.itemId = itemId == null ? "" : itemId;
        this.rowIndex = rowIndex;
        this.boundingBox = boundingBox;
        this.anchorType = anchorType == null ? "" : anchorType;
        this.anchorIds = Collections.unmodifiableList(new ArrayList<>(anchorIds));
        this.collapsed = collapsed;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public String getAnchorType() {
        return anchorType;
    }

    public List<String> getAnchorIds() {
        return anchorIds;
    }

    public boolean isCollapsed() {
        return collapsed;
    }
}
