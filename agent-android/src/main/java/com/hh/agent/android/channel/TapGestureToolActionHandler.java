package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TapGestureToolActionHandler extends AbstractGestureToolActionHandler {

    private static final Pattern BOUNDS_PATTERN =
            Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    @Override
    public String getActionName() {
        return "tap";
    }

    @Override
    public String getActionDescription() {
        return "tap 表示点击页面元素";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
        builder.property("x", ToolSchemaBuilder.integer()
                        .description("点击目标的 X 坐标。仅在 action=tap 时必填。"), false)
                .property("y", ToolSchemaBuilder.integer()
                        .description("点击目标的 Y 坐标。仅在 action=tap 时必填。"), false)
                .property("allowCoordinateFallback", ToolSchemaBuilder.bool()
                        .description("仅用于兼容旧测试或受控回退场景。为 true 时，即使缺少 observation，也允许继续使用裸坐标。默认 false。")
                        .defaultValue(false), false);
    }

    @Override
    public ToolResult execute(JSONObject params, AndroidGestureExecutor executor) {
        populateTapCoordinatesFromObservation(params);
        if (!params.has("x") || !params.has("y")) {
            return buildError("invalid_args", "tap requires integer fields 'x' and 'y'");
        }
        return executeGesture(GestureToolChannel.CHANNEL_NAME, params, executor);
    }

    @Override
    protected ToolResult validate(JSONObject params) {
        if (params.optBoolean("allowCoordinateFallback", false)) {
            return null;
        }

        JSONObject observation;
        try {
            observation = requireObservation(
                    params,
                    "tap requires current-turn observation evidence before execution",
                    "Call android_view_context_tool first, then retry tap with observation.snapshotId.");
        } catch (MissingObservationException exception) {
            return exception.getToolResult();
        }
        return requireObservationSnapshot(
                observation,
                "tap observation must include a non-empty snapshotId",
                "Call android_view_context_tool first, then retry tap with observation.snapshotId.");
    }

    @Override
    protected GestureExecutionResult executeInternal(JSONObject params, AndroidGestureExecutor executor) {
        return executor.tap(params);
    }

    private void populateTapCoordinatesFromObservation(JSONObject params) {
        if (params.has("x") && params.has("y")) {
            return;
        }

        JSONObject observation = params.optJSONObject("observation");
        if (observation == null) {
            return;
        }

        String referencedBounds = observation.optString("referencedBounds", "").trim();
        if (referencedBounds.isEmpty()) {
            return;
        }

        Matcher matcher = BOUNDS_PATTERN.matcher(referencedBounds);
        if (!matcher.matches()) {
            return;
        }

        int left = Integer.parseInt(matcher.group(1));
        int top = Integer.parseInt(matcher.group(2));
        int right = Integer.parseInt(matcher.group(3));
        int bottom = Integer.parseInt(matcher.group(4));
        try {
            params.put("x", (left + right) / 2);
            params.put("y", (top + bottom) / 2);
        } catch (JSONException ignored) {
            // JSONObject accepts integer values in normal paths; leave params unchanged on unexpected failure.
        }
    }
}
