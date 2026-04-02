package com.hh.agent.android.route.manifest;

public enum RouteManifestEncoding {
    URL("url"),
    BASE64("base64"),
    BASE64URL("base64url");

    private final String wireValue;

    RouteManifestEncoding(String wireValue) {
        this.wireValue = wireValue;
    }

    String getWireValue() {
        return wireValue;
    }

    static RouteManifestEncoding fromWireValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (RouteManifestEncoding encoding : values()) {
            if (encoding.wireValue.equals(normalized)) {
                return encoding;
            }
        }
        throw new IllegalArgumentException("Unsupported param encode: " + value);
    }
}
