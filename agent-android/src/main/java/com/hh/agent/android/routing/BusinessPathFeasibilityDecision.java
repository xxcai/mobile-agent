package com.hh.agent.android.routing;

public final class BusinessPathFeasibilityDecision {

    private final boolean businessPathFeasible;
    private final String entryFunction;
    private final FeasibilityFallbackMode fallbackMode;
    private final String reason;

    private BusinessPathFeasibilityDecision(boolean businessPathFeasible,
                                            String entryFunction,
                                            FeasibilityFallbackMode fallbackMode,
                                            String reason) {
        this.businessPathFeasible = businessPathFeasible;
        this.entryFunction = normalizeEntryFunction(entryFunction);
        this.fallbackMode = fallbackMode != null
                ? fallbackMode
                : FeasibilityFallbackMode.DIRECT_UI_PATH;
        this.reason = reason != null ? reason.trim() : "";
    }

    public static BusinessPathFeasibilityDecision businessFirst(String entryFunction,
                                                                FeasibilityFallbackMode fallbackMode,
                                                                String reason) {
        return new BusinessPathFeasibilityDecision(true, entryFunction, fallbackMode, reason);
    }

    public static BusinessPathFeasibilityDecision directUi(String reason) {
        return new BusinessPathFeasibilityDecision(false, "none",
                FeasibilityFallbackMode.DIRECT_UI_PATH, reason);
    }

    public boolean isBusinessPathFeasible() {
        return businessPathFeasible;
    }

    public String getEntryFunction() {
        return entryFunction;
    }

    public FeasibilityFallbackMode getFallbackMode() {
        return fallbackMode;
    }

    public String getReason() {
        return reason;
    }

    public String toLogString() {
        return "business_path_feasible=" + businessPathFeasible
                + " entry_function=" + entryFunction
                + " fallback_mode=" + fallbackMode.getWireValue()
                + " reason=" + reason;
    }

    private static String normalizeEntryFunction(String entryFunction) {
        if (entryFunction == null) {
            return "none";
        }
        String trimmed = entryFunction.trim();
        return trimmed.isEmpty() ? "none" : trimmed;
    }
}
