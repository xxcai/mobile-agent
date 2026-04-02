package com.hh.agent.android.route;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RouteOpenerTest {

    @Test
    public void returnsInvalidTargetWhenRequiredFieldsAreMissing() throws Exception {
        RouteOpener opener = new RouteOpener(
                () -> HostForegroundPreparationResult.success(false, true),
                uri -> {
                });

        RouteOpenResult result = opener.open(null);

        JSONObject json = result.toJson();
        assertEquals(false, json.getBoolean("success"));
        assertEquals("invalid_target", json.getString("error"));
    }

    @Test
    public void returnsHostPreparationFailureWhenForegroundIsNotReady() throws Exception {
        RouteOpener opener = new RouteOpener(
                () -> HostForegroundPreparationResult.failure("host_activity_not_stable", true, false),
                uri -> {
                });

        RouteOpenResult result = opener.open(validTarget());

        JSONObject json = result.toJson();
        assertEquals(false, json.getBoolean("success"));
        assertEquals("host_activity_not_stable", json.getString("error"));
        assertEquals(true, json.getJSONObject("meta").getBoolean("containerDismissed"));
        assertEquals(false, json.getJSONObject("meta").getBoolean("hostActivityReady"));
    }

    @Test
    public void returnsOpenUriFailedWhenInvokerThrows() throws Exception {
        RouteOpener opener = new RouteOpener(
                () -> HostForegroundPreparationResult.success(true, true),
                uri -> {
                    throw new IllegalStateException("boom");
                });

        RouteOpenResult result = opener.open(validTarget());

        JSONObject json = result.toJson();
        assertEquals(false, json.getBoolean("success"));
        assertEquals("open_uri_failed", json.getString("error"));
        assertEquals("boom", json.getString("message"));
    }

    @Test
    public void returnsSuccessWhenPreparationAndOpenBothSucceed() throws Exception {
        RouteOpener opener = new RouteOpener(
                () -> HostForegroundPreparationResult.success(true, true),
                uri -> {
                });

        RouteOpenResult result = opener.open(validTarget());

        JSONObject json = result.toJson();
        assertEquals(true, json.getBoolean("success"));
        assertEquals("h5://1001001",
                json.getJSONObject("target").getString("uri"));
        assertEquals(true, json.getJSONObject("meta").getBoolean("containerDismissed"));
        assertEquals(true, json.getJSONObject("meta").getBoolean("hostActivityReady"));
    }

    private RouteTarget validTarget() {
        return new RouteTarget.Builder()
                .targetType(RouteHint.TARGET_TYPE_WECODE)
                .uri("h5://1001001")
                .title("费控报销")
                .build();
    }
}
