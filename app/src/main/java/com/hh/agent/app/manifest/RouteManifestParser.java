package com.hh.agent.app.manifest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class RouteManifestParser {

    RouteManifest parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("manifest json cannot be null or empty");
        }
        try {
            JSONObject root = new JSONObject(json);
            String module = requireText(root, "module");
            JSONArray routesJson = root.optJSONArray("routes");
            if (routesJson == null) {
                throw new IllegalArgumentException("routes must be a JSON array");
            }

            List<RouteManifestRoute> routes = new ArrayList<>();
            for (int i = 0; i < routesJson.length(); i++) {
                JSONObject routeJson = requireObject(routesJson, i, "route");
                routes.add(parseRoute(routeJson));
            }
            return new RouteManifest(module, routes);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Failed to parse route manifest json", exception);
        }
    }

    private RouteManifestRoute parseRoute(JSONObject routeJson) throws JSONException {
        String path = requireText(routeJson, "path");
        String description = normalizeText(routeJson.optString("description", null));
        JSONArray paramsJson = routeJson.optJSONArray("params");
        List<RouteManifestParam> params = new ArrayList<>();
        if (paramsJson != null) {
            for (int i = 0; i < paramsJson.length(); i++) {
                JSONObject paramJson = requireObject(paramsJson, i, "param");
                params.add(parseParam(paramJson));
            }
        }
        return new RouteManifestRoute(path, description, params);
    }

    private RouteManifestParam parseParam(JSONObject paramJson) throws JSONException {
        String name = requireText(paramJson, "name");
        boolean required = paramJson.optBoolean("required", false);
        String description = normalizeText(paramJson.optString("description", null));
        RouteManifestEncoding encoding = RouteManifestEncoding.fromWireValue(
                normalizeText(paramJson.optString("encode", null)));
        return new RouteManifestParam(name, required, description, encoding);
    }

    private static JSONObject requireObject(JSONArray array, int index, String label) throws JSONException {
        JSONObject object = array.optJSONObject(index);
        if (object == null) {
            throw new IllegalArgumentException(label + " at index " + index + " must be a JSON object");
        }
        return object;
    }

    private static String requireText(JSONObject object, String fieldName) {
        String value = normalizeText(object.optString(fieldName, null));
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
