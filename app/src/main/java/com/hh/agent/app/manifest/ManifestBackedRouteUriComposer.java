package com.hh.agent.app.manifest;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ManifestBackedRouteUriComposer {
    private final Map<String, Map<String, RouteManifestParam>> paramsByPath;

    private ManifestBackedRouteUriComposer(Map<String, Map<String, RouteManifestParam>> paramsByPath) {
        this.paramsByPath = paramsByPath;
    }

    public static ManifestBackedRouteUriComposer fromAssetSource(RouteManifestAssetSource assetSource) throws IOException {
        List<RouteManifest> manifests = new RouteManifestLoader(assetSource).loadManifests();
        Map<String, Map<String, RouteManifestParam>> paramsByPath = new HashMap<>();
        for (RouteManifest manifest : manifests) {
            for (RouteManifestRoute route : manifest.getRoutes()) {
                Map<String, RouteManifestParam> params = new LinkedHashMap<>();
                for (RouteManifestParam param : route.getParams()) {
                    params.put(param.getName(), param);
                }
                paramsByPath.put(route.getPath(), params);
            }
        }
        return new ManifestBackedRouteUriComposer(paramsByPath);
    }

    public String compose(String baseUri, JSONObject routeArgs) {
        String normalizedBaseUri = requireText(baseUri, "baseUri");
        if (routeArgs == null || routeArgs.length() == 0) {
            return normalizedBaseUri;
        }

        Map<String, RouteManifestParam> knownParams = paramsByPath.get(normalizedBaseUri);
        if (knownParams == null) {
            throw new IllegalArgumentException("No route manifest found for uri: " + normalizedBaseUri);
        }

        StringBuilder builder = new StringBuilder(normalizedBaseUri);
        boolean hasQuery = normalizedBaseUri.contains("?");
        Iterator<String> keys = routeArgs.keys();
        while (keys.hasNext()) {
            String paramName = keys.next();
            RouteManifestParam manifestParam = knownParams.get(paramName);
            if (manifestParam == null) {
                throw new IllegalArgumentException("Unknown route param: " + paramName);
            }
            JSONObject paramArg = routeArgs.optJSONObject(paramName);
            if (paramArg == null) {
                throw new IllegalArgumentException("routeArgs." + paramName + " must be an object");
            }

            if (!paramArg.has("value")) {
                throw new IllegalArgumentException("routeArgs." + paramName + ".value is required");
            }

            String rawValue = paramArg.isNull("value") ? null : String.valueOf(paramArg.opt("value"));
            if (rawValue == null) {
                continue;
            }

            String finalValue = applyEncoding(manifestParam, paramArg, rawValue);
            builder.append(hasQuery ? '&' : '?');
            hasQuery = true;
            builder.append(paramName).append('=').append(finalValue);
        }
        return builder.toString();
    }

    private String applyEncoding(RouteManifestParam manifestParam, JSONObject paramArg, String rawValue) {
        RouteManifestEncoding encoding = manifestParam.getEncoding();
        if (encoding == null) {
            return rawValue;
        }

        if (!paramArg.has("encoded")) {
            throw new IllegalArgumentException("routeArgs." + manifestParam.getName() + ".encoded is required");
        }
        boolean alreadyEncoded = paramArg.optBoolean("encoded", false);
        if (alreadyEncoded) {
            return rawValue;
        }
        switch (encoding) {
            case URL:
                return URLEncoder.encode(rawValue, StandardCharsets.UTF_8);
            case BASE64:
                return Base64.getEncoder().encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
            case BASE64URL:
                return Base64.getUrlEncoder().encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
            default:
                throw new IllegalStateException("Unsupported encoding: " + encoding);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value.trim();
    }
}
