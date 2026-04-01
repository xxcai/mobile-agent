package com.hh.agent.app.manifest;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ManifestBackedRouteModuleResolver {
    private final List<RouteManifest> manifests;

    private ManifestBackedRouteModuleResolver(List<RouteManifest> manifests) {
        this.manifests = manifests;
    }

    public static ManifestBackedRouteModuleResolver fromAssetSource(RouteManifestAssetSource assetSource)
            throws IOException {
        return new ManifestBackedRouteModuleResolver(new RouteManifestLoader(assetSource).loadManifests());
    }

    public String inferModule(List<String> inputKeywords) {
        if (inputKeywords == null || inputKeywords.isEmpty()) {
            return null;
        }
        Set<String> matchedModules = new LinkedHashSet<>();
        for (RouteManifest manifest : manifests) {
            if (matchesManifest(manifest, inputKeywords)) {
                matchedModules.add(manifest.getModule());
            }
        }
        return matchedModules.size() == 1 ? matchedModules.iterator().next() : null;
    }

    private boolean matchesManifest(RouteManifest manifest, List<String> inputKeywords) {
        for (RouteManifestRoute route : manifest.getRoutes()) {
            if (matchesRoute(route, inputKeywords)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRoute(RouteManifestRoute route, List<String> inputKeywords) {
        if (route.getKeywords().isEmpty()) {
            return false;
        }
        for (String inputKeyword : inputKeywords) {
            String normalizedInput = normalize(inputKeyword);
            if (normalizedInput == null) {
                continue;
            }
            for (String routeKeyword : route.getKeywords()) {
                String normalizedRouteKeyword = normalize(routeKeyword);
                if (normalizedRouteKeyword == null) {
                    continue;
                }
                if (normalizedInput.contains(normalizedRouteKeyword)
                        || normalizedRouteKeyword.contains(normalizedInput)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
