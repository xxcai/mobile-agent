package com.hh.agent.android.route;

/**
 * Result of URI validation/access policy check.
 */
public final class UriAccessDecision {
    private final boolean allowed;
    private final String reason;

    private UriAccessDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static UriAccessDecision allowed() {
        return new UriAccessDecision(true, null);
    }

    public static UriAccessDecision denied(String reason) {
        return new UriAccessDecision(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
