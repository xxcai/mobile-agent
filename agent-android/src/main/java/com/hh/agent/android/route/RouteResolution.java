package com.hh.agent.android.route;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured route resolution result emitted by route runtime.
 */
public final class RouteResolution {
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_CANDIDATES = "candidates";
    public static final String STATUS_NOT_FOUND = "not_found";
    public static final String STATUS_INSUFFICIENT_HINT = "insufficient_hint";

    private final String status;
    private final RouteTarget recommendedTarget;
    private final List<RouteTarget> candidates;
    private final JSONObject diagnostics;

    private RouteResolution(String status,
                            RouteTarget recommendedTarget,
                            List<RouteTarget> candidates,
                            JSONObject diagnostics) {
        this.status = status;
        this.recommendedTarget = recommendedTarget;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.diagnostics = diagnostics == null ? new JSONObject() : diagnostics;
    }

    public static RouteResolution resolved(RouteTarget target) {
        return new RouteResolution(STATUS_RESOLVED, target, Collections.emptyList(), null);
    }

    public static RouteResolution candidates(List<RouteTarget> targets) {
        return new RouteResolution(STATUS_CANDIDATES, null, safeList(targets), null);
    }

    public static RouteResolution notFound(JSONObject diagnostics) {
        return new RouteResolution(STATUS_NOT_FOUND, null, Collections.emptyList(), diagnostics);
    }

    public static RouteResolution insufficientHint(JSONObject diagnostics) {
        return new RouteResolution(STATUS_INSUFFICIENT_HINT, null, Collections.emptyList(), diagnostics);
    }

    public String getStatus() {
        return status;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        put(json, "status", status);
        if (recommendedTarget != null) {
            put(json, "recommendedTarget", recommendedTarget.toJson());
        }
        if (!candidates.isEmpty()) {
            JSONArray candidatesJson = new JSONArray();
            for (RouteTarget candidate : candidates) {
                candidatesJson.put(candidate.toJson());
            }
            put(json, "candidates", candidatesJson);
        }
        if (diagnostics.length() > 0) {
            put(json, "diagnostics", diagnostics);
        }
        return json;
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize RouteResolution field: " + key, exception);
        }
    }

    private static List<RouteTarget> safeList(List<RouteTarget> targets) {
        if (targets == null) {
            return Collections.emptyList();
        }
        return targets;
    }
}
