package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HybridObservationComposerTest {

    @Test
    public void composeBuildsFusedActionableNodesWhenSignalsOverlap() throws Exception {
        JSONObject result = new JSONObject(HybridObservationComposer.compose(
                "native_xml",
                "com.hh.agent.ChatActivity",
                "发送消息",
                nativeXml(),
                visionJson(),
                1080,
                1920
        ));

        assertEquals("hybrid_native_screen", result.getString("mode"));
        assertTrue(result.getString("summary").contains("聊天输入页"));

        JSONObject quality = result.getJSONObject("quality");
        assertEquals(3, quality.getInt("nativeNodeCount"));
        assertEquals(1, quality.getInt("fusedMatchCount"));

        JSONArray actionableNodes = result.getJSONArray("actionableNodes");
        assertTrue(actionableNodes.length() > 0);

        JSONObject first = actionableNodes.getJSONObject(0);
        assertEquals("fused", first.getString("source"));
        assertEquals("control", first.getString("matchedVisionKind"));
        assertEquals("button", first.getString("visionType"));

        assertEquals(1, result.getJSONArray("sections").length());
        assertEquals(1, result.getJSONArray("listItems").length());
        assertTrue(result.getJSONArray("conflicts").length() > 0);
        JSONObject debug = result.getJSONObject("debug");
        assertEquals(1, debug.getJSONArray("matchPairs").length());
        JSONObject pair = debug.getJSONArray("matchPairs").getJSONObject(0);
        assertEquals(2, pair.getInt("nativeNodeIndex"));
        assertEquals("button", pair.getString("visionType"));
    }

    @Test
    public void composeFallsBackToNativeOnlyWhenVisualSignalMissing() throws Exception {
        JSONObject result = new JSONObject(HybridObservationComposer.compose(
                "native_xml",
                "com.hh.agent.ChatActivity",
                "发送消息",
                nativeXml(),
                null,
                0,
                0
        ));

        assertEquals("native_only", result.getString("mode"));
        assertTrue(result.getJSONArray("actionableNodes").length() > 0);
        assertEquals(0, result.getJSONArray("sections").length());
        assertEquals(0, result.getJSONArray("listItems").length());
    }

    private static String nativeXml() {
        return "<hierarchy activity=\"com.hh.agent.ChatActivity\">"
                + "<node index=\"0\" class=\"android.widget.FrameLayout\" bounds=\"[0,0][1080,1920]\">"
                + "<node index=\"1\" class=\"android.widget.EditText\" text=\"输入内容\" bounds=\"[40,1500][780,1700]\"></node>"
                + "<node index=\"2\" class=\"android.widget.Button\" text=\"发送消息\" bounds=\"[820,1500][1040,1700]\"></node>"
                + "</node>"
                + "</hierarchy>";
    }

    private static String visionJson() {
        return "{"
                + "\"summary\":\"聊天输入页\"," 
                + "\"page\":{\"width\":1080,\"height\":1920},"
                + "\"sections\":[{"
                + "\"id\":\"section-compose\","
                + "\"type\":\"compose_bar\","
                + "\"summaryText\":\"输入区\","
                + "\"bbox\":[0,1400,1080,1800],"
                + "\"importance\":0.95,"
                + "\"collapsedItemCount\":1"
                + "}],"
                + "\"items\":[{"
                + "\"id\":\"item-send\","
                + "\"sectionId\":\"section-compose\","
                + "\"summaryText\":\"发送按钮\","
                + "\"bbox\":[800,1480,1050,1710],"
                + "\"importance\":0.98,"
                + "\"textIds\":[\"text-send\"],"
                + "\"controlIds\":[\"control-send\"]"
                + "}],"
                + "\"texts\":[{"
                + "\"id\":\"text-send\","
                + "\"text\":\"发送消息\","
                + "\"bbox\":[820,1500,1040,1700],"
                + "\"score\":0.99"
                + "}],"
                + "\"controls\":[{"
                + "\"id\":\"control-send\","
                + "\"type\":\"button\","
                + "\"label\":\"发送消息\","
                + "\"bbox\":[820,1500,1040,1700],"
                + "\"score\":0.97"
                + "}],"
                + "\"debug\":{"
                + "\"dropSummary\":{"
                + "\"texts\":{\"dropped\":1},"
                + "\"controls\":{\"dropped\":2}"
                + "}"
                + "}"
                + "}";
    }
}