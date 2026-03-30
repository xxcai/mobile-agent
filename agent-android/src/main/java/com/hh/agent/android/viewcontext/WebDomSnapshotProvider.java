package com.hh.agent.android.viewcontext;

import com.hh.agent.core.tool.ToolResult;

/**
 * Produces a web-domain observation for the current foreground WebView.
 */
public interface WebDomSnapshotProvider {

    ToolResult getCurrentWebDomSnapshot(String targetHint);
}
