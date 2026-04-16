package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
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

    @Test
    public void build_nativeXml_emitsUiTreeAndScreenElements() throws Exception {
        UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
                "native_xml",
                "com.hh.agent.ChatActivity",
                "native",
                "发送消息",
                null,
                null,
                "<hierarchy activity=\"com.hh.agent.ChatActivity\"><node index=\"0\" class=\"android.widget.FrameLayout\" bounds=\"[0,0][1080,1920]\"><node index=\"2\" class=\"android.widget.Button\" text=\"发送消息\" bounds=\"[820,1500][1040,1700]\" clickable=\"true\"></node></node></hierarchy>",
                null,
                null,
                null,
                null
        );

        JSONObject uiTree = new JSONObject(observation.uiTreeJson);
        JSONArray elements = new JSONArray(observation.screenElementsJson);

        assertEquals("android.widget.FrameLayout", uiTree.getString("className"));
        assertEquals("native_xml", uiTree.getString("source"));
        assertEquals("发送消息", elements.getJSONObject(0).getString("text"));
        assertEquals("[820,1500][1040,1700]", elements.getJSONObject(0).getString("bounds"));
    }

    @Test
    public void build_nativeXml_emitsCanonicalFields() throws Exception {
        String nativeXml = "<hierarchy activity=\"com.hh.agent.ChatActivity\"><node index=\"0\" class=\"android.widget.FrameLayout\" bounds=\"[0,0][1080,1920]\"><node index=\"2\" class=\"android.widget.Button\" text=\"发送消息\" bounds=\"[820,1500][1040,1700]\" clickable=\"true\"></node></node></hierarchy>";
        
        UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
                "native_xml",
                "com.hh.agent.ChatActivity",
                "native",
                "发送消息",
                null,
                null,
                nativeXml,
                null,
                null,
                null,
                null
        );

        // Verify pageSummary is present and meaningful
        assertNotNull("pageSummary must not be null", observation.pageSummary);
        assertTrue("pageSummary must mention native page", observation.pageSummary.contains("Native page"));
        assertTrue("pageSummary must mention activity", observation.pageSummary.contains("com.hh.agent.ChatActivity"));

        // Verify qualityJson contains adapter metadata
        assertNotNull("qualityJson must not be null", observation.qualityJson);
        JSONObject quality = new JSONObject(observation.qualityJson);
        assertEquals("NativeXmlObservationAdapter", quality.getString("adapterName"));
        assertEquals("native_only", quality.getString("mode"));
        assertTrue("nativeNodeCount must be positive", quality.getInt("nativeNodeCount") > 0);

        // Verify rawJson preserves nativeViewXml
        assertNotNull("rawJson must not be null", observation.rawJson);
        JSONObject raw = new JSONObject(observation.rawJson);
        assertEquals("rawJson must preserve original nativeViewXml", nativeXml, raw.getString("nativeViewXml"));
        assertTrue("rawJson.webDom must be null for native_xml source", raw.isNull("webDom"));
    }

    @Test
    public void nativeAdapter_onlySupportsNativeXml() throws Exception {
        NativeXmlObservationAdapter adapter = new NativeXmlObservationAdapter();
        
        assertTrue("Must support native_xml", adapter.supports("native_xml"));
        assertTrue("Must NOT support screen_snapshot", !adapter.supports("screen_snapshot"));
        assertTrue("Must NOT support web_dom", !adapter.supports("web_dom"));
        assertTrue("Must NOT support hybrid", !adapter.supports("hybrid"));
    }

    @Test
    public void build_webDom_emitsCanonicalTagTreeAndElements() throws Exception {
        UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
                "web_dom",
                "com.hh.agent.BusinessWebActivity",
                "web",
                "提交",
                "https://example.test/form",
                "Mock Page",
                null,
                "{\"pageUrl\":\"https://example.test/form\",\"pageTitle\":\"Mock Page\",\"nodeCount\":2,\"maxDepthReached\":1,\"truncated\":false,\"tree\":{\"ref\":\"node-0\",\"tag\":\"body\",\"text\":\"\",\"bounds\":{\"x\":0,\"y\":0,\"width\":1080,\"height\":1920},\"children\":[{\"ref\":\"node-1\",\"tag\":\"button\",\"selector\":\"button#submit\",\"text\":\"提交\",\"ariaLabel\":\"提交按钮\",\"clickable\":true,\"inputable\":false,\"bounds\":{\"x\":12,\"y\":34,\"width\":120,\"height\":44},\"children\":[]}]}}",
                null,
                null,
                null
        );

        JSONObject uiTree = new JSONObject(observation.uiTreeJson);
        JSONArray elements = new JSONArray(observation.screenElementsJson);

        assertEquals("body", uiTree.getString("tagName"));
        assertEquals("web_dom", uiTree.getString("source"));
        assertEquals("node-1", elements.getJSONObject(0).getString("ref"));
        assertEquals("button#submit", elements.getJSONObject(0).getString("selector"));
    }

    @Test
    public void build_visualObservation_emitsSimplifiedVisualTree() throws Exception {
        String visualObservationJson = "{\"summary\":\"聊天输入页\",\"sections\":[{\"id\":\"section-compose\",\"type\":\"compose_bar\",\"summaryText\":\"输入区\",\"bbox\":[0,1400,1080,1800]}],\"items\":[{\"id\":\"item-send\",\"summaryText\":\"发送按钮\",\"bbox\":[800,1480,1050,1710]}],\"texts\":[{\"id\":\"text-send\",\"text\":\"发送\",\"bbox\":[820,1500,1040,1700]}],\"controls\":[{\"id\":\"control-send\",\"type\":\"button\",\"label\":\"发送\",\"bbox\":[820,1500,1040,1700],\"score\":0.97}]}";

        UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
                "screen_snapshot",
                "com.hh.agent.ChatActivity",
                "native",
                "发送",
                null,
                null,
                null,
                null,
                visualObservationJson,
                null,
                "screenvision://capture/send"
        );

        JSONObject uiTree = new JSONObject(observation.uiTreeJson);
        JSONArray elements = new JSONArray(observation.screenElementsJson);
        JSONObject quality = new JSONObject(observation.qualityJson);
        JSONObject raw = new JSONObject(observation.rawJson);

        assertEquals("visual_root", uiTree.getString("className"));
        assertEquals("聊天输入页", observation.pageSummary);
        assertTrue(elements.length() > 0);
        assertEquals("button", elements.getJSONObject(0).getString("role"));
        assertEquals("VisualObservationAdapter", quality.getString("adapterName"));
        assertEquals("screen_only", quality.getString("mode"));
        assertTrue(raw.has("visualObservationJson"));
        assertEquals("发送", raw.getJSONObject("visualObservationJson")
                .getJSONArray("controls")
                .getJSONObject(0)
                .getString("label"));
        assertEquals("screenvision://capture/send", raw.getString("screenSnapshot"));
    }

    @Test
    public void build_hybridObservation_prefersActionableNodesAsCanonicalElements() throws Exception {
        String nativeXml = "<hierarchy activity=\"com.hh.agent.ChatActivity\"><node index=\"2\" class=\"android.widget.Button\" text=\"发送\" bounds=\"[820,1500][1040,1700]\"></node></hierarchy>";
        String visualObservationJson = "{\"summary\":\"聊天输入页\"}";
        String hybridObservationJson = "{\"summary\":\"聊天输入页\",\"availableSignals\":{\"nativeXml\":true,\"screenVisionCompact\":true},\"quality\":{\"fusedMatchCount\":1},\"actionableNodes\":[{\"source\":\"fused\",\"nativeNodeIndex\":2,\"text\":\"发送\",\"bounds\":\"[820,1500][1040,1700]\",\"score\":0.95}],\"conflicts\":[]}";

        UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
                "native_xml",
                "com.hh.agent.ChatActivity",
                "native",
                "发送",
                null,
                null,
                nativeXml,
                null,
                visualObservationJson,
                hybridObservationJson,
                "screenvision://capture/send"
        );

        JSONObject uiTree = new JSONObject(observation.uiTreeJson);
        JSONArray elements = new JSONArray(observation.screenElementsJson);
        JSONObject quality = new JSONObject(observation.qualityJson);
        JSONObject raw = new JSONObject(observation.rawJson);

        assertEquals("android.widget.Button", uiTree.getString("className"));
        assertEquals("聊天输入页", observation.pageSummary);
        assertEquals("发送", elements.getJSONObject(0).getString("text"));
        assertEquals("fused", elements.getJSONObject(0).getString("source"));
        assertEquals("HybridObservationAdapter", quality.getString("adapterName"));
        assertEquals(1, quality.getInt("fusedMatchCount"));
        assertEquals(nativeXml, raw.getString("nativeViewXml"));
        assertEquals("聊天输入页", raw.getJSONObject("hybridObservationJson").getString("summary"));
        assertEquals("聊天输入页", raw.getJSONObject("visualObservationJson").getString("summary"));
        assertEquals("screenvision://capture/send", raw.getString("screenSnapshot"));
    }
}
