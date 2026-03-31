package com.hh.agent.android.viewcontext;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.hh.agent.core.tool.ToolResult;

/**
 * Host-process-only view context provider used in Step 02 before accessibility support.
 */
public final class ViewContextSnapshotProvider {

    private static final String SOURCE_NATIVE_XML = "native_xml";
    private static final String OBSERVATION_SCOPE_CURRENT_TURN = "current_turn";

    private ViewContextSnapshotProvider() {
    }

    public static ToolResult getCurrentNativeViewSnapshot(String targetHint) {
        Activity activity = InProcessViewHierarchyDumper.getCurrentStableForegroundActivity();
        if (activity == null) {
            return ToolResult.error("view_context_unavailable", "Foreground activity did not stabilize in time");
        }

        InProcessViewHierarchyDumper.DumpResult dumpResult =
                InProcessViewHierarchyDumper.dumpHierarchy(activity, targetHint);
        if (!dumpResult.success) {
            return ToolResult.error("view_context_unavailable", dumpResult.errorMessage);
        }

        ScreenSnapshotAnalysis visualAnalysis = tryAnalyzeScreenSnapshot(activity, targetHint);
        return buildObservationToolResult(
                SOURCE_NATIVE_XML,
                targetHint,
                dumpResult.activityClassName,
                "in_process_view_tree",
                dumpResult.xml,
                visualAnalysis
        );
    }

    public static ToolResult getCurrentScreenSnapshot(String targetHint) {
        return ScreenSnapshotObservationProvider.createDefault().getCurrentSnapshot(targetHint);
    }

    static ToolResult buildObservationToolResult(String source,
                                                 @Nullable String targetHint,
                                                 String activityClassName,
                                                 String observationMode,
                                                 @Nullable String nativeViewXml,
                                                 @Nullable ScreenSnapshotAnalysis analysis) {
        String visualObservationJson = analysis != null ? analysis.compactObservationJson : null;
        String screenSnapshotRef = analysis != null ? analysis.screenSnapshotRef : null;
        Integer imageWidth = analysis != null ? analysis.imageWidth : null;
        Integer imageHeight = analysis != null ? analysis.imageHeight : null;
        String hybridObservationJson = HybridObservationComposer.compose(
                source,
                activityClassName,
                targetHint,
                nativeViewXml,
                visualObservationJson,
                imageWidth != null ? imageWidth : 0,
                imageHeight != null ? imageHeight : 0
        );

        ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.createSnapshot(
                activityClassName,
                source,
                targetHint,
                nativeViewXml,
                visualObservationJson,
                screenSnapshotRef,
                hybridObservationJson
        );

        return ToolResult.success()
                .with("source", source)
                .with("mock", false)
                .with("targetHint", targetHint)
                .with("activityClassName", activityClassName)
                .with("observationMode", observationMode)
                .with("visualObservationMode", analysis != null ? analysis.observationMode : null)
                .with("snapshotId", snapshot.snapshotId)
                .with("snapshotCreatedAtEpochMs", snapshot.createdAtEpochMs)
                .with("snapshotScope", OBSERVATION_SCOPE_CURRENT_TURN)
                .with("snapshotCurrentTurnOnly", snapshot.currentTurnOnly)
                .with("nativeViewXml", nativeViewXml)
                .with("webDom", (String) null)
                .with("screenSnapshot", screenSnapshotRef)
                .with("screenSnapshotWidth", imageWidth)
                .with("screenSnapshotHeight", imageHeight)
                .withJson("screenVisionCompact", visualObservationJson)
                .withJson("screenVisionRaw", analysis != null ? analysis.rawObservationJson : null)
                .withJson("hybridObservation", hybridObservationJson);
    }

    @Nullable
    private static ScreenSnapshotAnalysis tryAnalyzeScreenSnapshot(Activity activity,
                                                                   @Nullable String targetHint) {
        ScreenSnapshotAnalyzer analyzer = ScreenSnapshotAnalyzerHolder.getInstance().getAnalyzer();
        if (analyzer == null) {
            return null;
        }
        try {
            return analyzer.analyze(activity, targetHint);
        } catch (Throwable ignored) {
            return null;
        }
    }
}