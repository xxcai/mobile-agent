package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.ObservationDetailMode;
import com.hh.agent.android.viewcontext.ViewContextSnapshotProvider;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class NativeXmlViewContextSourceHandler extends AbstractViewContextSourceHandler {

    @Override
    public String getSourceName() {
        return ViewContextToolChannel.SOURCE_NATIVE_XML;
    }

    @Override
    public String getSourceDescription() {
        return "native_xml returns the current native view hierarchy";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
    }

    @Override
    public ToolResult execute(JSONObject params, String targetHint) {
        boolean includeRawFallback = params.optBoolean("includeRawFallback", false);
        ObservationDetailMode detailMode = ObservationDetailMode.fromRaw(params.optString("__detailMode", null));
        return ViewContextSnapshotProvider.getCurrentNativeViewSnapshot(targetHint, includeRawFallback, detailMode)
                .with("channel", ViewContextToolChannel.CHANNEL_NAME);
    }
}
