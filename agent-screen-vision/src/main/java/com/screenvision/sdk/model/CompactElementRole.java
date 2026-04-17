package com.screenvision.sdk.model;

import java.util.Locale;

public enum CompactElementRole {
    PRIMARY_ACTION,
    SECONDARY_ACTION,
    INPUT,
    NAVIGATION,
    CONTENT,
    SUPPORTING,
    DECORATION;

    public String toJsonName() {
        return name().toLowerCase(Locale.US);
    }
}