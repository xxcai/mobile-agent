package com.hh.agent.android.channel;

import com.hh.agent.android.gesture.AndroidGestureExecutor;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

interface GestureToolActionDefinition {

    String getActionName();

    String getActionDescription();

    void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder);

    ToolResult execute(JSONObject params, AndroidGestureExecutor executor);
}
