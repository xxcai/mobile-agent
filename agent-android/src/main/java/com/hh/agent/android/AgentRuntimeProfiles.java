package com.hh.agent.android;

public final class AgentRuntimeProfiles {

    public static final String FULL = "full";
    public static final String VISUAL_ONLY = "visual_only";

    private AgentRuntimeProfiles() {
    }

    public static String normalize(String rawProfile) {
        if (rawProfile == null) {
            return FULL;
        }
        String normalized = rawProfile.trim();
        if (normalized.isEmpty()) {
            return FULL;
        }
        if (VISUAL_ONLY.equals(normalized)) {
            return normalized;
        }
        return FULL;
    }
}
