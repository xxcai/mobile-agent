package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.ViewContextSnapshotProvider;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class NativeXmlViewContextSourceDefinition extends AbstractViewContextSourceDefinition {

    @Override
    public String getSourceName() {
        return ViewContextToolChannel.SOURCE_NATIVE_XML;
    }

    @Override
    public String getSourceDescription() {
        return "native_xml 返回当前原生界面树";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
    }

    @Override
    public ToolResult execute(JSONObject params, String targetHint) {
        return ViewContextSnapshotProvider.getCurrentNativeViewSnapshot(targetHint)
                .with("channel", ViewContextToolChannel.CHANNEL_NAME);
    }
}
