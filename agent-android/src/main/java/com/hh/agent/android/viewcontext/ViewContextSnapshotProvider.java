package com.hh.agent.android.viewcontext;

import com.hh.agent.core.tool.ToolResult;

/**
 * Host-process-only view context provider used in Step 02 before accessibility support.
 */
public final class ViewContextSnapshotProvider {

    private ViewContextSnapshotProvider() {
    }

    public static ToolResult getCurrentNativeViewSnapshot(String targetHint) {
        InProcessViewHierarchyDumper.DumpResult dumpResult =
                InProcessViewHierarchyDumper.dumpCurrentHierarchy(targetHint);
        if (!dumpResult.success) {
            return ToolResult.error("view_context_unavailable", dumpResult.errorMessage);
        }

        return ToolResult.success()
                .with("source", "native_xml")
                .with("mock", false)
                .with("targetHint", targetHint)
                .with("activityClassName", dumpResult.activityClassName)
                .with("observationMode", "in_process_view_tree")
                .with("nativeViewXml", dumpResult.xml)
                .with("webDom", (String) null)
                .with("screenSnapshot", (String) null);
    }
}
