package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

interface ViewContextSourceHandler {

    String getSourceName();

    String getSourceDescription();

    void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder);

    ToolResult execute(JSONObject params, String targetHint) throws Exception;
}
