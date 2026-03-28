package com.hh.agent.android.route;

/**
 * Default policy used during early integration. Allows any URI that passes format validation.
 */
public final class AllowAllUriAccessPolicy implements UriAccessPolicy {

    @Override
    public UriAccessDecision evaluate(String uri) {
        return RouteUriFormat.isValid(uri)
                ? UriAccessDecision.allowed()
                : UriAccessDecision.denied("invalid_uri_format");
    }
}
