package com.hh.agent.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.hh.agent.BusinessWebActivity;
import com.hh.agent.H5BenchmarkActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = AppForegroundWaitTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppForegroundWaitTest {
    public static final class TestApplication extends Application {
    }

    @Test
    public void waitsForNewForegroundBusinessWebActivity() throws Exception {
        H5BenchmarkActivity current = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();
        BusinessWebActivity next = Robolectric.buildActivity(
                        BusinessWebActivity.class,
                        new Intent(ApplicationProvider.getApplicationContext(), BusinessWebActivity.class)
                                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_MODE_ENABLED, true)
                                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_ASSET_PATH,
                                        "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html"))
                .setup()
                .get();
        AtomicInteger calls = new AtomicInteger();

        Activity result = App.awaitNewForegroundBusinessWebActivity(
                () -> calls.getAndIncrement() < 2 ? current : next,
                current,
                50L,
                0L
        );

        assertSame(next, result);
        assertTrue(calls.get() >= 3);
    }

    @Test
    public void returnsNullWhenNewForegroundBusinessWebActivityDoesNotAppear() throws Exception {
        H5BenchmarkActivity current = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        Activity result = App.awaitNewForegroundBusinessWebActivity(
                () -> current,
                current,
                0L,
                0L
        );

        assertNull(result);
    }

    @Test
    public void acceptsOnlyANewBusinessWebActivityEvenWhenItAppearsAtDeadlineBoundary() throws Exception {
        BusinessWebActivity previous = Robolectric.buildActivity(
                        BusinessWebActivity.class,
                        new Intent(ApplicationProvider.getApplicationContext(), BusinessWebActivity.class)
                                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_MODE_ENABLED, true)
                                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_ASSET_PATH,
                                        "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html"))
                .setup()
                .get();
        BusinessWebActivity next = Robolectric.buildActivity(
                        BusinessWebActivity.class,
                        new Intent(ApplicationProvider.getApplicationContext(), BusinessWebActivity.class)
                                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_MODE_ENABLED, true)
                                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_ASSET_PATH,
                                        "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html"))
                .setup()
                .get();
        AtomicInteger calls = new AtomicInteger();

        Activity result = App.awaitNewForegroundBusinessWebActivity(
                () -> calls.getAndIncrement() == 0 ? previous : next,
                previous,
                50L,
                100L
        );

        assertSame(next, result);
        assertTrue(calls.get() >= 2);
    }
}
