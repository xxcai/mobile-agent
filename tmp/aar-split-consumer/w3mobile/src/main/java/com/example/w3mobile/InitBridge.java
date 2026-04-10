package com.example.w3mobile;

import android.content.Context;
import com.hh.agent.android.AgentInitializer;

public final class InitBridge {
    private InitBridge() {}

    public static void touch(Context context) {
        Class<?> clazz = AgentInitializer.class;
        if (clazz == null || context == null) {
            throw new IllegalStateException("unreachable");
        }
    }
}
