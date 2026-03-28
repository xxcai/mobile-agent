package com.hh.agent.android.viewcontext;

import androidx.annotation.Nullable;

/**
 * Result of runtime source selection before fallback execution.
 */
public final class ViewContextSourceSelection {

    public enum Status {
        POLICY_MATCHED,
        FALLBACK_RESOLVED,
        NO_POLICY_MATCH
    }

    private final Status status;
    @Nullable
    private final String source;
    @Nullable
    private final String matchedActivityClassName;

    private ViewContextSourceSelection(Status status,
                                       @Nullable String source,
                                       @Nullable String matchedActivityClassName) {
        this.status = status;
        this.source = source;
        this.matchedActivityClassName = matchedActivityClassName;
    }

    public static ViewContextSourceSelection policyMatched(String source, String matchedActivityClassName) {
        return new ViewContextSourceSelection(Status.POLICY_MATCHED, source, matchedActivityClassName);
    }

    public static ViewContextSourceSelection noPolicyMatch() {
        return new ViewContextSourceSelection(Status.NO_POLICY_MATCH, null, null);
    }

    public static ViewContextSourceSelection fallbackResolved(String source) {
        return new ViewContextSourceSelection(Status.FALLBACK_RESOLVED, source, null);
    }

    public Status getStatus() {
        return status;
    }

    public boolean hasPolicyMatch() {
        return status == Status.POLICY_MATCHED;
    }

    public boolean hasResolvedSource() {
        return source != null && !source.isEmpty();
    }

    @Nullable
    public String getSource() {
        return source;
    }

    @Nullable
    public String getMatchedActivityClassName() {
        return matchedActivityClassName;
    }
}
