package com.hh.agent.android.route;

import java.util.regex.Pattern;

final class RouteUriFormat {
    private static final Pattern URI_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.+$");

    private RouteUriFormat() {
    }

    static boolean isValid(String uri) {
        return uri != null && URI_PATTERN.matcher(uri.trim()).matches();
    }
}
