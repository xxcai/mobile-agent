package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;

public final class HybridObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "native_xml".equals(source)
                || "hybrid".equals(source)
                || "hybrid_observation".equals(source);
    }

    @Override
    public UnifiedViewObservation adapt(String source,
                                        String activityClassName,
                                        String interactionDomain,
                                        String targetHint,
                                        String pageUrl,
                                        String pageTitle,
                                        String nativeViewXml,
                                        String webDom,
                                        String visualObservationJson,
                                        String hybridObservationJson,
                                        String screenSnapshot) throws Exception {
        if (hybridObservationJson == null || hybridObservationJson.trim().isEmpty()) {
            return null;
        }

        JSONObject hybrid = new JSONObject(hybridObservationJson);
        JSONObject qualitySource = hybrid.optJSONObject("quality");
        JSONObject quality = new JSONObject()
                .put("adapterName", "HybridObservationAdapter")
                .put("mode", hybrid.optString("mode", "hybrid_native_screen"))
                .put("availableSignals", hybrid.optJSONObject("availableSignals") != null
                        ? hybrid.optJSONObject("availableSignals")
                        : JSONObject.NULL)
                .put("fusedMatchCount", qualitySource != null ? qualitySource.optInt("fusedMatchCount", 0) : 0);
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", nativeViewXml != null ? nativeViewXml : JSONObject.NULL)
                .put("webDom", JSONObject.NULL)
                .put("visualObservationJson", visualObservationJson != null
                        ? new JSONObject(visualObservationJson)
                        : JSONObject.NULL)
                .put("hybridObservationJson", hybrid)
                .put("screenSnapshot", screenSnapshot != null ? screenSnapshot : JSONObject.NULL);

        return new UnifiedViewObservation(
                source,
                interactionDomain,
                activityClassName,
                targetHint,
                pageUrl,
                pageTitle,
                hybrid.optString("summary", null),
                mapUiTree(source, nativeViewXml).toString(),
                HybridCanonicalMapper.mapActionableNodes(hybrid.optJSONArray("actionableNodes")).toString(),
                quality.toString(),
                raw.toString()
        );
    }

    private static JSONObject mapUiTree(String source, String nativeViewXml) throws Exception {
        Document document = NativeXmlObservationAdapter.NativeXmlTreeParser.parseXmlOnce(nativeViewXml);
        return NativeXmlObservationAdapter.NativeXmlTreeParser.buildUiTreeFromDocument(document, source);
    }

    static final class HybridCanonicalMapper {
        private HybridCanonicalMapper() {
        }

        static JSONArray mapActionableNodes(JSONArray actionableNodes) throws Exception {
            JSONArray elements = new JSONArray();
            if (actionableNodes == null) {
                return elements;
            }
            for (int i = 0; i < actionableNodes.length(); i++) {
                JSONObject node = actionableNodes.optJSONObject(i);
                if (node != null) {
                    elements.put(new JSONObject(node.toString()));
                }
            }
            return elements;
        }
    }
}
