package com.hh.agent.android;

public final class ToolProfilePolicy {

    private final String profile;

    public ToolProfilePolicy(String profile) {
        this.profile = AgentRuntimeProfiles.normalize(profile);
    }

    public String getProfile() {
        return profile;
    }
}
