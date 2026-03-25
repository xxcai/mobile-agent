package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class WebDomViewContextSourceDefinition extends AbstractViewContextSourceDefinition {

    @Override
    public String getSourceName() {
        return ViewContextToolChannel.SOURCE_WEB_DOM;
    }

    @Override
    public String getSourceDescription() {
        return "web_dom 当前返回 mock web DOM";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
    }

    @Override
    public ToolResult execute(JSONObject params, String targetHint) {
        return buildBaseResult(getSourceName(), targetHint)
                .with("nativeViewXml", (String) null)
                .with("webDom", ViewContextToolChannel.MOCK_WEB_DOM)
                .with("screenSnapshot", (String) null);
    }
}
