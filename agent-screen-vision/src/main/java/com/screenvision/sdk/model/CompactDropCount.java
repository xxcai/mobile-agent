package com.screenvision.sdk.model;

public final class CompactDropCount {
    private final String reason;
    private final int count;

    public CompactDropCount(String reason, int count) {
        this.reason = reason == null ? "" : reason;
        this.count = Math.max(0, count);
    }

    public String getReason() {
        return reason;
    }

    public int getCount() {
        return count;
    }
}
