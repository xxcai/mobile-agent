package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
import org.json.JSONObject;

public final class WebDomObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "web_dom".equals(source);
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
        if (webDom == null || webDom.isEmpty()) {
            throw new IllegalArgumentException("webDom cannot be null or empty for web_dom source");
        }
        JSONObject payload = new JSONObject(webDom);
        JSONObject uiTree = WebDomCanonicalMapper.mapTree(payload.optJSONObject("tree"), source);
        JSONArray screenElements = WebDomCanonicalMapper.collectElements(payload.optJSONObject("tree"), source);
        JSONObject quality = new JSONObject()
                .put("adapterName", "WebDomObservationAdapter")
                .put("mode", "web_only")
                .put("webNodeCount", payload.optInt("nodeCount", screenElements.length()))
                .put("webTruncated", payload.optBoolean("truncated", false))
                .put("webMaxDepthReached", payload.optInt("maxDepthReached", 0));
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", JSONObject.NULL)
                .put("webDom", payload)
                .put("visualObservationJson", JSONObject.NULL)
                .put("hybridObservationJson", JSONObject.NULL)
                .put("screenSnapshot", screenSnapshot);
        return new UnifiedViewObservation(
                source,
                interactionDomain,
                activityClassName,
                targetHint,
                firstNonEmpty(pageUrl, payload.optString("pageUrl", null)),
                firstNonEmpty(pageTitle, payload.optString("pageTitle", null)),
                buildWebSummary(firstNonEmpty(pageTitle, payload.optString("pageTitle", null)), screenElements.length()),
                uiTree.toString(),
                screenElements.toString(),
                quality.toString(),
                raw.toString()
        );
    }

    private static String firstNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }
        return fallback;
    }

    private static String buildWebSummary(String pageTitle, int elementCount) {
        if (elementCount == 0) {
            return "Web page with no actionable elements";
        }
        StringBuilder summary = new StringBuilder();
        if (pageTitle != null && !pageTitle.isEmpty()) {
            summary.append("Web page \"").append(pageTitle).append("\" with ");
        } else {
            summary.append("Web page with ");
        }
        summary.append(elementCount).append(" element(s)");
        return summary.toString();
    }

    static class WebDomCanonicalMapper {
        static JSONObject mapTree(JSONObject webNode, String source) throws Exception {
            if (webNode == null) {
                return new JSONObject().put("source", source);
            }
            JSONObject canonical = new JSONObject();
            canonical.put("source", source);
            if (webNode.has("ref")) {
                canonical.put("ref", webNode.getString("ref"));
            }
            if (webNode.has("tag")) {
                canonical.put("tagName", webNode.getString("tag"));
            }
            if (webNode.has("text")) {
                canonical.put("text", webNode.getString("text"));
            }
            if (webNode.has("selector")) {
                canonical.put("selector", webNode.getString("selector"));
            }
            if (webNode.has("ariaLabel")) {
                canonical.put("ariaLabel", webNode.getString("ariaLabel"));
            }
            if (webNode.has("bounds")) {
                canonical.put("bounds", webNode.getJSONObject("bounds"));
            }
            if (webNode.has("clickable")) {
                canonical.put("clickable", webNode.getBoolean("clickable"));
            }
            if (webNode.has("inputable")) {
                canonical.put("inputable", webNode.getBoolean("inputable"));
            }
            if (webNode.has("children")) {
                JSONArray originalChildren = webNode.getJSONArray("children");
                JSONArray canonicalChildren = new JSONArray();
                for (int i = 0; i < originalChildren.length(); i++) {
                    canonicalChildren.put(mapTree(originalChildren.getJSONObject(i), source));
                }
                if (canonicalChildren.length() > 0) {
                    canonical.put("children", canonicalChildren);
                }
            }
            return canonical;
        }

        static JSONArray collectElements(JSONObject webNode, String source) throws Exception {
            JSONArray elements = new JSONArray();
            if (webNode == null) {
                return elements;
            }
            collectElementsRecursive(webNode, elements, source);
            return elements;
        }

        private static void collectElementsRecursive(JSONObject node, JSONArray elements, String source) throws Exception {
            if (node == null) {
                return;
            }
            if (isActionable(node)) {
                JSONObject element = new JSONObject();
                element.put("source", source);
                if (node.has("ref")) {
                    element.put("ref", node.getString("ref"));
                }
                if (node.has("tag")) {
                    element.put("tagName", node.getString("tag"));
                }
                if (node.has("selector")) {
                    element.put("selector", node.getString("selector"));
                }
                if (node.has("text") && !node.getString("text").isEmpty()) {
                    element.put("text", node.getString("text"));
                }
                if (node.has("ariaLabel")) {
                    element.put("ariaLabel", node.getString("ariaLabel"));
                }
                if (node.has("bounds")) {
                    element.put("bounds", node.getJSONObject("bounds"));
                }
                if (node.has("clickable")) {
                    element.put("clickable", node.getBoolean("clickable"));
                }
                if (node.has("inputable")) {
                    element.put("inputable", node.getBoolean("inputable"));
                }
                elements.put(element);
            }
            if (node.has("children")) {
                JSONArray children = node.getJSONArray("children");
                for (int i = 0; i < children.length(); i++) {
                    collectElementsRecursive(children.getJSONObject(i), elements, source);
                }
            }
        }

        private static boolean isActionable(JSONObject node) throws Exception {
            if (node.has("clickable") && node.getBoolean("clickable")) {
                return true;
            }
            if (node.has("inputable") && node.getBoolean("inputable")) {
                return true;
            }
            if (node.has("text") && !node.getString("text").isEmpty()) {
                return true;
            }
            return false;
        }
    }
}
