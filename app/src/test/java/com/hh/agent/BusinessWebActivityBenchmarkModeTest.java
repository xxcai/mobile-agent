package com.hh.agent;

import android.app.Application;
import android.content.Intent;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = BusinessWebActivityBenchmarkModeTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BusinessWebActivityBenchmarkModeTest {
    public static final class TestApplication extends Application {
    }

    @Test
    public void benchmarkModeLoadsFullAssetUrlFromSkillFolder() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), BusinessWebActivity.class)
                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_MODE_ENABLED, true)
                .putExtra(BusinessWebActivity.EXTRA_BENCHMARK_ASSET_PATH,
                        "workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html");

        BusinessWebActivity activity = Robolectric.buildActivity(BusinessWebActivity.class, intent)
                .setup()
                .get();

        WebView webView = activity.findViewById(R.id.businessWebView);
        assertEquals(
                "file:///android_asset/workspace/skills/h5_benchmark_runner/miniwob/click-test-2.html",
                shadowOf(webView).getLastLoadedUrl());

        WebSettings settings = webView.getSettings();
        assertTrue(settings.getJavaScriptEnabled());
        assertTrue(settings.getDomStorageEnabled());
    }
}
