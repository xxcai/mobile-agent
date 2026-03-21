package com.hh.agent.android.ui;

/**
 * UI decision for a top-level tool call in the response card.
 */
public final class ToolUiDecision {

    private static final ToolUiDecision HIDDEN = new ToolUiDecision(false, null, null);

    private final boolean visible;
    private final String title;
    private final String description;

    private ToolUiDecision(boolean visible, String title, String description) {
        this.visible = visible;
        this.title = title;
        this.description = description;
    }

    public static ToolUiDecision hidden() {
        return HIDDEN;
    }

    public static ToolUiDecision visible(String title, String description) {
        if (title == null || title.trim().isEmpty()) {
            return HIDDEN;
        }
        String normalizedDescription = null;
        if (description != null && !description.trim().isEmpty()) {
            normalizedDescription = description.trim();
        }
        return new ToolUiDecision(true, title.trim(), normalizedDescription);
    }

    public boolean isVisible() {
        return visible;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
