package com.hh.agent.android.gesture;

/**
 * Registry for the active gesture executor.
 * Step 04 keeps the default mock executor; later steps can install a real runtime executor.
 */
public final class GestureExecutorRegistry {

    private static AndroidGestureExecutor executor = new MockGestureExecutor();

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
