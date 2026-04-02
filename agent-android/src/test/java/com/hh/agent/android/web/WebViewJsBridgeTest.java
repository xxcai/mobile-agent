package com.hh.agent.android.web;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WebViewJsBridgeTest {

    @Test
    public void decodeJsResult_unescapesJsonEncodedString() throws Exception {
        String decoded = WebViewJsBridge.decodeJsResult("\"{\\\"ok\\\":true,\\\"value\\\":\\\"hello\\\"}\"");

        assertEquals("{\"ok\":true,\"value\":\"hello\"}", decoded);
    }

    @Test
    public void parseObjectResult_parsesJsonObjectPayload() throws Exception {
        JSONObject json = WebViewJsBridge.parseObjectResult("\"{\\\"ok\\\":true,\\\"count\\\":1}\"");

        assertTrue(json.getBoolean("ok"));
        assertEquals(1, json.getInt("count"));
    }

    @Test
    public void parseRawResult_preservesPrimitiveValue() {
        WebViewJsBridge.RawJsResult result = WebViewJsBridge.parseRawResult("\"hello\"");

        assertEquals("string", result.valueType);
        assertEquals("hello", result.value);
    }

    @Test
    public void parseRawResult_preservesArrayValue() {
        WebViewJsBridge.RawJsResult result = WebViewJsBridge.parseRawResult("[1,2,3]");

        assertEquals("array", result.valueType);
        assertEquals("[1,2,3]", result.value.toString());
    }
}
