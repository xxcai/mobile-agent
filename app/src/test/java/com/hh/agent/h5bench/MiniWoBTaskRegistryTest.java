package com.hh.agent.h5bench;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = MiniWoBTaskRegistryTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class MiniWoBTaskRegistryTest {
    public static final class TestApplication extends android.app.Application {}
    @Test
    public void testLoadBaselineSuite() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        List<MiniWoBTaskDefinition> tasks = new MiniWoBTaskRegistry(context)
                .loadSuite("workspace/skills/h5_benchmark_runner/baseline-20.json");
        assertEquals(20, tasks.size());
        MiniWoBTaskDefinition first = tasks.get(0);
        assertEquals("click-test-2", first.getTaskId());
        assertTrue(first.getAssetPath().startsWith("workspace/skills/h5_benchmark_runner/miniwob/"));
        for (MiniWoBTaskDefinition task : tasks) {
            assertFalse("Asset path should not contain drag-", task.getAssetPath().contains("drag-"));
            context.getAssets().open(task.getAssetPath()).close();
        }
    }
}
