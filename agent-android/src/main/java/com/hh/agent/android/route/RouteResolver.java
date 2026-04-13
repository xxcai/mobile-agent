package com.hh.agent.android.route;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Step 06 resolver skeleton. Bridge-based search paths are added in later steps.
 */
public final class RouteResolver {
    private final UriAccessPolicy uriAccessPolicy;
    private final RouteScorer routeScorer;
    private final NativeRouteBridge nativeRouteBridge;
    private final WeCodeRouteBridge weCodeRouteBridge;

    public RouteResolver(UriAccessPolicy uriAccessPolicy,
                         RouteScorer routeScorer,
                         NativeRouteBridge nativeRouteBridge,
                         WeCodeRouteBridge weCodeRouteBridge) {
        this.uriAccessPolicy = uriAccessPolicy == null ? new AllowAllUriAccessPolicy() : uriAccessPolicy;
        this.routeScorer = routeScorer == null ? new NoOpRouteScorer() : routeScorer;
        this.nativeRouteBridge = nativeRouteBridge;
        this.weCodeRouteBridge = weCodeRouteBridge;
    }

    public RouteResolution resolve(RouteHint routeHint) {
        RouteHint normalizedHint = routeHint == null ? RouteHint.empty() : routeHint;
        if (!normalizedHint.isSearchable()) {
            return RouteResolution.insufficientHint(diagnostics("reason", "missing_searchable_fields"));
        }

        if (normalizedHint.getUri() != null) {
            UriAccessDecision decision = uriAccessPolicy.evaluate(normalizedHint.getUri());
            if (!decision.isAllowed()) {
                return RouteResolution.insufficientHint(diagnostics("reason", decision.getReason()));
            }
            RouteTarget target = new RouteTarget.Builder()
                    .targetType(resolveTargetType(normalizedHint))
                    .uri(normalizedHint.getUri())
                    .title(generateTitleFromUri(normalizedHint.getUri()))
                    .source("direct_uri")
                    .matchMode("uri_direct")
                    .build();
            List<RouteTarget> scoredTargets =
                    routeScorer.scoreAndSort(Collections.singletonList(target), normalizedHint);
            return RouteResolution.resolved(scoredTargets.get(0));
        }

        List<RouteTarget> candidates = collectCandidates(normalizedHint);
        if (candidates.isEmpty()) {
            return RouteResolution.notFound(diagnostics("reason", "bridge_not_connected_or_no_match"));
        }
        List<RouteTarget> scoredTargets = routeScorer.scoreAndSort(candidates, normalizedHint);
        if (scoredTargets.size() == 1) {
            return RouteResolution.resolved(scoredTargets.get(0));
        }
        return RouteResolution.candidates(scoredTargets);
    }

    private String resolveTargetType(RouteHint routeHint) {
        if (RouteHint.TARGET_TYPE_NATIVE.equals(routeHint.getTargetTypeHint())) {
            return RouteHint.TARGET_TYPE_NATIVE;
        }
        if (RouteHint.TARGET_TYPE_WECODE.equals(routeHint.getTargetTypeHint())) {
            return RouteHint.TARGET_TYPE_WECODE;
        }
        return RouteHint.TARGET_TYPE_UNKNOWN;
    }

    private String generateTitleFromUri(String uri) {
        int slashIndex = uri.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < uri.length() - 1) {
            return uri.substring(slashIndex + 1);
        }
        return uri;
    }

    private List<RouteTarget> collectCandidates(RouteHint routeHint) {
        List<RouteTarget> candidates = new ArrayList<>();

        if (nativeRouteBridge != null && shouldSearchNative(routeHint)) {
            if (routeHint.getNativeModule() != null) {
                candidates.addAll(mapNative(nativeRouteBridge.searchByModule(
                        routeHint.getNativeModule(),
                        routeHint.getKeywords())));
            } else if (!routeHint.getKeywords().isEmpty()) {
                candidates.addAll(mapNative(nativeRouteBridge.searchByKeywords(routeHint.getKeywords())));
            }
        }

        if (weCodeRouteBridge != null && shouldSearchWeCode(routeHint)) {
            String weCodeQuery = selectWeCodeQuery(routeHint);
            if (weCodeQuery != null) {
                candidates.addAll(mapWeCode(weCodeRouteBridge.search(weCodeQuery)));
            }
        }

        return candidates;
    }

    private List<RouteTarget> mapNative(List<NativeRouteRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<RouteTarget> targets = new ArrayList<>();
        for (NativeRouteRecord record : records) {
            targets.add(new RouteTarget.Builder()
                    .targetType(RouteHint.TARGET_TYPE_NATIVE)
                    .uri(record.getUri())
                    .title(record.getTitle())
                    .source("native_bridge")
                    .matchMode("bridge_search")
                    .build());
        }
        return targets;
    }

    private List<RouteTarget> mapWeCode(List<WeCodeRouteRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<RouteTarget> targets = new ArrayList<>();
        for (WeCodeRouteRecord record : records) {
            targets.add(new RouteTarget.Builder()
                    .targetType(RouteHint.TARGET_TYPE_WECODE)
                    .uri(record.getUri())
                    .title(record.getTitle())
                    .source("wecode_bridge")
                    .matchMode("bridge_search")
                    .build());
        }
        return targets;
    }

    private String selectWeCodeQuery(RouteHint routeHint) {
        if (routeHint.getWeCodeName() != null) {
            return routeHint.getWeCodeName();
        }
        if (!routeHint.getKeywords().isEmpty()) {
            return routeHint.getKeywords().get(0);
        }
        return null;
    }

    private boolean shouldSearchNative(RouteHint routeHint) {
        return !RouteHint.TARGET_TYPE_WECODE.equals(routeHint.getTargetTypeHint());
    }

    private boolean shouldSearchWeCode(RouteHint routeHint) {
        return !RouteHint.TARGET_TYPE_NATIVE.equals(routeHint.getTargetTypeHint());
    }

    private JSONObject diagnostics(String key, String value) {
        JSONObject diagnostics = new JSONObject();
        try {
            diagnostics.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize route diagnostics", exception);
        }
        return diagnostics;
    }
}
