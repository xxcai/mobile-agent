package com.hh.agent.android.gesture;

import org.json.JSONObject;

/**
 * Extensible gesture runtime abstraction.
 * Future real implementations can replace the default mock executor.
 */
public interface AndroidGestureExecutor {

    GestureExecutionResult tap(JSONObject params);

    GestureExecutionResult swipe(JSONObject params);
}
