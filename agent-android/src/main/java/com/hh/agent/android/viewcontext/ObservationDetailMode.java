package com.hh.agent.android.viewcontext;

import androidx.annotation.Nullable;

public enum ObservationDetailMode {
    DISCOVERY("discovery"),
    FOLLOW_UP("follow_up"),
    READOUT("readout");

    private final String wireValue;

    ObservationDetailMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ObservationDetailMode fromRaw(@Nullable String rawValue) {
        if (rawValue == null) {
            return DISCOVERY;
        }
        String normalized = rawValue.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return DISCOVERY;
        }
        if ("follow_up".equals(normalized) || "follow-up".equals(normalized) || "followup".equals(normalized)) {
            return FOLLOW_UP;
        }
        if ("readout".equals(normalized) || "read_out".equals(normalized) || "read-out".equals(normalized)) {
            return READOUT;
        }
        return DISCOVERY;
    }
}
