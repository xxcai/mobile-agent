package com.hh.agent.android.ui;

/**
 * UI decision for a top-level tool call in the response card.
 */
public final class ToolUiDecision {

    private static final ToolUiDecision HIDDEN = new ToolUiDecision(false, null);

    private final boolean visible;
    private final String displayName;

    private ToolUiDecision(boolean visible, String displayName) {
        this.visible = visible;
        this.displayName = displayName;
    }

    public static ToolUiDecision hidden() {
        return HIDDEN;
    }

    public static ToolUiDecision visible(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return HIDDEN;
        }
        return new ToolUiDecision(true, displayName.trim());
    }

    public boolean isVisible() {
        return visible;
    }

    public String getDisplayName() {
        return displayName;
    }
}
