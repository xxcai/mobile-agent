package com.hh.agent.android.gesture;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default gesture executor used until real runtime support is plugged in.
 */
public class MockGestureExecutor implements AndroidGestureExecutor {

    private static final Pattern BOUNDS_PATTERN =
            Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    @Override
    public GestureExecutionResult tap(JSONObject params) {
        try {
            JSONObject payload = new JSONObject(params.toString());
            JSONObject observation = payload.optJSONObject("observation");
            String referencedBounds = observation != null
                    ? observation.optString("referencedBounds", "")
                    : "";

            Point resolvedPoint = resolveTapPoint(referencedBounds,
                    payload.optInt("x"),
                    payload.optInt("y"));
            payload.put("resolvedTapX", resolvedPoint.x);
            payload.put("resolvedTapY", resolvedPoint.y);
            payload.put("tapPointSource", resolvedPoint.source);
            return GestureExecutionResult.success("tap", true, payload);
        } catch (Exception e) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("originalParams", new JSONObject(params.toString()));
                payload.put("tapPointSource", "resolution_error");
                payload.put("tapPointResolutionMessage", e.getMessage());
                return GestureExecutionResult.success("tap", true, payload);
            } catch (Exception ignored) {
                return GestureExecutionResult.success("tap", true, params);
            }
        }
    }

    @Override
    public GestureExecutionResult swipe(JSONObject params) {
        return GestureExecutionResult.success("swipe", true, params);
    }

    private Point resolveTapPoint(String referencedBounds, int fallbackX, int fallbackY) {
        if (referencedBounds != null && !referencedBounds.trim().isEmpty()) {
            Matcher matcher = BOUNDS_PATTERN.matcher(referencedBounds.trim());
            if (matcher.matches()) {
                int left = Integer.parseInt(matcher.group(1));
                int top = Integer.parseInt(matcher.group(2));
                int right = Integer.parseInt(matcher.group(3));
                int bottom = Integer.parseInt(matcher.group(4));
                int centerX = (left + right) / 2;
                int centerY = (top + bottom) / 2;
                return new Point(centerX, centerY, "observation_bounds");
            }
        }
        return new Point(fallbackX, fallbackY, "fallback_coordinates");
    }

    private static final class Point {
        private final int x;
        private final int y;
        private final String source;

        private Point(int x, int y, String source) {
            this.x = x;
            this.y = y;
            this.source = source;
        }
    }
}
