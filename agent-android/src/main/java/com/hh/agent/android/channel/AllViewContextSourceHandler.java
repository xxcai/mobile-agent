package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.ViewContextSnapshotProvider;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

final class AllViewContextSourceHandler extends AbstractViewContextSourceHandler {

    @Override
    public String getSourceName() {
        return ViewContextToolChannel.SOURCE_ALL;
    }

    @Override
    public String getSourceDescription() {
        return "all 是聚合模式，会先返回 native_xml，并按需附带 mock web_dom / screen_snapshot";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
        builder.property("includeMockWebDom", ToolSchemaBuilder.bool()
                        .description("当 source=all 时，是否附带 mock web DOM。")
                        .defaultValue(false), false)
                .property("includeMockScreenshot", ToolSchemaBuilder.bool()
                        .description("当 source=all 时，是否附带 mock screenshot 引用。")
                        .defaultValue(false), false);
    }

    @Override
    public ToolResult execute(JSONObject params, String targetHint) throws Exception {
        boolean includeMockWebDom = params.optBoolean("includeMockWebDom", false);
        boolean includeMockScreenshot = params.optBoolean("includeMockScreenshot", false);

        ToolResult result = buildBaseResult(getSourceName(), targetHint);
        ToolResult nativeSnapshot = ViewContextSnapshotProvider.getCurrentNativeViewSnapshot(targetHint);
        JSONObject nativeSnapshotJson = new JSONObject(nativeSnapshot.toJsonString());
        boolean nativeSuccess = nativeSnapshotJson.optBoolean("success", false);
        return result
                .with("mock", !nativeSuccess)
                .with("activityClassName",
                        nativeSuccess ? nativeSnapshotJson.optString("activityClassName", null) : null)
                .with("observationMode",
                        nativeSuccess ? nativeSnapshotJson.optString("observationMode", null) : null)
                .with("snapshotId",
                        nativeSuccess ? nativeSnapshotJson.optString("snapshotId", null) : null)
                .with("snapshotCreatedAtEpochMs",
                        nativeSuccess ? nativeSnapshotJson.optLong("snapshotCreatedAtEpochMs", 0L) : null)
                .with("snapshotScope",
                        nativeSuccess ? nativeSnapshotJson.optString("snapshotScope", null) : null)
                .with("snapshotCurrentTurnOnly",
                        nativeSuccess ? nativeSnapshotJson.optBoolean("snapshotCurrentTurnOnly", false) : null)
                .with("nativeViewXml",
                        nativeSuccess ? nativeSnapshotJson.optString("nativeViewXml", null) : null)
                .with("nativeViewUnavailableReason",
                        nativeSuccess ? null : nativeSnapshotJson.optString("message", "unknown"))
                .with("webDom", includeMockWebDom ? ViewContextToolChannel.MOCK_WEB_DOM : null)
                .with("screenSnapshot", includeMockScreenshot ? ViewContextToolChannel.MOCK_SCREEN_SNAPSHOT : null);
    }
}
