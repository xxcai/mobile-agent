package com.hh.agent.android.route.manifest;

import com.hh.agent.android.route.NativeRouteRegistry;
import com.hh.agent.android.route.NativeRouteRegistryEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RouteManifest {
    private final String module;
    private final List<RouteManifestRoute> routes;

    RouteManifest(String module, List<RouteManifestRoute> routes) {
        this.module = requireText(module, "module");
        if (routes == null) {
            throw new IllegalArgumentException("routes cannot be null");
        }
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
    }

    public String getModule() {
        return module;
    }

    public List<RouteManifestRoute> getRoutes() {
        return routes;
    }

    public NativeRouteRegistry toNativeRouteRegistry() {
        List<NativeRouteRegistryEntry> entries = new ArrayList<>();
        for (RouteManifestRoute route : routes) {
            entries.add(new NativeRouteRegistryEntry(
                    route.getPath(),
                    module,
                    route.getDescription(),
                    route.getKeywords()));
        }
        return new NativeRouteRegistry(entries);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value.trim();
    }
}
