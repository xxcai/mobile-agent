package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class SwipeGestureToolActionHandler extends AbstractGestureToolActionHandler {

    @Override
    public String getActionName() {
        return "swipe";
    }

    @Override
    public String getActionDescription() {
        return "swipe: scroll the current page";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
        builder.property("direction", ToolSchemaBuilder.string()
                        .description("Swipe direction")
                        .enumValues("down", "up"), false)
                .property("amount", ToolSchemaBuilder.string()
                        .description("Swipe distance")
                        .enumValues("small", "medium", "large", "one_screen")
                        .defaultValue("medium"), false)
                .property("duration", ToolSchemaBuilder.integer()
                        .description("Post-swipe settle time in ms"), false);
    }

    @Override
    public ToolResult execute(JSONObject params, AndroidGestureExecutor executor) {
        if (!params.has("direction")) {
            return buildError("invalid_args", "swipe requires 'direction'");
        }
        return executeGesture(GestureToolChannel.CHANNEL_NAME, params, executor);
    }

    @Override
    protected ToolResult validate(JSONObject params) {
        JSONObject observation;
        try {
            observation = requireObservation(
                    params,
                    "swipe requires current-turn observation evidence for the target scroll container",
                    "Call android_view_context_tool first, then retry swipe with observation.snapshotId and observation.referencedBounds for the target container.");
        } catch (MissingObservationException exception) {
            return exception.getToolResult();
        }

        ToolResult snapshotValidationResult = requireObservationSnapshot(
                observation,
                "swipe observation must include a non-empty snapshotId",
                "Call android_view_context_tool first, then retry swipe with observation.snapshotId and observation.referencedBounds for the target container.");
        if (snapshotValidationResult != null) {
            return snapshotValidationResult;
        }

        String referencedBounds = observation.optString("referencedBounds", "").trim();
        if (referencedBounds.isEmpty()) {
            return buildObservationError(
                    "missing_scroll_container_bounds",
                    "swipe observation must include referencedBounds for the target scroll container",
                    "Select the target scroll container from the latest android_view_context_tool result, then retry swipe with observation.referencedBounds for that container.");
        }
        return null;
    }

    @Override
    protected GestureExecutionResult executeInternal(JSONObject params, AndroidGestureExecutor executor) {
        return executor.swipe(params);
    }
}