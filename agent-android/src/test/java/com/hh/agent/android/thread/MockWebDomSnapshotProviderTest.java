package com.hh.agent.android.thread;

import android.app.Activity;

import com.hh.agent.android.viewcontext.MockWebDomSnapshotProvider;
import com.hh.agent.android.viewcontext.StableForegroundActivityProvider;
import com.hh.agent.android.viewcontext.ViewObservationSnapshot;
import com.hh.agent.android.viewcontext.ViewObservationSnapshotRegistry;
import com.hh.agent.android.viewcontext.WebViewFinder;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MockWebDomSnapshotProviderTest {

    @Test
    public void getCurrentWebDomSnapshot_exposesCanonicalFieldsForMockPayload() throws Exception {
        MockWebDomSnapshotProvider provider = new MockWebDomSnapshotProvider(
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return null;
                    }
                },
                new WebViewFinder() {
                    @Override
                    public WebViewFindResult findPrimaryWebView(Activity activity) {
                        return null;
                    }
                },
                new MainThreadRunner(new MainThreadRunner.MainThreadScheduler() {
                    @Override
                    public boolean isMainThread() {
                        return true;
                    }

                    @Override
                    public void post(Runnable runnable) {
                        runnable.run();
                    }
                })
        );

        JSONObject result = new JSONObject(provider.getCurrentWebDomSnapshot("contact").toJsonString());

        assertTrue(result.getBoolean("success"));
        assertTrue(result.has("uiTree"));
        assertTrue(result.has("screenElements"));
        assertTrue(result.has("pageSummary"));
        assertTrue(result.has("quality"));
        assertTrue(result.has("raw"));
        assertEquals("web_dom", result.getJSONObject("uiTree").getString("source"));

        ViewObservationSnapshot latestSnapshot = ViewObservationSnapshotRegistry.getLatestSnapshot();
        assertNotNull(latestSnapshot);
        assertNotNull(latestSnapshot.uiTreeJson);
        assertNotNull(latestSnapshot.screenElementsJson);
        assertNotNull(latestSnapshot.pageSummary);
        assertNotNull(latestSnapshot.qualityJson);
        assertNotNull(latestSnapshot.rawJson);
    }
}
