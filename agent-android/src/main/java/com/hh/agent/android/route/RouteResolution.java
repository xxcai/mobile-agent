package com.hh.agent.android.route;

import com.hh.agent.android.selection.CandidateSelection;
import com.hh.agent.android.selection.CandidateSelectionItem;
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
    private final CandidateSelection candidateSelection;
    private final JSONObject diagnostics;

    private RouteResolution(String status,
                            RouteTarget recommendedTarget,
                            List<RouteTarget> candidates,
                            CandidateSelection candidateSelection,
                            JSONObject diagnostics) {
        this.status = status;
        this.recommendedTarget = recommendedTarget;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.candidateSelection = candidateSelection;
        this.diagnostics = diagnostics == null ? new JSONObject() : diagnostics;
    }

    public static RouteResolution resolved(RouteTarget target) {
        return new RouteResolution(STATUS_RESOLVED, target, Collections.emptyList(), null, null);
    }

    public static RouteResolution candidates(List<RouteTarget> targets) {
        List<RouteTarget> safeTargets = safeList(targets);
        return new RouteResolution(
                STATUS_CANDIDATES,
                null,
                safeTargets,
                buildCandidateSelection(safeTargets),
                null);
    }

    public static RouteResolution notFound(JSONObject diagnostics) {
        return new RouteResolution(STATUS_NOT_FOUND, null, Collections.emptyList(), null, diagnostics);
    }

    public static RouteResolution insufficientHint(JSONObject diagnostics) {
        return new RouteResolution(STATUS_INSUFFICIENT_HINT, null, Collections.emptyList(), null, diagnostics);
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
            if (candidateSelection != null) {
                put(json, "candidateSelection", candidateSelection.toJson());
            }
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

    private static CandidateSelection buildCandidateSelection(List<RouteTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        List<CandidateSelectionItem> items = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            RouteTarget target = targets.get(index);
            CandidateSelectionItem.Builder builder = new CandidateSelectionItem.Builder(
                    index + 1,
                    target.getTitle() == null ? target.getUri() : target.getTitle())
                    .stableKey(target.getTargetType() + ":" + target.getUri())
                    .payload(target.toJson())
                    .alias(target.getTitle())
                    .alias(target.getUri())
                    .alias(lastSegment(target.getUri()));
            items.add(builder.build());
        }
        return CandidateSelection.indexed("route", items);
    }

    private static String lastSegment(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        int slashIndex = uri.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < uri.length() - 1) {
            return uri.substring(slashIndex + 1);
        }
        int schemeIndex = uri.indexOf("://");
        if (schemeIndex >= 0 && schemeIndex < uri.length() - 3) {
            return uri.substring(schemeIndex + 3);
        }
        return uri;
    }
}
