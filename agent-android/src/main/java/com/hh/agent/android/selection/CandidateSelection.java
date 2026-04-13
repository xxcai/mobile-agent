package com.hh.agent.android.selection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CandidateSelection {
    private final String domain;
    private final List<CandidateSelectionItem> items;

    private CandidateSelection(String domain, List<CandidateSelectionItem> items) {
        this.domain = normalizeText(domain);
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static CandidateSelection indexed(String domain, List<CandidateSelectionItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items cannot be null or empty");
        }
        return new CandidateSelection(domain, items);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        put(json, "type", "indexed_candidates");
        if (domain != null) {
            put(json, "domain", domain);
        }
        put(json, "supportsOrdinalSelection", true);
        put(json, "supportsAliasSelection", true);
        JSONArray itemsJson = new JSONArray();
        for (CandidateSelectionItem item : items) {
            itemsJson.put(item.toJson());
        }
        put(json, "items", itemsJson);
        return json;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize candidate selection field: " + key, exception);
        }
    }
}
