package com.hh.agent.h5bench;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.InputStreamReader;
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
        List<MiniWoBTaskDefinition> tasks = new MiniWoBTaskRegistry(context).loadSuite(
                "workspace/skills/h5_benchmark_runner/baseline-20.json");
        assertEquals(20, tasks.size());
        MiniWoBTaskDefinition first = tasks.get(0);
        assertEquals("click-test-2", first.getTaskId());
        assertTrue(first.getAssetPath().startsWith("workspace/skills/h5_benchmark_runner/miniwob/"));
        for (MiniWoBTaskDefinition task : tasks) {
            assertFalse("Asset path should not contain drag-", task.getAssetPath().contains("drag-"));
            context.getAssets().open(task.getAssetPath()).close();
        }
    }

    @Test
    public void baselineManifestContainsSuiteMetadataAndTaskList() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open("workspace/skills/h5_benchmark_runner/baseline-20.json"))) {
            JsonObject manifest = JsonParser.parseReader(reader).getAsJsonObject();

            assertEquals("miniwob-v0-baseline-20", manifest.get("suiteId").getAsString());
            assertEquals("workspace/skills/h5_benchmark_runner/miniwob", manifest.get("assetBasePath").getAsString());
            assertTrue(manifest.has("tasks"));
            assertTrue(manifest.get("tasks").isJsonArray());
        }
    }

    @Test
    public void normalizeAssetPath_resolvesRelativeAndAbsoluteForms() {
        String basePath = "workspace/skills/h5_benchmark_runner/miniwob";

        assertEquals(
                "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                MiniWoBTaskRegistry.normalizeAssetPath(basePath, "click-test-2.html"));
        assertEquals(
                "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                MiniWoBTaskRegistry.normalizeAssetPath(basePath, "/click-test-2.html"));
        assertEquals(
                "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                MiniWoBTaskRegistry.normalizeAssetPath(
                        basePath,
                        "/workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html"));
    }

    @Test
    public void normalizeAssetPath_rejectsBlankFields() {
        IllegalArgumentException blankAssetPath = assertThrows(
                IllegalArgumentException.class,
                () -> MiniWoBTaskRegistry.normalizeAssetPath(
                        "workspace/skills/h5_benchmark_runner/miniwob",
                        " "));
        assertTrue(blankAssetPath.getMessage().contains("assetPath"));

        IllegalArgumentException blankBasePath = assertThrows(
                IllegalArgumentException.class,
                () -> MiniWoBTaskRegistry.normalizeAssetPath("", "click-test-2.html"));
        assertTrue(blankBasePath.getMessage().contains("assetBasePath"));
    }
}
