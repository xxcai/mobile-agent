package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
import org.json.JSONObject;

public final class VisualObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "screen_snapshot".equals(source) || "visual_observation".equals(source);
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
        if (visualObservationJson == null || visualObservationJson.trim().isEmpty()) {
            return null;
        }

        JSONObject visual = new JSONObject(visualObservationJson);
        JSONObject quality = new JSONObject()
                .put("adapterName", "VisualObservationAdapter")
                .put("mode", "screen_only")
                .put("visionTextCount", length(visual.optJSONArray("texts")))
                .put("visionControlCount", length(visual.optJSONArray("controls")));
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", JSONObject.NULL)
                .put("webDom", JSONObject.NULL)
                .put("visualObservationJson", visual)
                .put("hybridObservationJson", JSONObject.NULL)
                .put("screenSnapshot", screenSnapshot != null ? screenSnapshot : JSONObject.NULL);

        return new UnifiedViewObservation(
                source,
                interactionDomain,
                activityClassName,
                targetHint,
                pageUrl,
                pageTitle,
                visual.optString("summary", null),
                VisualCanonicalMapper.mapTree(source, visual).toString(),
                VisualCanonicalMapper.collectElements(source, visual).toString(),
                quality.toString(),
                raw.toString()
        );
    }

    private static int length(JSONArray array) {
        return array != null ? array.length() : 0;
    }

    static final class VisualCanonicalMapper {
        private VisualCanonicalMapper() {
        }

        static JSONObject mapTree(String source, JSONObject visual) throws Exception {
            JSONObject root = new JSONObject()
                    .put("source", source)
                    .put("className", "visual_root");
            if (visual.has("summary")) {
                root.put("summary", visual.getString("summary"));
            }
            JSONArray children = new JSONArray();
            appendChildren(children, "section", visual.optJSONArray("sections"));
            appendChildren(children, "item", visual.optJSONArray("items"));
            appendChildren(children, "text", visual.optJSONArray("texts"));
            appendChildren(children, "control", visual.optJSONArray("controls"));
            if (children.length() > 0) {
                root.put("children", children);
            }
            return root;
        }

        static JSONArray collectElements(String source, JSONObject visual) throws Exception {
            JSONArray elements = new JSONArray();
            appendElements(elements, source, "control", visual.optJSONArray("controls"));
            appendElements(elements, source, "text", visual.optJSONArray("texts"));
            appendElements(elements, source, "item", visual.optJSONArray("items"));
            appendElements(elements, source, "section", visual.optJSONArray("sections"));
            return elements;
        }

        private static void appendChildren(JSONArray children, String kind, JSONArray source) throws Exception {
            if (source == null) {
                return;
            }
            for (int i = 0; i < source.length(); i++) {
                JSONObject entry = source.optJSONObject(i);
                if (entry != null) {
                    children.put(mapNode(kind, entry));
                }
            }
        }

        private static void appendElements(JSONArray elements, String sourceName, String kind, JSONArray source) throws Exception {
            if (source == null) {
                return;
            }
            for (int i = 0; i < source.length(); i++) {
                JSONObject entry = source.optJSONObject(i);
                if (entry != null) {
                    elements.put(mapElement(sourceName, kind, entry));
                }
            }
        }

        private static JSONObject mapNode(String kind, JSONObject entry) throws Exception {
            JSONObject node = new JSONObject()
                    .put("className", kind)
                    .put("role", roleOf(kind, entry));
            copyIfPresent(entry, node, "id");
            copyTextLike(entry, node);
            copyBbox(entry, node);
            return node;
        }

        private static JSONObject mapElement(String sourceName, String kind, JSONObject entry) throws Exception {
            JSONObject element = new JSONObject()
                    .put("source", sourceName)
                    .put("role", roleOf(kind, entry));
            copyIfPresent(entry, element, "id");
            copyTextLike(entry, element);
            copyBbox(entry, element);
            if (entry.has("score")) {
                element.put("score", entry.getDouble("score"));
            }
            return element;
        }

        private static String roleOf(String kind, JSONObject entry) {
            if ("control".equals(kind)) {
                String type = entry.optString("type", null);
                if (type != null && !type.isEmpty()) {
                    return type;
                }
            }
            return kind;
        }

        private static void copyTextLike(JSONObject source, JSONObject target) throws Exception {
            if (source.has("label")) {
                target.put("text", source.getString("label"));
            } else if (source.has("text")) {
                target.put("text", source.getString("text"));
            } else if (source.has("summaryText")) {
                target.put("text", source.getString("summaryText"));
            }
        }

        private static void copyIfPresent(JSONObject source, JSONObject target, String key) throws Exception {
            if (source.has(key)) {
                target.put(key, source.get(key));
            }
        }

        private static void copyBbox(JSONObject source, JSONObject target) throws Exception {
            JSONArray bbox = source.optJSONArray("bbox");
            if (bbox != null) {
                target.put("bounds", bbox);
            }
        }
    }
}
