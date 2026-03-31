package com.hh.agent.android.viewcontext;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

final class HybridObservationComposer {

    private static final int MAX_ACTIONABLE = 18;
    private static final int MAX_SECTIONS = 8;
    private static final int MAX_ITEMS = 12;
    private static final int MAX_CONFLICTS = 8;
    private static final double MATCH_THRESHOLD = 0.40d;

    private HybridObservationComposer() {
    }

    static String compose(String primarySource,
                          @Nullable String activityClassName,
                          @Nullable String targetHint,
                          @Nullable String nativeViewXml,
                          @Nullable String screenVisionCompactJson,
                          int imageWidth,
                          int imageHeight) {
        try {
            NativeData nativeData = parseNative(nativeViewXml);
            VisionData visionData = parseVision(screenVisionCompactJson, imageWidth, imageHeight);
            match(nativeData.nodes, visionData.signals);
            JSONObject result = new JSONObject();
            result.put("schemaVersion", 1);
            result.put("mode", modeOf(nativeData.available, visionData.available));
            putNullable(result, "primarySource", trim(primarySource));
            putNullable(result, "activityClassName", trim(activityClassName != null ? activityClassName : nativeData.activityClassName));
            putNullable(result, "targetHint", trim(targetHint));
            result.put("summary", summaryOf(nativeData, visionData));
            result.put("executionHint", "Prefer actionableNodes with source=fused or native and use their bounds as referencedBounds. Treat vision_only nodes as weaker candidates.");
            result.put("page", new JSONObject()
                    .put("width", visionData.width > 0 ? visionData.width : nativeData.maxRight)
                    .put("height", visionData.height > 0 ? visionData.height : nativeData.maxBottom));
            result.put("availableSignals", new JSONObject()
                    .put("nativeXml", nativeData.available)
                    .put("screenVisionCompact", visionData.available)
                    .put("visualPageGeometry", visionData.width > 0 && visionData.height > 0));
            result.put("quality", new JSONObject()
                    .put("nativeNodeCount", nativeData.nodes.size())
                    .put("nativeTextNodeCount", nativeData.textNodeCount)
                    .put("visionTextCount", visionData.textCount)
                    .put("visionControlCount", visionData.controlCount)
                    .put("fusedMatchCount", matchedCount(nativeData.nodes))
                    .put("visionDroppedTextCount", visionData.droppedTextCount)
                    .put("visionDroppedControlCount", visionData.droppedControlCount));
            result.put("actionableNodes", buildActionable(nativeData.nodes, visionData.signals, targetHint));
            result.put("sections", buildRegions(visionData.sections, nativeData.nodes, MAX_SECTIONS, true));
            result.put("listItems", buildRegions(visionData.items, nativeData.nodes, MAX_ITEMS, false));
            result.put("conflicts", buildConflicts(nativeData, visionData, targetHint));
            result.put("debug", buildDebug(nativeData, visionData, targetHint));
            return result.toString();
        } catch (Exception ignored) {
            return "{\"schemaVersion\":1,\"mode\":\"unavailable\",\"summary\":\"Hybrid observation composition failed\"}";
        }
    }

    private static String modeOf(boolean hasNative, boolean hasVision) {
        if (hasNative && hasVision) {
            return "hybrid_native_screen";
        }
        if (hasNative) {
            return "native_only";
        }
        if (hasVision) {
            return "screen_only";
        }
        return "unavailable";
    }

