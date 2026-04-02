package com.hh.agent.android.selection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CandidateSelectionItem {
    private final int index;
    private final String label;
    private final String stableKey;
    private final List<String> aliases;
    private final JSONObject payload;

    private CandidateSelectionItem(Builder builder) {
        this.index = builder.index;
        this.label = requireText(builder.label, "label");
        this.stableKey = normalizeText(builder.stableKey);
        this.aliases = Collections.unmodifiableList(new ArrayList<>(builder.aliases));
        this.payload = builder.payload == null ? new JSONObject() : copy(builder.payload);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        put(json, "index", index);
        put(json, "label", label);
        if (stableKey != null) {
            put(json, "stableKey", stableKey);
        }
        if (!aliases.isEmpty()) {
            JSONArray aliasesJson = new JSONArray();
            for (String alias : aliases) {
                aliasesJson.put(alias);
            }
            put(json, "aliases", aliasesJson);
        }
        if (payload.length() > 0) {
            put(json, "payload", payload);
        }
        return json;
    }

    public static final class Builder {
        private final int index;
        private final String label;
        private String stableKey;
        private final Set<String> aliases = new LinkedHashSet<>();
        private JSONObject payload;

        public Builder(int index, String label) {
            if (index <= 0) {
                throw new IllegalArgumentException("index must be positive");
            }
            this.index = index;
            this.label = label;
        }

        public Builder stableKey(String stableKey) {
            this.stableKey = stableKey;
            return this;
        }

        public Builder alias(String alias) {
            String normalized = normalizeText(alias);
            if (normalized != null) {
                aliases.add(normalized);
            }
            return this;
        }

        public Builder payload(JSONObject payload) {
            this.payload = payload;
            return this;
        }

        public CandidateSelectionItem build() {
            return new CandidateSelectionItem(this);
        }
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to copy candidate selection payload", exception);
        }
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize candidate selection item field: " + key, exception);
        }
    }
}
