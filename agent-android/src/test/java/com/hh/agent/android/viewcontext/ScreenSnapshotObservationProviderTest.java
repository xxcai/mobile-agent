package com.hh.agent.android.viewcontext;

import android.app.Activity;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScreenSnapshotObservationProviderTest {

    @Test
    public void getCurrentSnapshot_returnsErrorWhenAnalyzerMissing() throws Exception {
        ScreenSnapshotObservationProvider provider = new ScreenSnapshotObservationProvider(
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return new TestActivity();
                    }
                },
                null
        );

        JSONObject result = new JSONObject(provider.getCurrentSnapshot("send").toJsonString());

        assertFalse(result.getBoolean("success"));
        assertEquals("view_context_unavailable", result.getString("error"));
    }

    @Test
    public void getCurrentSnapshot_returnsErrorWhenActivityMissing() throws Exception {
        ScreenSnapshotObservationProvider provider = new ScreenSnapshotObservationProvider(
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return null;
                    }
                },
                new ScreenSnapshotAnalyzer() {
                    @Override
                    public ScreenSnapshotAnalysis analyze(Activity activity, String targetHint) {
                        throw new AssertionError("analyze should not be called when activity is missing");
                    }
                }
        );

        JSONObject result = new JSONObject(provider.getCurrentSnapshot("send").toJsonString());

        assertFalse(result.getBoolean("success"));
        assertEquals("view_context_unavailable", result.getString("error"));
    }

    @Test
    public void getCurrentSnapshot_returnsStructuredPayloadAndStoresSnapshot() throws Exception {
        ScreenSnapshotObservationProvider provider = new ScreenSnapshotObservationProvider(
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return new TestActivity();
                    }
                },
                new ScreenSnapshotAnalyzer() {
                    @Override
                    public ScreenSnapshotAnalysis analyze(Activity activity, String targetHint) {
                        return new ScreenSnapshotAnalysis(
                                activity.getClass().getName(),
                                "screenvision_compact_ocr_ui",
                                "screenvision://capture/test",
                                "{\"summary\":\"Inbox\"}",
                                null,
                                1080,
                                1920
                        );
                    }
                }
        );

        JSONObject result = new JSONObject(provider.getCurrentSnapshot("send button").toJsonString());

        assertTrue(result.getBoolean("success"));
        assertEquals("screen_snapshot", result.getString("source"));
        assertEquals("screenvision_compact_ocr_ui", result.getString("observationMode"));
        assertEquals("screenvision_compact_ocr_ui", result.getString("visualObservationMode"));
        assertEquals("send button", result.getString("targetHint"));
        assertEquals("screenvision://capture/test", result.getString("screenSnapshot"));
        assertEquals("Inbox", result.getJSONObject("screenVisionCompact").getString("summary"));
        assertEquals("screen_only", result.getJSONObject("hybridObservation").getString("mode"));
        assertTrue(result.isNull("screenVisionRaw"));
        assertEquals("screen_snapshot", result.getJSONObject("uiTree").getString("source"));
        assertEquals("Inbox", result.getString("pageSummary"));
        assertEquals("VisualObservationAdapter", result.getJSONObject("quality").getString("adapterName"));
        assertTrue(result.getJSONObject("raw").has("visualObservationJson"));

        ViewObservationSnapshot latest = ViewObservationSnapshotRegistry.getLatestSnapshot();
        assertNotNull(latest);
        assertEquals("screen_snapshot", latest.source);
        assertEquals("{\"summary\":\"Inbox\"}", latest.visualObservationJson);
        assertEquals("screenvision://capture/test", latest.screenSnapshot);
        assertNotNull(latest.hybridObservationJson);
        assertEquals("screen_only", new JSONObject(latest.hybridObservationJson).getString("mode"));
        assertEquals("screen_snapshot", new JSONObject(latest.uiTreeJson).getString("source"));
        assertEquals("Inbox", latest.pageSummary);
        assertEquals("VisualObservationAdapter", new JSONObject(latest.qualityJson).getString("adapterName"));
        assertTrue(new JSONObject(latest.rawJson).has("visualObservationJson"));
    }

    public static class TestActivity extends Activity {
    }
}
