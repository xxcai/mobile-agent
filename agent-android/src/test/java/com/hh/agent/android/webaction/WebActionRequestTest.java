package com.hh.agent.android.webaction;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WebActionRequestTest {

    @Test
    public void fromJson_parsesAndNormalizesRefAndScript() throws Exception {
        WebActionRequest request = WebActionRequest.fromJson(new JSONObject()
                .put("action", " eval_js ")
                .put("ref", " node-1 ")
                .put("script", " window.location.href "));

        assertEquals("eval_js", request.action);
        assertEquals("node-1", request.ref);
        assertEquals("window.location.href", request.script);
    }

    @Test
    public void fromJson_convertsBlankOptionalFieldsToNull() throws Exception {
        WebActionRequest request = WebActionRequest.fromJson(new JSONObject()
                .put("action", "click")
                .put("ref", "   ")
                .put("selector", " ")
                .put("text", "")
                .put("script", "  "));

        assertNull(request.ref);
        assertNull(request.selector);
        assertNull(request.text);
        assertNull(request.script);
    }
}
