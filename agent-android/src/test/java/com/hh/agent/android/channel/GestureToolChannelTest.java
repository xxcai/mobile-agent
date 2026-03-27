package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.gesture.GestureExecutorRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GestureToolChannelTest {

    private final GestureToolChannel channel = new GestureToolChannel();
    private final AndroidGestureExecutor originalExecutor = GestureExecutorRegistry.getExecutor();

    @After
    public void tearDown() {
        GestureExecutorRegistry.setExecutor(originalExecutor);
    }

    @Test
    public void buildToolDefinitionAggregatesActionSpecificProperties() throws Exception {
        JSONObject schema = channel.buildToolDefinition();
        JSONObject properties = schema.getJSONObject("function")
                .getJSONObject("parameters")
                .getJSONObject("properties");

        assertTrue(properties.has("x"));
        assertTrue(properties.has("y"));
        assertTrue(properties.has("direction"));
        assertTrue(properties.has("amount"));
        assertTrue(properties.has("duration"));
        assertTrue(properties.has("allowCoordinateFallback"));

        JSONObject action = properties.getJSONObject("action");
        assertEquals(2, action.getJSONArray("enum").length());
        assertEquals("tap", action.getJSONArray("enum").getString(0));
        assertEquals("swipe", action.getJSONArray("enum").getString(1));
    }

    @Test
    public void executeRoutesTapThroughActionDefinition() throws Exception {
        RecordingGestureExecutor executor = new RecordingGestureExecutor();
        GestureExecutorRegistry.setExecutor(executor);

        JSONObject params = new JSONObject()
                .put("action", "tap")
                .put("x", 10)
                .put("y", 20)
                .put("observation", new JSONObject().put("snapshotId", "snap-1"));

        JSONObject result = new JSONObject(channel.execute(params).toJsonString());
        assertTrue(result.getBoolean("success"));
        assertEquals("tap", result.getString("action"));
        assertEquals("tap", executor.lastAction);
        assertEquals(10, executor.lastParams.getInt("x"));
        assertEquals(20, executor.lastParams.getInt("y"));
    }

    @Test
    public void executeDerivesTapCoordinatesFromObservationBounds() throws Exception {
        RecordingGestureExecutor executor = new RecordingGestureExecutor();
        GestureExecutorRegistry.setExecutor(executor);

        JSONObject params = new JSONObject()
                .put("action", "tap")
                .put("observation", new JSONObject()
                        .put("snapshotId", "snap-1")
                        .put("referencedBounds", "[539,2169][799,2337]")
                        .put("targetDescriptor", "发现标签"));

        JSONObject result = new JSONObject(channel.execute(params).toJsonString());
        assertTrue(result.getBoolean("success"));
        assertEquals("tap", executor.lastAction);
        assertEquals(669, executor.lastParams.getInt("x"));
        assertEquals(2253, executor.lastParams.getInt("y"));
    }

    @Test
    public void executeKeepsObservationErrorWhenTapHasCoordinatesButNoObservation() throws Exception {
        RecordingGestureExecutor executor = new RecordingGestureExecutor();
        GestureExecutorRegistry.setExecutor(executor);

        JSONObject params = new JSONObject()
                .put("action", "tap")
                .put("x", 10)
                .put("y", 20);

        JSONObject result = new JSONObject(channel.execute(params).toJsonString());
        assertFalse(result.getBoolean("success"));
        assertEquals("missing_view_context_observation", result.getString("error"));
        assertEquals("", executor.lastAction);
    }

    @Test
    public void executeReturnsSwipeObservationErrorBeforeExecutorCall() throws Exception {
        RecordingGestureExecutor executor = new RecordingGestureExecutor();
        GestureExecutorRegistry.setExecutor(executor);

        JSONObject params = new JSONObject()
                .put("action", "swipe")
                .put("direction", "down")
                .put("observation", new JSONObject().put("snapshotId", "snap-1"));

        JSONObject result = new JSONObject(channel.execute(params).toJsonString());
        assertFalse(result.getBoolean("success"));
        assertEquals("missing_scroll_container_bounds", result.getString("error"));
        assertEquals("", executor.lastAction);
    }

    private static final class RecordingGestureExecutor implements AndroidGestureExecutor {
        private String lastAction = "";
        private JSONObject lastParams;

        @Override
        public GestureExecutionResult tap(JSONObject params) {
            lastAction = "tap";
            lastParams = params;
            return GestureExecutionResult.success("tap", false, lastParams);
        }

        @Override
        public GestureExecutionResult swipe(JSONObject params) {
            lastAction = "swipe";
            lastParams = params;
            return GestureExecutionResult.success("swipe", false, lastParams);
        }
    }
}
