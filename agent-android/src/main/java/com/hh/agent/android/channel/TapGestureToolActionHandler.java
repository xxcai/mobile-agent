package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class TapGestureToolActionHandler extends AbstractGestureToolActionHandler {

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
                    "Call android_view_context_tool with source=native_xml first, then retry tap with observation.snapshotId.");
        } catch (MissingObservationException exception) {
            return exception.getToolResult();
        }
        return requireObservationSnapshot(
                observation,
                "tap observation must include a non-empty snapshotId",
                "Call android_view_context_tool with source=native_xml first, then retry tap with observation.snapshotId.");
    }

    @Override
    protected GestureExecutionResult executeInternal(JSONObject params, AndroidGestureExecutor executor) {
        return executor.tap(params);
    }
}
