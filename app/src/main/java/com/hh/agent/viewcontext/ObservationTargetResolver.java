package com.hh.agent.viewcontext;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ObservationTargetResolver {

    private static final Pattern NODE_MATCH_PATTERN = Pattern.compile(
            "<node[^>]*index=\\\"([^\\\"]+)\\\"[^>]*text=\\\"([^\\\"]*)\\\"[^>]*bounds=\\\"([^\\\"]+)\\\"[^>]*/?>");

    private ObservationTargetResolver() {
    }

    @Nullable
    public static TargetReference resolve(@Nullable JSONObject viewContextJson,
                                          @Nullable String targetHint) {
        if (viewContextJson == null) {
            return null;
        }

        TargetReference hybridMatch = resolveFromHybrid(
                viewContextJson.optJSONObject("hybridObservation"),
                targetHint
        );
        if (hybridMatch != null) {
            return hybridMatch;
        }

        return resolveFromNativeXml(viewContextJson.optString("nativeViewXml", ""), targetHint);
    }

    @Nullable
    private static TargetReference resolveFromHybrid(@Nullable JSONObject hybridObservation,
                                                     @Nullable String targetHint) {
        if (hybridObservation == null) {
            return null;
        }

        JSONArray actionableNodes = hybridObservation.optJSONArray("actionableNodes");
        if (actionableNodes == null || actionableNodes.length() == 0) {
            return null;
        }

        String normalizedHint = normalize(targetHint);
        TargetReference best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < actionableNodes.length(); i++) {
            JSONObject node = actionableNodes.optJSONObject(i);
            if (node == null) {
                continue;
            }

            String bounds = boundsFrom(node);
            if (!hasText(bounds)) {
                continue;
            }

            double score = Math.max(0d, node.optDouble("score", 0d));
            String source = trimToNull(node.optString("source", null));
            if ("fused".equals(source)) {
                score += 0.25d;
            } else if ("native".equals(source)) {
                score += 0.15d;
            } else if ("vision_only".equals(source)) {
                score -= 0.10d;
            }
            if (node.has("nativeNodeIndex") && !node.isNull("nativeNodeIndex")) {
                score += 0.08d;
            }

            String text = trimToNull(node.optString("text", null));
            String visionLabel = trimToNull(node.optString("visionLabel", null));
            String resourceId = trimToNull(node.optString("resourceId", null));
            String className = trimToNull(node.optString("className", null));
            String visionType = trimToNull(node.optString("visionType", null));

            double hintScore = hintScore(normalizedHint, text, visionLabel, resourceId, className, visionType);
            if (!normalizedHint.isEmpty()) {
                if (hintScore < 0.42d) {
                    continue;
                }
                score += hintScore;
            }

            Integer nativeNodeIndex = node.has("nativeNodeIndex") && !node.isNull("nativeNodeIndex")
                    ? Integer.valueOf(node.optInt("nativeNodeIndex"))
                    : null;
            TargetReference candidate = new TargetReference(
                    nativeNodeIndex,
                    bounds,
                    source != null ? source : "hybrid_actionable",
                    firstNonEmpty(text, visionLabel, resourceHint(resourceId), visionType),
                    visionType
            );
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    @Nullable
    private static TargetReference resolveFromNativeXml(@Nullable String nativeViewXml,
                                                        @Nullable String targetHint) {
        if (!hasText(nativeViewXml)) {
            return null;
        }

        String normalizedHint = normalize(targetHint);
        Matcher matcher = NODE_MATCH_PATTERN.matcher(nativeViewXml);
        while (matcher.find()) {
            String nodeText = matcher.group(2);
            if (!normalizedHint.isEmpty() && textMatch(normalizedHint, normalize(nodeText)) < 0.72d) {
                continue;
            }
            try {
                return new TargetReference(
                        Integer.valueOf(Integer.parseInt(matcher.group(1))),
                        matcher.group(3),
                        "native_xml_fallback",
                        trimToNull(nodeText),
                        null
                );
            } catch (NumberFormatException ignored) {
                return new TargetReference(null, matcher.group(3), "native_xml_fallback", trimToNull(nodeText), null);
            }
        }
        return null;
    }

    @Nullable
    private static String boundsFrom(JSONObject object) {
        String bounds = trimToNull(object.optString("bounds", null));
        if (bounds != null) {
            return bounds;
        }
        JSONArray bbox = object.optJSONArray("bbox");
        if (bbox == null || bbox.length() < 4) {
            return null;
        }
        return "[" + bbox.optInt(0) + "," + bbox.optInt(1) + "]["
                + bbox.optInt(2) + "," + bbox.optInt(3) + "]";
    }

    private static double hintScore(String normalizedHint, @Nullable String... values) {
        if (normalizedHint.isEmpty() || values == null) {
            return 0d;
        }
        double best = 0d;
        for (String value : values) {
            best = Math.max(best, textMatch(normalizedHint, normalize(firstNonEmpty(value, resourceHint(value)))));
        }
        return best;
    }

    private static double textMatch(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        if (left.equals(right)) {
            return 1d;
        }
        if (left.contains(right) || right.contains(left)) {
            return 0.88d;
        }
        Set<Character> overlap = new LinkedHashSet<>();
        for (int i = 0; i < left.length(); i++) {
            char c = left.charAt(i);
            if (right.indexOf(c) >= 0) {
                overlap.add(c);
            }
        }
        double coverage = overlap.size() / (double) Math.max(left.length(), right.length());
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

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalize(@Nullable String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.US)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .replace("\n", "")
                .replace("\r", "");
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
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

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public static final class TargetReference {
        @Nullable public final Integer nodeIndex;
        public final String bounds;
        public final String source;
        @Nullable public final String matchedText;
        @Nullable public final String visionType;

        public TargetReference(@Nullable Integer nodeIndex,
                               String bounds,
                               String source,
                               @Nullable String matchedText,
                               @Nullable String visionType) {
            this.nodeIndex = nodeIndex;
            this.bounds = bounds;
            this.source = source;
            this.matchedText = matchedText;
            this.visionType = visionType;
        }
    }
}