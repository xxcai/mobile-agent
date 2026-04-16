package com.hh.agent.android;

import org.json.JSONObject;

public final class AgentRuntimeProfileConfig {

    private final String agentProfile;

    private AgentRuntimeProfileConfig(String agentProfile) {
        this.agentProfile = agentProfile;
    }

    public static AgentRuntimeProfileConfig fromConfigJson(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            return new AgentRuntimeProfileConfig(AgentRuntimeProfiles.FULL);
        }
        try {
            JSONObject root = new JSONObject(configJson);
            String agentProfile = AgentRuntimeProfiles.normalize(root.optString("agentProfile", null));
            if (!AgentRuntimeProfiles.FULL.equals(agentProfile) || root.has("agentProfile")) {
                return new AgentRuntimeProfileConfig(agentProfile);
            }

            // Backward compatibility for older configs during migration.
            JSONObject tools = root.optJSONObject("tools");
            String legacyToolsProfile = AgentRuntimeProfiles.normalize(
                    tools != null ? tools.optString("profile", null) : null);

            JSONObject prompt = root.optJSONObject("prompt");
            String legacyPromptProfile = AgentRuntimeProfiles.normalize(
                    prompt != null ? prompt.optString("profile", legacyToolsProfile) : legacyToolsProfile);

            String fallbackProfile = AgentRuntimeProfiles.FULL.equals(legacyToolsProfile)
                    ? legacyPromptProfile
                    : legacyToolsProfile;
            return new AgentRuntimeProfileConfig(fallbackProfile);
        } catch (Exception ignored) {
            return new AgentRuntimeProfileConfig(AgentRuntimeProfiles.FULL);
        }
    }

    public String getAgentProfile() {
        return agentProfile;
    }
}
