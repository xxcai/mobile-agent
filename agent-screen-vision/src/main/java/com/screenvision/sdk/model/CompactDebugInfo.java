package com.screenvision.sdk.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompactDebugInfo {
    private final List<CompactDebugSectionSource> sectionSources;
    private final List<CompactDebugRow> rows;
    private final CompactDropSummary dropSummary;

    public CompactDebugInfo(
            List<CompactDebugSectionSource> sectionSources,
            List<CompactDebugRow> rows,
            CompactDropSummary dropSummary
    ) {
        this.sectionSources = Collections.unmodifiableList(new ArrayList<>(sectionSources));
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.dropSummary = dropSummary;
    }

    public List<CompactDebugSectionSource> getSectionSources() {
        return sectionSources;
    }

    public List<CompactDebugRow> getRows() {
        return rows;
    }

    public CompactDropSummary getDropSummary() {
        return dropSummary;
    }
}
