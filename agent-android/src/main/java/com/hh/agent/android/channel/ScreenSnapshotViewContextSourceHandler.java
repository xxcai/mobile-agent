package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.ViewContextSnapshotProvider;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class ScreenSnapshotViewContextSourceHandler extends AbstractViewContextSourceHandler {

    @Override
    public String getSourceName() {
        return ViewContextToolChannel.SOURCE_SCREEN_SNAPSHOT;
    }

    @Override
    public String getSourceDescription() {
        return "screen_snapshot returns screenshot-backed OCR and UI analysis when a visual analyzer is installed";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
    }

    @Override
    public ToolResult execute(JSONObject params, String targetHint) {
        return ViewContextSnapshotProvider.getCurrentScreenSnapshot(targetHint)
                .with("channel", ViewContextToolChannel.CHANNEL_NAME);
    }
}
