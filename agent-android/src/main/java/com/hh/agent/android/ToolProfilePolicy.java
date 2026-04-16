package com.hh.agent.android;

import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.channel.WebActionToolChannel;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ToolProfilePolicy {

    private static final Set<String> VISUAL_TOOL_CHANNELS = new LinkedHashSet<>(Arrays.asList(
            ViewContextToolChannel.CHANNEL_NAME,
            GestureToolChannel.CHANNEL_NAME,
            WebActionToolChannel.CHANNEL_NAME
    ));

    private final String profile;

    public ToolProfilePolicy(String profile) {
        this.profile = AgentRuntimeProfiles.normalize(profile);
    }

    public String getProfile() {
        return profile;
    }

    public boolean isToolChannelEnabled(String channelName) {
        if (AgentRuntimeProfiles.FULL.equals(profile)) {
            return true;
        }
        if (AgentRuntimeProfiles.VISUAL_ONLY.equals(profile)) {
            return VISUAL_TOOL_CHANNELS.contains(channelName);
        }
        return true;
    }
}
