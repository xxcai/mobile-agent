package com.screenvision.sdk.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompactListItem {
    private final String id;
    private final String sectionId;
    private final BoundingBox boundingBox;
    private final float importance;
    private final String summaryText;
    private final List<String> textIds;
    private final List<String> controlIds;

    public CompactListItem(
            String id,
            String sectionId,
            BoundingBox boundingBox,
            float importance,
            String summaryText,
            List<String> textIds,
            List<String> controlIds
    ) {
        this.id = id;
        this.sectionId = sectionId == null ? "" : sectionId;
        this.boundingBox = boundingBox;
        this.importance = importance;
        this.summaryText = summaryText == null ? "" : summaryText;
        this.textIds = Collections.unmodifiableList(new ArrayList<>(textIds));
        this.controlIds = Collections.unmodifiableList(new ArrayList<>(controlIds));
    }

    public String getId() {
        return id;
    }

    public String getSectionId() {
        return sectionId;
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

    public List<String> getTextIds() {
        return textIds;
    }

    public List<String> getControlIds() {
        return controlIds;
    }
}