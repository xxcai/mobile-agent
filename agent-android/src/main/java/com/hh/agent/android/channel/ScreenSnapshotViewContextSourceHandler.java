package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class ScreenSnapshotViewContextSourceHandler extends AbstractViewContextSourceHandler {

    @Override
    public String getSourceName() {
        return ViewContextToolChannel.SOURCE_SCREEN_SNAPSHOT;
    }

    @Override
    public String getSourceDescription() {
        return "screen_snapshot 当前返回 mock screenshot 引用";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
    }

    @Override
    public ToolResult execute(JSONObject params, String targetHint) {
        return buildBaseResult(getSourceName(), targetHint)
                .with("nativeViewXml", (String) null)
                .with("webDom", (String) null)
                .with("screenSnapshot", ViewContextToolChannel.MOCK_SCREEN_SNAPSHOT);
    }
}