    private static JSONArray buildActionable(List<NativeNode> nativeNodes,
                                             List<Signal> signals,
                                             @Nullable String targetHint) throws Exception {
        List<ScoredJson> scored = new ArrayList<>();
        for (NativeNode node : nativeNodes) {
            double score = nodeScore(node, targetHint);
            if ((node.matched == null && !node.interesting()) || (node.matched == null && score < 0.34d)) {
                continue;
            }
            JSONObject object = new JSONObject();
            object.put("id", node.id());
            object.put("source", node.matched != null ? "fused" : "native");
            object.put("nativeNodeIndex", node.index);
            putNullable(object, "text", trim(node.text));
            putNullable(object, "className", trim(node.className));
            putNullable(object, "resourceId", trim(node.resourceId));
            putBounds(object, node.bounds);
            object.put("score", round(score));
            object.put("actionability", labelOf(score));
            if (node.matched != null) {
                object.put("matchScore", round(node.matchScore));
                putNullable(object, "matchedVisionId", trim(node.matched.rawId));
                putNullable(object, "matchedVisionKind", trim(node.matched.kind));
                putNullable(object, "visionType", trim(node.matched.type));
                putNullable(object, "visionLabel", trim(node.matched.label));
                putNullable(object, "visionRole", trim(node.matched.role));
            }
            scored.add(new ScoredJson(score, node.id(), object));
        }
        for (Signal signal : signals) {
            double score = signalScore(signal, targetHint);
            if (signal.matchedNode != null || !signal.actionable(targetHint) || score < 0.45d) {
                continue;
            }
            JSONObject object = new JSONObject();
            object.put("id", signal.id());
            object.put("source", "vision_only");
            putNullable(object, "text", trim(signal.label));
            putNullable(object, "visionType", trim(signal.type));
            putNullable(object, "visionRole", trim(signal.role));
            putBounds(object, signal.bounds);
            object.put("score", round(score));
            object.put("actionability", labelOf(score));
            scored.add(new ScoredJson(score, signal.id(), object));
        }
        Collections.sort(scored, new Comparator<ScoredJson>() {
            @Override
            public int compare(ScoredJson left, ScoredJson right) {
                int scoreCompare = Double.compare(right.score, left.score);
                return scoreCompare != 0 ? scoreCompare : left.key.compareTo(right.key);
            }
        });
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(MAX_ACTIONABLE, scored.size()); i++) {
            array.put(scored.get(i).object);
        }
        return array;
    }
    private static JSONArray buildRegions(JSONArray source,
                                          List<NativeNode> nativeNodes,
                                          int limit,
                                          boolean section) throws Exception {
        List<JSONObject> ranked = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject object = source.optJSONObject(i);
            if (object != null) {
                ranked.add(object);
            }
        }
        Collections.sort(ranked, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left, JSONObject right) {
                return Double.compare(right.optDouble("importance", 0d), left.optDouble("importance", 0d));
            }
        });
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            JSONObject raw = ranked.get(i);
            Bounds bounds = Bounds.fromArray(raw.optJSONArray("bbox"));
            List<NativeNode> nodes = nodesIn(bounds, nativeNodes);
            JSONObject item = new JSONObject();
            putNullable(item, "id", trim(raw.optString("id", null)));
            putNullable(item, "type", trim(raw.optString("type", null)));
            putNullable(item, "sectionId", trim(raw.optString("sectionId", null)));
            putNullable(item, "summaryText", trim(raw.optString("summaryText", null)));
            putBounds(item, bounds);
            item.put("importance", round(raw.optDouble("importance", 0d)));
            item.put("matchedNativeNodeIds", toIdArray(nodes, 6));
            item.put("matchedNativeNodeCount", nodes.size());
            if (section) {
                item.put("collapsedItemCount", raw.optInt("collapsedItemCount", 0));
            } else {
                item.put("textCount", length(raw.optJSONArray("textIds")));
                item.put("controlCount", length(raw.optJSONArray("controlIds")));
            }
            array.put(item);
        }
        return array;
    }

    private static JSONArray buildConflicts(NativeData nativeData,
                                            VisionData visionData,
                                            @Nullable String targetHint) throws Exception {
        List<ScoredJson> scored = new ArrayList<>();
        int drops = visionData.droppedTextCount + visionData.droppedControlCount;
        if (drops > 0) {
            scored.add(new ScoredJson(1d + (drops / 100d), "drop", new JSONObject()
                    .put("code", "vision_compaction_drop_summary")
                    .put("severity", drops >= 5 ? "warning" : "info")
                    .put("message", "Screen vision compact stage dropped " + visionData.droppedTextCount + " texts and " + visionData.droppedControlCount + " controls.")));
        }
        for (Signal signal : visionData.signals) {
            double score = signalScore(signal, targetHint);
            if (signal.matchedNode == null && signal.actionable(targetHint) && score >= 0.72d) {
                JSONObject object = new JSONObject();
                object.put("code", "vision_only_candidate");
                object.put("severity", "warning");
                object.put("message", "High-value visual candidate has no native match: " + signal.displayText());
                putBounds(object, signal.bounds);
                scored.add(new ScoredJson(score, signal.id(), object));
            }
        }
        for (NativeNode node : nativeData.nodes) {
            double score = nodeScore(node, targetHint);
            if (node.matched == null && node.interesting() && score >= 0.78d) {
                JSONObject object = new JSONObject();
                object.put("code", "native_only_candidate");
                object.put("severity", "info");
                object.put("message", "High-value native candidate has no visual match: " + node.displayText());
                object.put("nativeNodeIndex", node.index);
                putBounds(object, node.bounds);
                scored.add(new ScoredJson(score, node.id(), object));
            }
        }
        Collections.sort(scored, new Comparator<ScoredJson>() {
            @Override
            public int compare(ScoredJson left, ScoredJson right) {
                return Double.compare(right.score, left.score);
            }
        });
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(MAX_CONFLICTS, scored.size()); i++) {
            array.put(scored.get(i).object);
        }
        return array;
    }

    private static JSONObject buildDebug(NativeData nativeData,
                                         VisionData visionData,
                                         @Nullable String targetHint) throws Exception {
        JSONObject debug = new JSONObject();
        debug.put("matchPairs", buildMatchPairs(nativeData.nodes, targetHint));
        debug.put("nativeOnlyCandidates", buildNativeOnlyCandidates(nativeData.nodes, targetHint));
        debug.put("visionOnlyCandidates", buildVisionOnlyCandidates(visionData.signals, targetHint));
        debug.put("topNativeTexts", new JSONArray(collectTexts(nativeData.nodes, 8)));
        return debug;
    }

    private static JSONArray buildMatchPairs(List<NativeNode> nativeNodes,
                                             @Nullable String targetHint) throws Exception {
        List<ScoredJson> scored = new ArrayList<>();
        for (NativeNode node : nativeNodes) {
            if (node.matched == null) {
                continue;
            }
            Signal signal = node.matched;
            JSONObject object = new JSONObject();
            object.put("nativeId", node.id());
            object.put("nativeNodeIndex", node.index);
            putNullable(object, "nativeText", trim(node.text));
            putNullable(object, "nativeClassName", trim(node.className));
            putNullable(object, "nativeResourceId", trim(node.resourceId));
            putBounds(object, node.bounds);
            putNullable(object, "visionId", trim(signal.rawId));
            putNullable(object, "visionKind", trim(signal.kind));
            putNullable(object, "visionType", trim(signal.type));
            putNullable(object, "visionLabel", trim(signal.label));
            putNullable(object, "visionRole", trim(signal.role));
            object.put("visionBounds", signal.bounds != null ? signal.bounds.value() : JSONObject.NULL);
            object.put("matchScore", round(node.matchScore));
            object.put("nativeScore", round(nodeScore(node, targetHint)));
            object.put("visionScore", round(signalScore(signal, targetHint)));
            scored.add(new ScoredJson(node.matchScore, node.id(), object));
        }
        Collections.sort(scored, new Comparator<ScoredJson>() {
            @Override
            public int compare(ScoredJson left, ScoredJson right) {
                int scoreCompare = Double.compare(right.score, left.score);
                return scoreCompare != 0 ? scoreCompare : left.key.compareTo(right.key);
            }
        });
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(12, scored.size()); i++) {
            array.put(scored.get(i).object);
        }
        return array;
    }

    private static JSONArray buildNativeOnlyCandidates(List<NativeNode> nativeNodes,
                                                       @Nullable String targetHint) throws Exception {
        List<ScoredJson> scored = new ArrayList<>();
        for (NativeNode node : nativeNodes) {
            if (node.matched != null || !node.interesting()) {
                continue;
            }
            double score = nodeScore(node, targetHint);
            if (score < 0.42d) {
                continue;
            }
            JSONObject object = new JSONObject();
            object.put("id", node.id());
            object.put("nativeNodeIndex", node.index);
            putNullable(object, "text", trim(node.text));
            putNullable(object, "className", trim(node.className));
            putNullable(object, "resourceId", trim(node.resourceId));
            putBounds(object, node.bounds);
            object.put("score", round(score));
            object.put("actionability", labelOf(score));
            scored.add(new ScoredJson(score, node.id(), object));
        }
        Collections.sort(scored, new Comparator<ScoredJson>() {
            @Override
            public int compare(ScoredJson left, ScoredJson right) {
                int scoreCompare = Double.compare(right.score, left.score);
                return scoreCompare != 0 ? scoreCompare : left.key.compareTo(right.key);
            }
        });
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(10, scored.size()); i++) {
            array.put(scored.get(i).object);
        }
        return array;
    }

    private static JSONArray buildVisionOnlyCandidates(List<Signal> signals,
                                                       @Nullable String targetHint) throws Exception {
        List<ScoredJson> scored = new ArrayList<>();
        for (Signal signal : signals) {
            if (signal.matchedNode != null || !signal.actionable(targetHint)) {
                continue;
            }
            double score = signalScore(signal, targetHint);
            if (score < 0.42d) {
                continue;
            }
            JSONObject object = new JSONObject();
            object.put("id", signal.id());
            putNullable(object, "text", trim(signal.label));
            putNullable(object, "visionType", trim(signal.type));
            putNullable(object, "visionRole", trim(signal.role));
            putBounds(object, signal.bounds);
            object.put("score", round(score));
            object.put("actionability", labelOf(score));
            scored.add(new ScoredJson(score, signal.id(), object));
        }
        Collections.sort(scored, new Comparator<ScoredJson>() {
            @Override
            public int compare(ScoredJson left, ScoredJson right) {
                int scoreCompare = Double.compare(right.score, left.score);
                return scoreCompare != 0 ? scoreCompare : left.key.compareTo(right.key);
            }
        });
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(10, scored.size()); i++) {
            array.put(scored.get(i).object);
        }
        return array;
    }
    private static String summaryOf(NativeData nativeData, VisionData visionData) {
        if (hasText(visionData.summary) && nativeData.available) {
            return visionData.summary + " Native tree captured " + nativeData.nodes.size() + " nodes and fused " + matchedCount(nativeData.nodes) + " screenshot matches.";
        }
        if (hasText(visionData.summary)) {
            return visionData.summary;
        }
        if (nativeData.available) {
            List<String> texts = collectTexts(nativeData.nodes, 5);
            return "Native tree captured " + nativeData.nodes.size() + " visible nodes." + (texts.isEmpty() ? "" : " Key texts: " + String.join(", ", texts) + ".");
        }
        return "No native or screenshot observation signal was available for the current page.";
    }
    private static void match(List<NativeNode> nativeNodes, List<Signal> signals) {
        for (NativeNode node : nativeNodes) {
            for (Signal signal : signals) {
                double score = matchScore(node, signal);
                if (score > node.bestScore || (almostEqual(score, node.bestScore) && preferSignalForNode(node, signal, node.bestSignal))) {
                    node.bestScore = score;
                    node.bestSignal = signal;
                }
                if (score > signal.bestScore || (almostEqual(score, signal.bestScore) && preferNodeForSignal(signal, node, signal.bestNode))) {
                    signal.bestScore = score;
                    signal.bestNode = node;
                }
            }
        }
        for (NativeNode node : nativeNodes) {
            if (node.bestSignal != null && node.bestScore >= MATCH_THRESHOLD && node.bestSignal.bestNode == node) {
                node.matched = node.bestSignal;
                node.matchScore = node.bestScore;
                node.bestSignal.matchedNode = node;
            }
        }
    }

    private static boolean almostEqual(double left, double right) {
        return Math.abs(left - right) <= 0.0001d;
    }

    private static boolean preferSignalForNode(NativeNode node, Signal candidate, @Nullable Signal current) {
        if (current == null) {
            return true;
        }
        if (node.interactiveClass() && candidate.isControl() != current.isControl()) {
            return candidate.isControl();
        }
        double candidateText = Math.max(textMatch(node.text, candidate.label), textMatch(resourceHint(node.resourceId), candidate.label));
        double currentText = Math.max(textMatch(node.text, current.label), textMatch(resourceHint(node.resourceId), current.label));
        if (Math.abs(candidateText - currentText) > 0.05d) {
            return candidateText > currentText;
        }
        if (Math.abs(candidate.importance - current.importance) > 0.05d) {
            return candidate.importance > current.importance;
        }
        return candidate.confidence > current.confidence;
    }

    private static boolean preferNodeForSignal(Signal signal, NativeNode candidate, @Nullable NativeNode current) {
        if (current == null) {
            return true;
        }
        if (signal.isControl() && candidate.interactiveClass() != current.interactiveClass()) {
            return candidate.interactiveClass();
        }
        double candidateText = Math.max(textMatch(candidate.text, signal.label), textMatch(resourceHint(candidate.resourceId), signal.label));
        double currentText = Math.max(textMatch(current.text, signal.label), textMatch(resourceHint(current.resourceId), signal.label));
        if (Math.abs(candidateText - currentText) > 0.05d) {
            return candidateText > currentText;
        }
        return candidate.index < current.index;
    }

    private static double matchScore(NativeNode node, Signal signal) {
        double overlap = overlap(node.bounds, signal.bounds);
        double text = Math.max(textMatch(node.text, signal.label), textMatch(resourceHint(node.resourceId), signal.label));
        if (overlap <= 0.02d && text < 0.72d) {
            return 0d;
        }
        double score = overlap * (signal.isControl() ? 0.58d : 0.68d) + text * (signal.isControl() ? 0.42d : 0.32d);
        if (text >= 0.95d && overlap >= 0.20d) {
            score += 0.08d;
        }
        return clamp(score);
    }

    private static double nodeScore(NativeNode node, @Nullable String targetHint) {
        double score = 0.06d;
        if (hasText(node.text)) {
            score += 0.34d;
        }
        if (hasText(node.resourceId)) {
            score += 0.12d;
        }
        if (node.interactiveClass()) {
            score += 0.18d;
        }
        if (node.matched != null) {
            score += 0.20d + (0.24d * node.matchScore);
        }
        score += 0.22d * hintScore(targetHint, node.text, resourceHint(node.resourceId), node.matched != null ? node.matched.label : null);
        return clamp(score);
    }

    private static double signalScore(Signal signal, @Nullable String targetHint) {
        double score = 0.10d + clamp(signal.confidence) * 0.28d + clamp(signal.importance) * 0.28d;
        if (signal.isControl()) {
            score += 0.18d;
        }
        if (hasText(signal.label)) {
            score += 0.10d;
        }
        if (signal.actionable(targetHint)) {
            score += 0.12d;
        }
        score += 0.22d * hintScore(targetHint, signal.label, signal.type, signal.role);
        return clamp(score);
    }

    private static int matchedCount(List<NativeNode> nodes) {
        int count = 0;
        for (NativeNode node : nodes) {
            if (node.matched != null) {
                count++;
            }
        }
        return count;
    }

    private static List<NativeNode> nodesIn(@Nullable Bounds bounds, List<NativeNode> nodes) {
        List<NativeNode> result = new ArrayList<>();
        if (bounds == null) {
            return result;
        }
        for (NativeNode node : nodes) {
            if (!node.interesting()) {
                continue;
            }
            if (bounds.containsCenter(node.bounds) || overlap(bounds, node.bounds) >= 0.35d) {
                result.add(node);
            }
        }
        Collections.sort(result, new Comparator<NativeNode>() {
            @Override
            public int compare(NativeNode left, NativeNode right) {
                return Double.compare(nodeScore(right, null), nodeScore(left, null));
            }
        });
        return result;
    }

    private static JSONArray toIdArray(List<NativeNode> nodes, int limit) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(limit, nodes.size()); i++) {
            array.put(nodes.get(i).id());
        }
        return array;
    }

    private static NativeData parseNative(@Nullable String xml) {
        if (!hasText(xml)) {
            return new NativeData(false, null, new ArrayList<NativeNode>(), 0, 0, 0);
        }
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            String activity = root != null ? trim(root.getAttribute("activity")) : null;
            NodeList list = document.getElementsByTagName("node");
            List<NativeNode> nodes = new ArrayList<>();
            int textCount = 0;
            int maxRight = 0;
            int maxBottom = 0;
            for (int i = 0; i < list.getLength(); i++) {
                if (!(list.item(i) instanceof Element)) {
                    continue;
                }
                Element element = (Element) list.item(i);
                Bounds bounds = Bounds.parse(trim(element.getAttribute("bounds")));
                if (bounds == null) {
                    continue;
                }
                NativeNode node = new NativeNode(parseInt(element.getAttribute("index"), i), trim(element.getAttribute("class")), trim(element.getAttribute("resource-id")), trim(element.getAttribute("text")), bounds);
                if (hasText(node.text)) {
                    textCount++;
                }
                nodes.add(node);
                maxRight = Math.max(maxRight, bounds.right);
                maxBottom = Math.max(maxBottom, bounds.bottom);
            }
            return new NativeData(true, activity, nodes, textCount, maxRight, maxBottom);
        } catch (Exception ignored) {
            return new NativeData(false, null, new ArrayList<NativeNode>(), 0, 0, 0);
        }
    }
    private static VisionData parseVision(@Nullable String json, int fallbackWidth, int fallbackHeight) {
        if (!hasText(json)) {
            return new VisionData(false, null, fallbackWidth, fallbackHeight, new JSONArray(), new JSONArray(), new ArrayList<Signal>(), 0, 0, 0, 0);
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject page = root.optJSONObject("page");
            int width = page != null ? page.optInt("width", fallbackWidth) : fallbackWidth;
            int height = page != null ? page.optInt("height", fallbackHeight) : fallbackHeight;
            List<Signal> signals = new ArrayList<>();
            JSONArray texts = root.optJSONArray("texts");
            JSONArray controls = root.optJSONArray("controls");
            if (texts != null) {
                for (int i = 0; i < texts.length(); i++) {
                    JSONObject text = texts.optJSONObject(i);
                    Bounds bounds = text != null ? Bounds.fromArray(text.optJSONArray("bbox")) : null;
                    if (text == null || bounds == null) {
                        continue;
                    }
                    signals.add(new Signal(trim(text.optString("id", null)), "text", "text", trim(text.optString("text", null)), trim(text.optString("role", null)), bounds, text.optDouble("confidence", 0d), text.optDouble("importance", 0d)));
                }
            }
            if (controls != null) {
                for (int i = 0; i < controls.length(); i++) {
                    JSONObject control = controls.optJSONObject(i);
                    Bounds bounds = control != null ? Bounds.fromArray(control.optJSONArray("bbox")) : null;
                    if (control == null || bounds == null) {
                        continue;
                    }
                    signals.add(new Signal(trim(control.optString("id", null)), "control", trim(control.optString("type", null)), trim(control.optString("label", null)), trim(control.optString("role", null)), bounds, control.optDouble("confidence", 0d), control.optDouble("importance", 0d)));
                }
            }
            JSONObject debug = root.optJSONObject("debug");
            return new VisionData(true, trim(root.optString("summary", null)), Math.max(0, width), Math.max(0, height), root.optJSONArray("sections") != null ? root.optJSONArray("sections") : new JSONArray(), root.optJSONArray("items") != null ? root.optJSONArray("items") : new JSONArray(), signals, length(texts), length(controls), dropCount(debug, "texts"), dropCount(debug, "controls"));
        } catch (Exception ignored) {
            return new VisionData(false, null, fallbackWidth, fallbackHeight, new JSONArray(), new JSONArray(), new ArrayList<Signal>(), 0, 0, 0, 0);
        }
    }

    private static int dropCount(@Nullable JSONObject debug, String key) {
        if (debug == null) {
            return 0;
        }
        JSONObject summary = debug.optJSONObject("dropSummary");
        if (summary == null) {
            return 0;
        }
        JSONArray counts = summary.optJSONArray(key);
        if (counts != null) {
            int total = 0;
            for (int i = 0; i < counts.length(); i++) {
                JSONObject item = counts.optJSONObject(i);
                if (item != null) {
                    total += Math.max(0, item.optInt("count", 0));
                }
            }
            return total;
        }
        JSONObject countObject = summary.optJSONObject(key);
        if (countObject != null) {
            return Math.max(0, countObject.optInt("dropped", countObject.optInt("count", 0)));
        }
        return 0;
    }

    private static List<String> collectTexts(List<NativeNode> nodes, int limit) {
        Set<String> texts = new LinkedHashSet<>();
        for (NativeNode node : nodes) {
            if (!hasText(node.text)) {
                continue;
            }
            texts.add(node.text.trim());
            if (texts.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(texts);
    }

    private static double hintScore(@Nullable String hint, @Nullable String... candidates) {
        if (!hasText(hint) || candidates == null) {
            return 0d;
        }
        double best = 0d;
        for (String candidate : candidates) {
            best = Math.max(best, textMatch(hint, candidate));
        }
        return best;
    }

    private static double textMatch(@Nullable String left, @Nullable String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        if (a.equals(b)) {
            return 1d;
        }
        if (a.contains(b) || b.contains(a)) {
            return 0.88d;
        }
        Set<Character> overlap = new LinkedHashSet<>();
        for (int i = 0; i < a.length(); i++) {
            char c = a.charAt(i);
            if (b.indexOf(c) >= 0) {
                overlap.add(c);
            }
        }
        double coverage = overlap.size() / (double) Math.max(a.length(), b.length());
        if (coverage >= 0.75d) {
            return 0.72d;
        }
        if (coverage >= 0.50d) {
            return 0.48d;
        }
        if (coverage >= 0.30d) {
            return 0.28d;
        }
        return 0d;
    }

    private static double overlap(@Nullable Bounds left, @Nullable Bounds right) {
        if (left == null || right == null) {
            return 0d;
        }
        int l = Math.max(left.left, right.left);
        int t = Math.max(left.top, right.top);
        int r = Math.min(left.right, right.right);
        int b = Math.min(left.bottom, right.bottom);
        if (l >= r || t >= b) {
            return 0d;
        }
        double intersection = (r - l) * (double) (b - t);
        double union = left.area() + right.area() - intersection;
        double iou = union > 0d ? intersection / union : 0d;
        double coverage = intersection / Math.min(left.area(), right.area());
        return clamp(Math.max(iou, coverage * 0.92d));
    }

    private static String normalize(@Nullable String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.US).replace("_", "").replace("-", "").replace(" ", "").replace("\n", "").replace("\r", "");
    }

    @Nullable
    private static String resourceHint(@Nullable String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value;
        int slash = text.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < text.length()) {
            text = text.substring(slash + 1);
        }
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            text = text.substring(colon + 1);
        }
        text = text.replace('_', ' ').replace('-', ' ').trim();
        return text.isEmpty() ? null : text;
    }

    private static int parseInt(@Nullable String value, int fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int length(@Nullable JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Nullable
    private static String trim(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    private static double clamp(double value) {
        if (value < 0d) {
            return 0d;
        }
        if (value > 1d) {
            return 1d;
        }
        return value;
    }

    private static String labelOf(double score) {
        if (score >= 0.80d) {
            return "high";
        }
        if (score >= 0.50d) {
            return "medium";
        }
        return "low";
    }

    private static void putNullable(JSONObject object, String key, @Nullable String value) throws Exception {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static void putBounds(JSONObject object, @Nullable Bounds bounds) throws Exception {
        if (bounds == null) {
            object.put("bounds", JSONObject.NULL);
            object.put("bbox", JSONObject.NULL);
            return;
        }
        object.put("bounds", bounds.value());
        object.put("bbox", new JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom));
    }
    private static final class NativeData {
        final boolean available;
        @Nullable final String activityClassName;
        final List<NativeNode> nodes;
        final int textNodeCount;
        final int maxRight;
        final int maxBottom;

        NativeData(boolean available, @Nullable String activityClassName, List<NativeNode> nodes, int textNodeCount, int maxRight, int maxBottom) {
            this.available = available;
            this.activityClassName = activityClassName;
            this.nodes = nodes;
            this.textNodeCount = textNodeCount;
            this.maxRight = maxRight;
            this.maxBottom = maxBottom;
        }
    }

    private static final class VisionData {
        final boolean available;
        @Nullable final String summary;
        final int width;
        final int height;
        final JSONArray sections;
        final JSONArray items;
        final List<Signal> signals;
        final int textCount;
        final int controlCount;
        final int droppedTextCount;
        final int droppedControlCount;

        VisionData(boolean available, @Nullable String summary, int width, int height, JSONArray sections, JSONArray items, List<Signal> signals, int textCount, int controlCount, int droppedTextCount, int droppedControlCount) {
            this.available = available;
            this.summary = summary;
            this.width = width;
            this.height = height;
            this.sections = sections;
            this.items = items;
            this.signals = signals;
            this.textCount = textCount;
            this.controlCount = controlCount;
            this.droppedTextCount = droppedTextCount;
            this.droppedControlCount = droppedControlCount;
        }
    }

    private static final class NativeNode {
        final int index;
        @Nullable final String className;
        @Nullable final String resourceId;
        @Nullable final String text;
        final Bounds bounds;
        @Nullable Signal bestSignal;
        double bestScore;
        @Nullable Signal matched;
        double matchScore;

        NativeNode(int index, @Nullable String className, @Nullable String resourceId, @Nullable String text, Bounds bounds) {
            this.index = index;
            this.className = className;
            this.resourceId = resourceId;
            this.text = text;
            this.bounds = bounds;
        }

        boolean interactiveClass() {
            if (!hasText(className)) {
                return false;
            }
            String value = className.toLowerCase(Locale.US);
            return value.contains("button") || value.contains("edittext") || value.contains("check") || value.contains("switch") || value.contains("radio") || value.contains("tab") || value.contains("imagebutton") || value.contains("toggle");
        }

        boolean interesting() {
            return hasText(text) || hasText(resourceId) || interactiveClass();
        }

        String id() {
            return "native:" + index;
        }

        String displayText() {
            if (hasText(text)) {
                return text;
            }
            String hint = resourceHint(resourceId);
            return hint != null ? hint : id();
        }
    }

    private static final class Signal {
        @Nullable final String rawId;
        final String kind;
        @Nullable final String type;
        @Nullable final String label;
        @Nullable final String role;
        final Bounds bounds;
        final double confidence;
        final double importance;
        double bestScore;
        @Nullable NativeNode bestNode;
        @Nullable NativeNode matchedNode;

        Signal(@Nullable String rawId, String kind, @Nullable String type, @Nullable String label, @Nullable String role, Bounds bounds, double confidence, double importance) {
            this.rawId = rawId;
            this.kind = kind;
            this.type = type;
            this.label = label;
            this.role = role;
            this.bounds = bounds;
            this.confidence = confidence;
            this.importance = importance;
        }

        boolean isControl() {
            return "control".equals(kind);
        }

        boolean actionable(@Nullable String targetHint) {
            if (isControl()) {
                return true;
            }
            if (hasText(role)) {
                String value = role.toLowerCase(Locale.US);
                if ("primary_action".equals(value) || "secondary_action".equals(value) || "input".equals(value) || "navigation".equals(value)) {
                    return true;
                }
            }
            return hintScore(targetHint, label, type, role) >= 0.55d;
        }

        String id() {
            return "vision:" + kind + ":" + (rawId == null ? "unknown" : rawId);
        }

        String displayText() {
            if (hasText(label)) {
                return label;
            }
            if (hasText(type)) {
                return type;
            }
            return id();
        }
    }

    private static final class ScoredJson {
        final double score;
        final String key;
        final JSONObject object;

        ScoredJson(double score, String key, JSONObject object) {
            this.score = score;
            this.key = key;
            this.object = object;
        }
    }

    private static final class Bounds {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int area() {
            return Math.max(1, (right - left) * (bottom - top));
        }

        boolean containsCenter(Bounds other) {
            int centerX = (other.left + other.right) / 2;
            int centerY = (other.top + other.bottom) / 2;
            return centerX >= left && centerX <= right && centerY >= top && centerY <= bottom;
        }

        String value() {
            return "[" + left + "," + top + "][" + right + "," + bottom + "]";
        }

        @Nullable
        static Bounds parse(@Nullable String value) {
            if (!hasText(value)) {
                return null;
            }
            try {
                int a = value.indexOf('[');
                int b = value.indexOf(',', a);
                int c = value.indexOf(']', b);
                int d = value.indexOf('[', c);
                int e = value.indexOf(',', d);
                int f = value.indexOf(']', e);
                int left = Integer.parseInt(value.substring(a + 1, b));
                int top = Integer.parseInt(value.substring(b + 1, c));
                int right = Integer.parseInt(value.substring(d + 1, e));
                int bottom = Integer.parseInt(value.substring(e + 1, f));
                return right > left && bottom > top ? new Bounds(left, top, right, bottom) : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        @Nullable
        static Bounds fromArray(@Nullable JSONArray array) {
            if (array == null || array.length() != 4) {
                return null;
            }
            int left = array.optInt(0, Integer.MIN_VALUE);
            int top = array.optInt(1, Integer.MIN_VALUE);
            int right = array.optInt(2, Integer.MIN_VALUE);
            int bottom = array.optInt(3, Integer.MIN_VALUE);
            return left != Integer.MIN_VALUE && top != Integer.MIN_VALUE && right > left && bottom > top
                    ? new Bounds(left, top, right, bottom)
                    : null;
        }
    }
}