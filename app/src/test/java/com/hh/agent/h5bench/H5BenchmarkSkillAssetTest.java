package com.hh.agent.h5bench;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = H5BenchmarkSkillAssetTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class H5BenchmarkSkillAssetTest {
    public static final class TestApplication extends android.app.Application {}

    @Test
    public void skillGuidesDialogTriggerThroughBenchmarkShortcut() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String skill = readUtf8(context, "workspace/skills/h5_benchmark_runner/SKILL.md");

        assertTrue(skill.contains("h5_benchmark_runner"));
        assertTrue(skill.contains("H5基准测试"));
        assertTrue(skill.contains("start_h5_benchmark"));
        assertTrue(skill.contains("H5BenchmarkActivity"));
        assertTrue(skill.contains("wrong_page"));
        assertTrue(skill.contains("already_running"));
    }

    private String readUtf8(Context context, String assetPath) throws Exception {
        try (InputStream inputStream = context.getAssets().open(assetPath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
