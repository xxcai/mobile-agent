package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.gesture.GestureExecutionResult;
import com.hh.agent.android.viewcontext.ViewObservationSnapshot;
import com.hh.agent.android.viewcontext.ViewObservationSnapshotRegistry;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

abstract class AbstractGestureToolActionHandler implements GestureToolActionHandler {

    protected final ToolResult buildError(String errorCode, String message) {
        return ToolResult.error(errorCode, message);
    }

    protected final ToolResult executeGesture(String channelName,
                                              JSONObject params,
                                              AndroidGestureExecutor executor) {
        ToolResult validationResult = validate(params);
        if (validationResult != null) {
            return validationResult;
        }

        GestureExecutionResult result = executeInternal(params, executor);
        return result.toToolResult(channelName);
    }

    protected ToolResult validate(JSONObject params) {
        return null;
    }

    protected final JSONObject requireObservation(JSONObject params,
                                                  String missingObservationMessage,
                                                  String missingObservationPrompt) {
        JSONObject observation = params.optJSONObject("observation");
        if (observation == null) {
            throw new MissingObservationException(buildObservationError(
                    "missing_view_context_observation",
                    missingObservationMessage,
                    missingObservationPrompt));
        }
        return observation;
    }

    protected final ToolResult requireObservationSnapshot(JSONObject observation,
                                                          String missingSnapshotMessage,
                                                          String missingSnapshotPrompt) {
        String snapshotId = observation.optString("snapshotId", "").trim();
        if (snapshotId.isEmpty()) {
            return buildObservationError(
                    "missing_view_context_snapshot_id",
                    missingSnapshotMessage,
                    missingSnapshotPrompt);
        }
        return validateSnapshotExists(snapshotId, missingSnapshotPrompt);
    }

    protected final ToolResult validateSnapshotExists(String snapshotId, String promptForModel) {
        ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.findSnapshotById(snapshotId);
        if (snapshot == null) {
            return buildObservationError(
                    "invalid_snapshot_id",
                    "snapshotId '" + snapshotId + "' not found in registry",
                    "The snapshotId is expired or invalid. Call android_view_context_tool to get a fresh observation, then retry with the new snapshotId.");
        }
        return null;
    }

    protected final ToolResult buildObservationError(String errorCode,
                                                     String message,
                                                     String promptForModel) {
        return ToolResult.error(errorCode, message)
                .with("channel", GestureToolChannel.CHANNEL_NAME)
                .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                .with("suggestedSource", "native_xml")
                .with("messageForModel", promptForModel);
    }

    protected abstract GestureExecutionResult executeInternal(JSONObject params,
                                                             AndroidGestureExecutor executor);

    protected static final class MissingObservationException extends RuntimeException {
        private final ToolResult toolResult;

        private MissingObservationException(ToolResult toolResult) {
            this.toolResult = toolResult;
        }

        ToolResult getToolResult() {
            return toolResult;
        }
    }
}

