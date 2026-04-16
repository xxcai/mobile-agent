package com.hh.agent.android.viewcontext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class NativeXmlObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "native_xml".equals(source);
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
        JSONObject uiTree = NativeXmlTreeParser.parseRoot(nativeViewXml, source);
        JSONArray screenElements = NativeXmlTreeParser.collectElements(nativeViewXml, source);
        JSONObject quality = new JSONObject()
                .put("adapterName", "NativeXmlObservationAdapter")
                .put("mode", "native_only")
                .put("nativeNodeCount", screenElements.length());
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", nativeViewXml)
                .put("webDom", JSONObject.NULL)
                .put("visualObservationJson", JSONObject.NULL)
                .put("hybridObservationJson", JSONObject.NULL)
                .put("screenSnapshot", screenSnapshot);
        return new UnifiedViewObservation(
                source,
                interactionDomain,
                activityClassName,
                targetHint,
                pageUrl,
                pageTitle,
                buildNativeSummary(screenElements, activityClassName),
                uiTree.toString(),
                screenElements.toString(),
                quality.toString(),
                raw.toString()
        );
    }

    private String buildNativeSummary(JSONArray screenElements, String activityClassName) throws Exception {
        if (screenElements.length() == 0) {
            return "Native page with no actionable elements";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("Native page (").append(activityClassName).append(") with ");
        summary.append(screenElements.length()).append(" element(s): ");
        for (int i = 0; i < Math.min(3, screenElements.length()); i++) {
            JSONObject el = screenElements.getJSONObject(i);
            if (i > 0) summary.append(", ");
            if (el.has("text")) {
                summary.append(el.getString("text"));
            } else if (el.has("className")) {
                summary.append(el.getString("className"));
            }
        }
        if (screenElements.length() > 3) {
            summary.append("...");
        }
        return summary.toString();
    }

    static class NativeXmlTreeParser {
        static JSONObject parseRoot(String nativeViewXml, String source) throws Exception {
            if (nativeViewXml == null || nativeViewXml.isEmpty()) {
                return new JSONObject().put("source", source);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(nativeViewXml.getBytes(StandardCharsets.UTF_8)));
            Element hierarchy = doc.getDocumentElement();
            NodeList children = hierarchy.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    return parseNode((Element) child, source);
                }
            }
            return new JSONObject().put("source", source);
        }

        static JSONArray collectElements(String nativeViewXml, String source) throws Exception {
            JSONArray elements = new JSONArray();
            if (nativeViewXml == null || nativeViewXml.isEmpty()) {
                return elements;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(nativeViewXml.getBytes(StandardCharsets.UTF_8)));
            Element hierarchy = doc.getDocumentElement();
            collectElementsRecursive(hierarchy, elements, source);
            return elements;
        }

        private static JSONObject parseNode(Element element, String source) throws Exception {
            JSONObject node = new JSONObject();
            node.put("source", source);
            if (element.hasAttribute("class")) {
                node.put("className", element.getAttribute("class"));
            }
            if (element.hasAttribute("text")) {
                node.put("text", element.getAttribute("text"));
            }
            if (element.hasAttribute("bounds")) {
                node.put("bounds", element.getAttribute("bounds"));
            }
            if (element.hasAttribute("index")) {
                node.put("index", Integer.parseInt(element.getAttribute("index")));
            }
            if (element.hasAttribute("clickable")) {
                node.put("clickable", Boolean.parseBoolean(element.getAttribute("clickable")));
            }
            JSONArray children = new JSONArray();
            NodeList childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    children.put(parseNode((Element) child, source));
                }
            }
            if (children.length() > 0) {
                node.put("children", children);
            }
            return node;
        }

        private static void collectElementsRecursive(Element element, JSONArray elements, String source) throws Exception {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) child;
                    if (isActionable(el)) {
                        JSONObject elementJson = new JSONObject();
                        elementJson.put("source", source);
                        if (el.hasAttribute("text") && !el.getAttribute("text").isEmpty()) {
                            elementJson.put("text", el.getAttribute("text"));
                        }
                        if (el.hasAttribute("class")) {
                            elementJson.put("className", el.getAttribute("class"));
                        }
                        if (el.hasAttribute("bounds")) {
                            elementJson.put("bounds", el.getAttribute("bounds"));
                        }
                        if (el.hasAttribute("clickable")) {
                            elementJson.put("clickable", Boolean.parseBoolean(el.getAttribute("clickable")));
                        }
                        elements.put(elementJson);
                    }
                    collectElementsRecursive(el, elements, source);
                }
            }
        }

        private static boolean isActionable(Element element) {
            return element.hasAttribute("clickable") && "true".equals(element.getAttribute("clickable"))
                    || element.hasAttribute("text") && !element.getAttribute("text").isEmpty();
        }
    }
}
