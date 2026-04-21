package com.screenvision.sdk.model;

import java.util.Locale;

public enum CompactSectionType {
    HEADER,
    MODAL,
    PRIMARY,
    BOTTOM_BAR,
    FLOATING,
    DENSE_LIST,
    SECONDARY;

    public String toJsonName() {
        return name().toLowerCase(Locale.US);
    }
}