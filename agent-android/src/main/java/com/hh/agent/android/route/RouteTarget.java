package com.hh.agent.android.route;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured route target returned by resolver and consumed by opener.
 */
public final class RouteTarget {
    private final String targetType;
    private final String uri;
    private final String title;
    private final String source;
    private final String matchMode;
    private final Double confidence;
    private final List<String> scoreReasons;

    private RouteTarget(Builder builder) {
        this.targetType = builder.targetType;
        this.uri = builder.uri;
        this.title = builder.title;
        this.source = builder.source;
        this.matchMode = builder.matchMode;
        this.confidence = builder.confidence;
        this.scoreReasons = Collections.unmodifiableList(new ArrayList<>(builder.scoreReasons));
    }

    public String getTargetType() {
        return targetType;
    }

    public String getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        put(json, "targetType", targetType);
        put(json, "uri", uri);
        put(json, "title", title);
        if (source != null) {
            put(json, "source", source);
        }
        if (matchMode != null) {
            put(json, "matchMode", matchMode);
        }
        if (confidence != null) {
            put(json, "confidence", confidence);
        }
        if (!scoreReasons.isEmpty()) {
            JSONArray reasonsJson = new JSONArray();
            for (String reason : scoreReasons) {
                reasonsJson.put(reason);
            }
            put(json, "scoreReasons", reasonsJson);
        }
        return json;
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize RouteTarget field: " + key, exception);
        }
    }

    public static final class Builder {
        private String targetType;
        private String uri;
        private String title;
        private String source;
        private String matchMode;
        private Double confidence;
        private final List<String> scoreReasons = new ArrayList<>();

        public Builder targetType(String targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder matchMode(String matchMode) {
            this.matchMode = matchMode;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder addScoreReason(String reason) {
            if (reason != null && !reason.trim().isEmpty()) {
                scoreReasons.add(reason.trim());
            }
            return this;
        }

        public RouteTarget build() {
            validateText(targetType, "targetType");
            validateText(uri, "uri");
            validateText(title, "title");
            return new RouteTarget(this);
        }

        private static void validateText(String value, String fieldName) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(fieldName + " cannot be null or empty");
            }
        }
    }
}
