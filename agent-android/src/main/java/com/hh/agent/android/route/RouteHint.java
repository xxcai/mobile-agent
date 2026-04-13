package com.hh.agent.android.route;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalized route hint passed from skill/tool input into route runtime.
 */
public final class RouteHint {
    public static final String TARGET_TYPE_NATIVE = "native";
    public static final String TARGET_TYPE_WECODE = "wecode";
    public static final String TARGET_TYPE_UNKNOWN = "unknown";

    private final String targetTypeHint;
    private final String uri;
    private final String nativeModule;
    private final String weCodeName;
    private final List<String> keywords;

    private RouteHint(String targetTypeHint,
                      String uri,
                      String nativeModule,
                      String weCodeName,
                      List<String> keywords) {
        this.targetTypeHint = normalizeTargetType(targetTypeHint);
        this.uri = normalizeText(uri);
        this.nativeModule = normalizeText(nativeModule);
        this.weCodeName = normalizeText(weCodeName);
        this.keywords = Collections.unmodifiableList(new ArrayList<>(keywords));
    }

    public static RouteHint fromJson(JSONObject routeHintJson) {
        if (routeHintJson == null) {
            return empty();
        }
        return new RouteHint(
                routeHintJson.optString("targetTypeHint", TARGET_TYPE_UNKNOWN),
                routeHintJson.optString("uri", null),
                routeHintJson.optString("nativeModule", null),
                routeHintJson.optString("weCodeName", null),
                normalizeKeywords(routeHintJson.optJSONArray("keywords"))
        );
    }

    public static RouteHint empty() {
        return new RouteHint(TARGET_TYPE_UNKNOWN, null, null, null, Collections.emptyList());
    }

    public String getTargetTypeHint() {
        return targetTypeHint;
    }

    public String getUri() {
        return uri;
    }

    public String getNativeModule() {
        return nativeModule;
    }

    public String getWeCodeName() {
        return weCodeName;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public boolean isSearchable() {
        return uri != null
                || nativeModule != null
                || weCodeName != null
                || !keywords.isEmpty();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        put(json, "targetTypeHint", targetTypeHint);
        putIfPresent(json, "uri", uri);
        putIfPresent(json, "nativeModule", nativeModule);
        putIfPresent(json, "weCodeName", weCodeName);
        JSONArray keywordsJson = new JSONArray();
        for (String keyword : keywords) {
            keywordsJson.put(keyword);
        }
        put(json, "keywords", keywordsJson);
        return json;
    }

    private static List<String> normalizeKeywords(JSONArray source) {
        if (source == null || source.length() == 0) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (int index = 0; index < source.length(); index++) {
            String value = normalizeText(source.optString(index, null));
            if (value == null) {
                continue;
            }
            normalized.add(value);
            if (normalized.size() >= 5) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private static String normalizeTargetType(String raw) {
        String normalized = normalizeText(raw);
        if (TARGET_TYPE_NATIVE.equals(normalized) || TARGET_TYPE_WECODE.equals(normalized)) {
            return normalized;
        }
        return TARGET_TYPE_UNKNOWN;
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void putIfPresent(JSONObject json, String key, String value) {
        if (value != null) {
            put(json, key, value);
        }
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize RouteHint field: " + key, exception);
        }
    }
}
