package com.hh.agent.viewcontext;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ObservationTargetResolverTest {

    @Test
    public void resolvePrefersCanonicalScreenElementsBeforeLegacyFallback() throws Exception {
        JSONObject viewContext = new JSONObject()
                .put("screenElements", new JSONArray()
                        .put(new JSONObject()
                                .put("text", "发送")
                                .put("source", "fused")
                                .put("bounds", "[820,1500][1040,1700]")
                                .put("score", 0.95)
                                .put("nativeNodeIndex", 2)))
                .put("hybridObservation", new JSONObject()
                        .put("actionableNodes", new JSONArray()
                                .put(new JSONObject()
                                        .put("text", "旧发送")
                                        .put("bounds", "[1,1][2,2]")
                                        .put("score", 0.10))));

        ObservationTargetResolver.TargetReference target =
                ObservationTargetResolver.resolve(viewContext, "发送");

        assertNotNull(target);
        assertEquals("[820,1500][1040,1700]", target.bounds);
        assertEquals("fused", target.source);
        assertEquals(Integer.valueOf(2), target.nodeIndex);
    }

    @Test
    public void resolvePrefersHybridActionableNodes() throws Exception {
        JSONObject viewContext = new JSONObject()
                .put("hybridObservation", new JSONObject()
                        .put("actionableNodes", new org.json.JSONArray()
                                .put(new JSONObject()
                                        .put("source", "fused")
                                        .put("text", "发送消息")
                                        .put("visionType", "button")
                                        .put("nativeNodeIndex", 7)
                                        .put("bounds", "[820,1500][1040,1700]")
                                        .put("score", 0.96))));

        ObservationTargetResolver.TargetReference target =
                ObservationTargetResolver.resolve(viewContext, "发送消息");

        assertNotNull(target);
        assertEquals(Integer.valueOf(7), target.nodeIndex);
        assertEquals("[820,1500][1040,1700]", target.bounds);
        assertEquals("fused", target.source);
        assertEquals("发送消息", target.matchedText);
        assertEquals("button", target.visionType);
    }

    @Test
    public void resolveFallsBackToNativeViewXml() throws Exception {
        JSONObject viewContext = new JSONObject()
                .put("nativeViewXml", "<hierarchy><node index=\"3\" text=\"发送消息\" bounds=\"[820,1500][1040,1700]\"/></hierarchy>");

        ObservationTargetResolver.TargetReference target =
                ObservationTargetResolver.resolve(viewContext, "发送消息");

        assertNotNull(target);
        assertEquals(Integer.valueOf(3), target.nodeIndex);
        assertEquals("[820,1500][1040,1700]", target.bounds);
        assertEquals("native_xml_fallback", target.source);
    }
}
