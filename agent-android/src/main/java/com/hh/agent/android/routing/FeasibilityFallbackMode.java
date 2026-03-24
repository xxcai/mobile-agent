package com.hh.agent.android.routing;

public enum FeasibilityFallbackMode {
    NO_UI_FALLBACK("no_ui_fallback"),
    UI_FALLBACK_ON_STRUCTURED_FAILURE("ui_fallback_on_structured_failure"),
    DIRECT_UI_PATH("direct_ui_path");

    private final String wireValue;

    FeasibilityFallbackMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
