package com.screenvision.sdk.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompactDropSummary {
    private final List<CompactDropCount> texts;
    private final List<CompactDropCount> controls;

    public CompactDropSummary(List<CompactDropCount> texts, List<CompactDropCount> controls) {
        this.texts = Collections.unmodifiableList(new ArrayList<>(texts));
        this.controls = Collections.unmodifiableList(new ArrayList<>(controls));
    }

    public List<CompactDropCount> getTexts() {
        return texts;
    }

    public List<CompactDropCount> getControls() {
        return controls;
    }
}
