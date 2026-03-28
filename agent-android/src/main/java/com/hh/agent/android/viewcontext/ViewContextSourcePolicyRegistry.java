package com.hh.agent.android.viewcontext;

import androidx.annotation.Nullable;

/**
 * Stores the active app-provided Activity -> source policy for runtime lookup.
 */
public final class ViewContextSourcePolicyRegistry {

    private static volatile ActivityViewContextSourcePolicy activePolicy =
            ActivityViewContextSourcePolicy.EMPTY;

    private ViewContextSourcePolicyRegistry() {
    }

    public static void setActivePolicy(@Nullable ActivityViewContextSourcePolicy policy) {
        activePolicy = policy != null ? policy : ActivityViewContextSourcePolicy.EMPTY;
    }

    public static ActivityViewContextSourcePolicy getActivePolicy() {
        return activePolicy;
    }
}
