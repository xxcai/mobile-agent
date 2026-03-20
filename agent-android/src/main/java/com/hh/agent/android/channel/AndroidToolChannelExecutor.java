package com.hh.agent.android.channel;

import org.json.JSONObject;

/**
 * Executes a top-level Android tool channel and provides its schema.
 */
public interface AndroidToolChannelExecutor {

    /**
     * Returns the outer tool channel name exposed to the model.
     */
    String getChannelName();

    /**
     * Builds the tool definition object that will be appended to tools.json.
     */
    JSONObject buildToolDefinition() throws Exception;

    /**
     * Executes a channel call using the raw params passed from native.
     */
    String execute(JSONObject params);
}
