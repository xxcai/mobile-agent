package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.ObservationDetailMode;
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
        return "all returns the native observation and can optionally attach mock web_dom or screen_snapshot data";
    }

    @Override
    public void contributeProperties(ToolSchemaBuilder.FunctionToolBuilder builder) {
        builder.property("includeMockWebDom", ToolSchemaBuilder.bool()
                        .description("When source=all, attach mock web DOM output as an extra field.")
                        .defaultValue(false), false)
                .property("includeMockScreenshot", ToolSchemaBuilder.bool()
                        .description("When source=all, attach a mock screenshot reference if no real screenshot is present.")
                        .defaultValue(false), false);
    }

    @Override
    public ToolResult execute(JSONObject params, String targetHint) throws Exception {
        boolean includeMockWebDom = params.optBoolean("includeMockWebDom", false);
        boolean includeMockScreenshot = params.optBoolean("includeMockScreenshot", false);
        ObservationDetailMode detailMode = ObservationDetailMode.fromRaw(params.optString("__detailMode", null));

        ToolResult result = buildBaseResult(getSourceName(), targetHint);
        ToolResult nativeSnapshot = ViewContextSnapshotProvider.getCurrentNativeViewSnapshot(targetHint, true, detailMode);
        JSONObject nativeSnapshotJson = new JSONObject(nativeSnapshot.toJsonString());
        boolean nativeSuccess = nativeSnapshotJson.optBoolean("success", false);

        String realScreenSnapshot = nativeSuccess ? nativeSnapshotJson.optString("screenSnapshot", null) : null;
        if (realScreenSnapshot != null && realScreenSnapshot.trim().isEmpty()) {
            realScreenSnapshot = null;
        }

        return result
                .with("mock", !nativeSuccess)
                .with("activityClassName",
                        nativeSuccess ? nativeSnapshotJson.optString("activityClassName", null) : null)
                .with("observationMode",
                        nativeSuccess ? nativeSnapshotJson.optString("observationMode", null) : null)
                .with("observationDetailMode",
                        nativeSuccess ? nativeSnapshotJson.optString("observationDetailMode", null) : null)
                .with("visualObservationMode",
                        nativeSuccess ? nativeSnapshotJson.optString("visualObservationMode", null) : null)
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
                .withJson("screenVisionCompact",
                        nativeSuccess && nativeSnapshotJson.optJSONObject("screenVisionCompact") != null
                                ? nativeSnapshotJson.optJSONObject("screenVisionCompact").toString()
                                : null)
                .withJson("screenVisionRaw",
                        nativeSuccess && nativeSnapshotJson.optJSONObject("screenVisionRaw") != null
                                ? nativeSnapshotJson.optJSONObject("screenVisionRaw").toString()
                                : null)
                .withJson("hybridObservation",
                        nativeSuccess && nativeSnapshotJson.optJSONObject("hybridObservation") != null
                                ? nativeSnapshotJson.optJSONObject("hybridObservation").toString()
                                : null)
                .with("screenSnapshot",
                        realScreenSnapshot != null
                                ? realScreenSnapshot
                                : (includeMockScreenshot ? ViewContextToolChannel.MOCK_SCREEN_SNAPSHOT : null))
                .with("screenSnapshotWidth",
                        nativeSuccess && !nativeSnapshotJson.isNull("screenSnapshotWidth")
                                ? nativeSnapshotJson.optInt("screenSnapshotWidth")
                                : null)
                .with("screenSnapshotHeight",
                        nativeSuccess && !nativeSnapshotJson.isNull("screenSnapshotHeight")
                                ? nativeSnapshotJson.optInt("screenSnapshotHeight")
                                : null)
                .with("nativeViewUnavailableReason",
                        nativeSuccess ? null : nativeSnapshotJson.optString("message", "unknown"))
                .with("webDom", includeMockWebDom ? ViewContextToolChannel.MOCK_WEB_DOM : null);
    }
}
