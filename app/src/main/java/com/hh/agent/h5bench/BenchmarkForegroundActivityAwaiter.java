package com.hh.agent.h5bench;

import android.app.Activity;
import android.os.SystemClock;

import com.hh.agent.BusinessWebActivity;

public class BenchmarkForegroundActivityAwaiter {
    private final ForegroundActivityProvider foregroundActivityProvider;
    private final TimeSource timeSource;
    private final Sleeper sleeper;
    private final long pollIntervalMs;

    public BenchmarkForegroundActivityAwaiter(ForegroundActivityProvider foregroundActivityProvider) {
        this(foregroundActivityProvider, SystemClock::elapsedRealtime, Thread::sleep, 50L);
    }

    BenchmarkForegroundActivityAwaiter(
            ForegroundActivityProvider foregroundActivityProvider,
            TimeSource timeSource,
            Sleeper sleeper,
            long pollIntervalMs) {
        this.foregroundActivityProvider = foregroundActivityProvider;
        this.timeSource = timeSource;
        this.sleeper = sleeper;
        this.pollIntervalMs = Math.max(1L, pollIntervalMs);
    }

    public Activity awaitNewBusinessWebActivity(Activity previousActivity, long timeoutMs)
            throws InterruptedException {
        long deadline = timeSource.nowMs() + Math.max(0L, timeoutMs);
        while (timeSource.nowMs() <= deadline) {
            Activity currentActivity = foregroundActivityProvider.getCurrentForegroundActivity();
            if (isReadyBusinessWebActivity(currentActivity, previousActivity)) {
                return currentActivity;
            }
            sleeper.sleep(pollIntervalMs);
        }
        return null;
    }

    private boolean isReadyBusinessWebActivity(Activity currentActivity, Activity previousActivity) {
        return currentActivity instanceof BusinessWebActivity
                && currentActivity != previousActivity
                && !currentActivity.isFinishing()
                && !currentActivity.isDestroyed()
                && currentActivity.getWindow() != null
                && currentActivity.getWindow().getDecorView() != null
                && currentActivity.getWindow().getDecorView().isAttachedToWindow();
    }

    public interface ForegroundActivityProvider {
        Activity getCurrentForegroundActivity();
    }

    interface TimeSource {
        long nowMs();
    }

    interface Sleeper {
        void sleep(long delayMs) throws InterruptedException;
    }
}
