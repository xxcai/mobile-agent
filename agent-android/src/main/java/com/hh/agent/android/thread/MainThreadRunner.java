package com.hh.agent.android.thread;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small utility for scheduling work onto the Android main thread with an optional blocking wait.
 */
public class MainThreadRunner {

    private static final long DEFAULT_TIMEOUT_MS = 1500L;

    private final MainThreadScheduler scheduler;

    public MainThreadRunner() {
        this(new AndroidMainThreadScheduler(new Handler(Looper.getMainLooper()), Looper.getMainLooper()));
    }

    @VisibleForTesting
    MainThreadRunner(MainThreadScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void run(Runnable task) {
        if (scheduler.isMainThread()) {
            task.run();
            return;
        }
        scheduler.post(task);
    }

    public <T> T call(MainThreadCallable<T> callable) throws Exception {
        return call(callable, DEFAULT_TIMEOUT_MS);
    }

    public <T> T call(MainThreadCallable<T> callable, long timeoutMs) throws Exception {
        if (scheduler.isMainThread()) {
            return callable.call();
        }

        AtomicReference<T> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.post(() -> {
            try {
                resultRef.set(callable.call());
            } catch (Exception exception) {
                errorRef.set(exception);
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!completed) {
            throw new MainThreadTimeoutException("Main-thread task timed out after " + timeoutMs + " ms");
        }
        Exception error = errorRef.get();
        if (error != null) {
            throw error;
        }
        return resultRef.get();
    }

    public interface MainThreadCallable<T> {
        T call() throws Exception;
    }

    interface MainThreadScheduler {
        boolean isMainThread();

        void post(Runnable runnable);
    }

    private static final class AndroidMainThreadScheduler implements MainThreadScheduler {

        private final Handler handler;
        private final Looper mainLooper;

        private AndroidMainThreadScheduler(Handler handler, Looper mainLooper) {
            this.handler = handler;
            this.mainLooper = mainLooper;
        }

        @Override
        public boolean isMainThread() {
            return Looper.myLooper() == mainLooper;
        }

        @Override
        public void post(Runnable runnable) {
            handler.post(runnable);
        }
    }

    public static final class MainThreadTimeoutException extends Exception {
        public MainThreadTimeoutException(String message) {
            super(message);
        }
    }
}
