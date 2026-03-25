package com.hh.agent.android.gesture;

/**
 * Registry for the active gesture executor.
 */
public final class GestureExecutorRegistry {

    private static AndroidGestureExecutor executor = new InProcessGestureExecutor();

    private GestureExecutorRegistry() {
    }

    public static synchronized void setExecutor(AndroidGestureExecutor newExecutor) {
        if (newExecutor == null) {
            throw new IllegalArgumentException("Gesture executor cannot be null");
        }
        executor = newExecutor;
    }

    public static synchronized AndroidGestureExecutor getExecutor() {
        return executor;
    }
}
