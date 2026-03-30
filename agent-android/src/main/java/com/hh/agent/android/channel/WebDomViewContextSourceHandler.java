package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.MockWebDomSnapshotProvider;
import com.hh.agent.android.viewcontext.WebDomSnapshotProvider;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class WebDomViewContextSourceHandler extends AbstractViewContextSourceHandler {

    private final WebDomSnapshotProvider snapshotProvider;

    WebDomViewContextSourceHandler() {
        this(MockWebDomSnapshotProvider.createDefault());
    }

    WebDomViewContextSourceHandler(WebDomSnapshotProvider snapshotProvider) {
        this.snapshotProvider = snapshotProvider;
    }

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
        return snapshotProvider.getCurrentWebDomSnapshot(targetHint);
    }
}
