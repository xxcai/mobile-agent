package com.hh.agent.h5bench;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = H5BenchmarkManifestRepositoryTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class H5BenchmarkManifestRepositoryTest {
    public static final class TestApplication extends android.app.Application {}

    @Test
    public void loadsBaseline20ManifestFromSkillDirectory() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        H5BenchmarkManifest manifest = new H5BenchmarkManifestRepository(context)
                .load("workspace/skills/h5_benchmark_runner/baseline-20.json");

        assertEquals("miniwob-v0-baseline-20", manifest.getSuiteId());
        assertEquals("workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                manifest.getTasks().get(0).getAssetPath());
    }
}
