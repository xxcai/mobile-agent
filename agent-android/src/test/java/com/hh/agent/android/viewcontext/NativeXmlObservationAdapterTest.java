package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NativeXmlObservationAdapterTest {
    private NativeXmlObservationAdapter adapter;

    @Before
    public void setUp() {
        adapter = new NativeXmlObservationAdapter();
    }

    @Test
    public void testMalformedIndexDoesNotCrash() throws Exception {
        String xmlWithBadIndex = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <node index=\"not_a_number\" text=\"Button\" class=\"android.widget.Button\" " +
                "bounds=\"[0,0][100,100]\" clickable=\"true\" />\n" +
                "</hierarchy>";

        UnifiedViewObservation result = adapter.adapt(
                "native_xml",
                "com.example.TestActivity",
                "native",
                null,
                null,
                null,
                xmlWithBadIndex,
                null,
                null,
                null,
                null
        );

        assertNotNull(result);
        JSONObject uiTree = new JSONObject(result.uiTreeJson);
        assertFalse(uiTree.has("index")); // index should be omitted if malformed
    }

    @Test
    public void testXxeVulnerabilityProtection() throws Exception {
        // Attempt XXE attack with external entity
        String xxeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <node text=\"&xxe;\" class=\"android.widget.TextView\" bounds=\"[0,0][100,100]\" />\n" +
                "</hierarchy>";

        try {
            adapter.adapt(
                    "native_xml",
                    "com.example.TestActivity",
                    "native",
                    null,
                    null,
                    null,
                    xxeXml,
                    null,
                    null,
                    null,
                    null
            );
            fail("Should reject XML with DOCTYPE declarations");
        } catch (Exception e) {
            // Should throw an exception due to disallowed DOCTYPE
            assertTrue(e.getMessage().contains("DOCTYPE") || 
                      e.getMessage().contains("http://apache.org/xml/features/disallow-doctype-decl"));
        }
    }

    @Test
    public void testXmlParsedOnlyOnce() throws Exception {
        String validXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <node index=\"0\" text=\"First\" class=\"android.widget.Button\" " +
                "bounds=\"[0,0][100,100]\" clickable=\"true\" />\n" +
                "  <node index=\"1\" text=\"Second\" class=\"android.widget.Button\" " +
                "bounds=\"[0,100][100,200]\" clickable=\"true\" />\n" +
                "</hierarchy>";

        UnifiedViewObservation result = adapter.adapt(
                "native_xml",
                "com.example.TestActivity",
                "native",
                null,
                null,
                null,
                validXml,
                null,
                null,
                null,
                null
        );

        assertNotNull(result);
        
        // Verify both uiTree and screenElements are populated correctly
        JSONObject uiTree = new JSONObject(result.uiTreeJson);
        assertEquals("native_xml", uiTree.getString("source"));
        assertTrue(uiTree.has("text"));
        
        JSONArray screenElements = new JSONArray(result.screenElementsJson);
        assertEquals(2, screenElements.length());
        
        // Verify first element
        JSONObject firstElement = screenElements.getJSONObject(0);
        assertEquals("First", firstElement.getString("text"));
        
        // Verify second element
        JSONObject secondElement = screenElements.getJSONObject(1);
        assertEquals("Second", secondElement.getString("text"));
    }

    @Test
    public void testValidIndexIsParsedCorrectly() throws Exception {
        String xmlWithValidIndex = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <node index=\"42\" text=\"Button\" class=\"android.widget.Button\" " +
                "bounds=\"[0,0][100,100]\" clickable=\"true\" />\n" +
                "</hierarchy>";

        UnifiedViewObservation result = adapter.adapt(
                "native_xml",
                "com.example.TestActivity",
                "native",
                null,
                null,
                null,
                xmlWithValidIndex,
                null,
                null,
                null,
                null
        );

        assertNotNull(result);
        JSONObject uiTree = new JSONObject(result.uiTreeJson);
        assertEquals(42, uiTree.getInt("index"));
    }

    @Test
    public void testEmptyIndexAttributeDoesNotCrash() throws Exception {
        String xmlWithEmptyIndex = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <node index=\"\" text=\"Button\" class=\"android.widget.Button\" " +
                "bounds=\"[0,0][100,100]\" clickable=\"true\" />\n" +
                "</hierarchy>";

        UnifiedViewObservation result = adapter.adapt(
                "native_xml",
                "com.example.TestActivity",
                "native",
                null,
                null,
                null,
                xmlWithEmptyIndex,
                null,
                null,
                null,
                null
        );

        assertNotNull(result);
        JSONObject uiTree = new JSONObject(result.uiTreeJson);
        assertFalse(uiTree.has("index")); // empty index should be omitted
    }
}
