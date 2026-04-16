package com.hh.agent.android.viewcontext;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UnifiedViewObservationFacadeTest {

    @Test
    public void build_returnsCanonicalObservationMetadata() throws Exception {
        UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
                "native_xml",
                "com.hh.agent.ChatActivity",
                "native",
                "发送消息",
                null,
                null,
                "<hierarchy activity=\"com.hh.agent.ChatActivity\"><node index=\"0\" class=\"android.widget.Button\" text=\"发送消息\" bounds=\"[820,1500][1040,1700]\"></node></hierarchy>",
                null,
                null,
                null,
                null
        );

        assertEquals("native_xml", observation.source);
        assertEquals("native", observation.interactionDomain);
        assertEquals("发送消息", observation.targetHint);
        assertNotNull(observation.uiTreeJson);
        assertNotNull(observation.screenElementsJson);
    }

    @Test
    public void build_retainsRawAttachments() throws Exception {
        UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
                "web_dom",
                "com.hh.agent.BusinessWebActivity",
                "web",
                "提交",
                "https://example.test/form",
                "Mock Page",
                null,
                "{\"pageUrl\":\"https://example.test/form\",\"pageTitle\":\"Mock Page\",\"tree\":{\"tag\":\"body\",\"children\":[]}}",
                null,
                null,
                null
        );

        JSONObject raw = new JSONObject(observation.rawJson);
        assertTrue(raw.has("webDom"));
        assertEquals("https://example.test/form", observation.pageUrl);
        assertEquals("Mock Page", observation.pageTitle);
    }
}
