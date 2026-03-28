package com.hh.agent.android.viewcontext;

import android.app.Activity;

import androidx.annotation.Nullable;

import java.util.Map;

/**
 * Resolves runtime source selection from app-provided Activity policy before fallback.
 */
public final class ViewContextSourceSelector {

    private final ActivityViewContextSourcePolicy policy;

    public ViewContextSourceSelector(@Nullable ActivityViewContextSourcePolicy policy) {
        this.policy = policy != null ? policy : ActivityViewContextSourcePolicy.EMPTY;
    }

    public ViewContextSourceSelection selectByPolicy(@Nullable Activity activity) {
        if (activity == null) {
            return ViewContextSourceSelection.noPolicyMatch();
        }
        return selectByPolicy(activity.getClass());
    }

    ViewContextSourceSelection selectByPolicy(@Nullable Class<?> activityClass) {
        if (activityClass == null) {
            return ViewContextSourceSelection.noPolicyMatch();
        }

        Map<String, String> activitySourceMap = policy.getActivitySourceMap();
        if (activitySourceMap == null || activitySourceMap.isEmpty()) {
            return ViewContextSourceSelection.noPolicyMatch();
        }

        Class<?> current = activityClass;
        while (current != null && Activity.class.isAssignableFrom(current)) {
            String source = normalizeSource(activitySourceMap.get(current.getName()));
            if (source != null) {
                return ViewContextSourceSelection.policyMatched(source, current.getName());
            }
            current = current.getSuperclass();
        }
        return ViewContextSourceSelection.noPolicyMatch();
    }

    @Nullable
    private String normalizeSource(@Nullable String source) {
        if (source == null) {
            return null;
        }
        String trimmed = source.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
