package com.hh.agent.android.thread;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainThreadRunnerTest {

    @Test
    public void call_runsImmediatelyWhenAlreadyOnMainThread() throws Exception {
        MainThreadRunner runner = new MainThreadRunner(new MainThreadRunner.MainThreadScheduler() {
            @Override
            public boolean isMainThread() {
                return true;
            }

            @Override
            public void post(Runnable runnable) {
                throw new AssertionError("post should not be used when already on main thread");
            }
        });

        String value = runner.call(() -> "ok");

        assertEquals("ok", value);
    }

    @Test
    public void run_postsWhenOffMainThread() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MainThreadRunner runner = new MainThreadRunner(new MainThreadRunner.MainThreadScheduler() {
            @Override
            public boolean isMainThread() {
                return false;
            }

            @Override
            public void post(Runnable runnable) {
                runnable.run();
            }
        });

        runner.run(() -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    public void call_waitsForPostedResultWhenOffMainThread() throws Exception {
        MainThreadRunner runner = new MainThreadRunner(new MainThreadRunner.MainThreadScheduler() {
            @Override
            public boolean isMainThread() {
                return false;
            }

            @Override
            public void post(Runnable runnable) {
                new Thread(runnable).start();
            }
        });

        String value = runner.call(() -> "posted", 1000L);

        assertEquals("posted", value);
    }
}
