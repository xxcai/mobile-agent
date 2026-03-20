package com.hh.agent.android.gesture;

import org.json.JSONObject;

/**
 * Default gesture executor used until real runtime support is plugged in.
 */
public class MockGestureExecutor implements AndroidGestureExecutor {

    @Override
    public GestureExecutionResult tap(JSONObject params) {
        return GestureExecutionResult.success("tap", true, params);
    }

    @Override
    public GestureExecutionResult swipe(JSONObject params) {
        return GestureExecutionResult.success("swipe", true, params);
    }
}
