package com.hh.agent.android.viewcontext;

import com.hh.agent.core.tool.ToolResult;

/**
 * Host-process-only view context provider used in Step 02 before accessibility support.
 */
public final class ViewContextSnapshotProvider {

    private static final String INTERACTION_DOMAIN_NATIVE = "native";
    private static final String SOURCE_NATIVE_XML = "native_xml";
    private static final String OBSERVATION_SCOPE_CURRENT_TURN = "current_turn";

    private ViewContextSnapshotProvider() {
    }

    public static ToolResult getCurrentNativeViewSnapshot(String targetHint) {
        InProcessViewHierarchyDumper.DumpResult dumpResult =
                InProcessViewHierarchyDumper.dumpCurrentHierarchy(targetHint);
        if (!dumpResult.success) {
            return ToolResult.error("view_context_unavailable", dumpResult.errorMessage);
        }

        ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.createSnapshot(
                dumpResult.activityClassName,
                SOURCE_NATIVE_XML,
                INTERACTION_DOMAIN_NATIVE,
                targetHint,
                dumpResult.xml,
                null
        );

        return ToolResult.success()
                .with("source", SOURCE_NATIVE_XML)
                .with("interactionDomain", INTERACTION_DOMAIN_NATIVE)
                .with("mock", false)
                .with("targetHint", targetHint)
                .with("activityClassName", dumpResult.activityClassName)
                .with("observationMode", "in_process_view_tree")
                .with("snapshotId", snapshot.snapshotId)
                .with("snapshotCreatedAtEpochMs", snapshot.createdAtEpochMs)
                .with("snapshotScope", OBSERVATION_SCOPE_CURRENT_TURN)
                .with("snapshotCurrentTurnOnly", snapshot.currentTurnOnly)
                .with("nativeViewXml", dumpResult.xml)
                .with("webDom", (String) null)
                .with("screenSnapshot", (String) null);
    }
}
