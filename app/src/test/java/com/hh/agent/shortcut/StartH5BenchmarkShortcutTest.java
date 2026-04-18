package com.hh.agent.shortcut;

import androidx.test.core.app.ApplicationProvider;

import com.hh.agent.H5BenchmarkActivity;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.h5bench.H5BenchmarkHost;
import com.hh.agent.h5bench.H5BenchmarkRunState;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = StartH5BenchmarkShortcutTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StartH5BenchmarkShortcutTest {

    public static final class TestApplication extends android.app.Application {
    }

    @Test
    public void returnsWrongPageWhenForegroundIsNotH5BenchmarkActivity() throws Exception {
        StartH5BenchmarkShortcut shortcut = new StartH5BenchmarkShortcut(
                () -> null,
                () -> null);

        JSONObject result = execute(shortcut, new JSONObject());

        assertFalse(result.getBoolean("success"));
        assertEquals("wrong_page", result.getString("error"));
        assertEquals("wrong_page", result.getString("code"));
    }

    @Test
    public void returnsAlreadyRunningWhenBenchmarkHostIsBusy() throws Exception {
        H5BenchmarkHost host = new H5BenchmarkHost(ignored -> { });
        host.start();
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        StartH5BenchmarkShortcut shortcut = new StartH5BenchmarkShortcut(
                () -> activity,
                () -> host);

        JSONObject result = execute(shortcut, new JSONObject());

        assertFalse(result.getBoolean("success"));
        assertEquals("already_running", result.getString("error"));
        assertEquals("already_running", result.getString("code"));
        assertEquals(H5BenchmarkRunState.STARTING.name(), result.getString("state"));
    }

    @Test
    public void returnsStartedWhenHostAcceptsRun() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        H5BenchmarkHost host = new H5BenchmarkHost(ignored -> starts.incrementAndGet());
        H5BenchmarkActivity activity = Robolectric.buildActivity(H5BenchmarkActivity.class).setup().get();

        StartH5BenchmarkShortcut shortcut = new StartH5BenchmarkShortcut(
                () -> activity,
                () -> host);

        JSONObject result = execute(shortcut, new JSONObject());

        assertTrue(result.getBoolean("success"));
        assertEquals("started", result.getString("code"));
        assertEquals(H5BenchmarkRunState.STARTING.name(), result.getString("state"));
        assertEquals(1, starts.get());
    }

    @Test
    public void appShortcutProviderRegistersStartH5BenchmarkShortcut() {
        List<ShortcutExecutor> shortcuts = AppShortcutProvider.createShortcuts(
                ApplicationProvider.getApplicationContext());

        ShortcutExecutor match = null;
        for (ShortcutExecutor shortcut : shortcuts) {
            if ("start_h5_benchmark".equals(shortcut.getDefinition().getName())) {
                match = shortcut;
                break;
            }
        }

        assertNotNull(match);
    }

    private JSONObject execute(StartH5BenchmarkShortcut shortcut, JSONObject args) throws Exception {
        return new JSONObject(shortcut.execute(args).toJsonString());
    }
}
