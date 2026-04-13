package com.hh.agent;

import android.app.Application;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = BusinessWebActivityTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BusinessWebActivityTest {

    public static final class TestApplication extends Application {
    }

    @Test
    public void formatViewContextProbeReport_includesFailureDiagnosticsWhenProbeFails() throws Exception {
        JSONObject result = new JSONObject()
                .put("success", false)
                .put("source", "web_dom")
                .put("interactionDomain", "web")
                .put("selectionStatus", "POLICY_MATCHED")
                .put("activityClassName", BusinessWebActivity.class.getName())
                .put("webViewCandidateCount", 1)
                .put("webViewSelectionReason", "largest_visible_area")
                .put("failureStage", "parse_payload")
                .put("error", "dom_capture_failed")
                .put("message", "Value not-json of type java.lang.String cannot be converted to JSONObject")
                .put("rawJsResult", "\"not-json\"")
                .put("decodedJsResult", "not-json");

        String report = BusinessWebActivity.formatViewContextProbeReport(
                result,
                "debug submit button",
                "business_page_form.html");

        assertTrue(report.contains("actual.failureStage=parse_payload"));
        assertTrue(report.contains("actual.error=dom_capture_failed"));
        assertTrue(report.contains("actual.message=Value not-json of type java.lang.String cannot be converted to JSONObject"));
        assertTrue(report.contains("actual.rawJsResult=\"not-json\""));
        assertTrue(report.contains("actual.decodedJsResult=not-json"));
    }
}
