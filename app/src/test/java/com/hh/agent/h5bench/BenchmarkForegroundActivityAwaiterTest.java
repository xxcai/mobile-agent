package com.hh.agent.h5bench;

import android.app.Activity;
import android.app.Application;

import com.hh.agent.BusinessWebActivity;
import com.hh.agent.H5BenchmarkActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = BenchmarkForegroundActivityAwaiterTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BenchmarkForegroundActivityAwaiterTest {
    public static final class TestApplication extends Application {
    }

    @Test
    public void waitsForNewBusinessWebActivityInsteadOfReturningCurrentBenchmarkScreen() throws Exception {
        H5BenchmarkActivity currentBenchmarkActivity =
                Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();
        BusinessWebActivity businessWebActivity =
                Robolectric.buildActivity(BusinessWebActivity.class).setup().get();
        FakeForegroundActivityProvider provider = new FakeForegroundActivityProvider(
                currentBenchmarkActivity,
                currentBenchmarkActivity,
                businessWebActivity);
        FakeTimeSource timeSource = new FakeTimeSource();

        BenchmarkForegroundActivityAwaiter awaiter = new BenchmarkForegroundActivityAwaiter(
                provider,
                timeSource,
                timeSource::advanceBy,
                50L);

        Activity resolved = awaiter.awaitNewBusinessWebActivity(currentBenchmarkActivity, 500L);

        assertSame(businessWebActivity, resolved);
    }

    @Test
    public void returnsNullWhenBusinessWebActivityNeverBecomesForeground() throws Exception {
        H5BenchmarkActivity currentBenchmarkActivity =
                Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();
        FakeForegroundActivityProvider provider = new FakeForegroundActivityProvider(
                currentBenchmarkActivity,
                currentBenchmarkActivity,
                currentBenchmarkActivity);
        FakeTimeSource timeSource = new FakeTimeSource();

        BenchmarkForegroundActivityAwaiter awaiter = new BenchmarkForegroundActivityAwaiter(
                provider,
                timeSource,
                timeSource::advanceBy,
                50L);

        Activity resolved = awaiter.awaitNewBusinessWebActivity(currentBenchmarkActivity, 120L);

        assertNull(resolved);
    }

    private static final class FakeForegroundActivityProvider
            implements BenchmarkForegroundActivityAwaiter.ForegroundActivityProvider {
        private final List<Activity> activities;
        private int index;

        private FakeForegroundActivityProvider(Activity... activities) {
            this.activities = new ArrayList<>(Arrays.asList(activities));
        }

        @Override
        public Activity getCurrentForegroundActivity() {
            if (activities.isEmpty()) {
                return null;
            }
            int currentIndex = Math.min(index, activities.size() - 1);
            Activity activity = activities.get(currentIndex);
            if (index < activities.size() - 1) {
                index++;
            }
            return activity;
        }
    }

    private static final class FakeTimeSource implements BenchmarkForegroundActivityAwaiter.TimeSource {
        private long nowMs;

        @Override
        public long nowMs() {
            return nowMs;
        }

        void advanceBy(long delayMs) {
            nowMs += delayMs;
        }
    }
}
