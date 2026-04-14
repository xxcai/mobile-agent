package com.hh.agent.android.debug;

public final class BenchmarkTaskContextHolder {

    private static BenchmarkTaskContext current;

    private BenchmarkTaskContextHolder() {
    }

    public static synchronized void setCurrent(BenchmarkTaskContext context) {
        current = context;
    }

    public static synchronized BenchmarkTaskContext consumeCurrent() {
        BenchmarkTaskContext context = current;
        current = null;
        return context;
    }

    public static synchronized void clearCurrent() {
        current = null;
    }
}
