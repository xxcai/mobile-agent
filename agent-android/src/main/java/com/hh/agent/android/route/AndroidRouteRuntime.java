package com.hh.agent.android.route;

/**
 * Route runtime facade used by route tools.
 */
public final class AndroidRouteRuntime {
    private final RouteResolver routeResolver;
    private final HostRouteInvoker hostRouteInvoker;

    public AndroidRouteRuntime(RouteResolver routeResolver, HostRouteInvoker hostRouteInvoker) {
        if (routeResolver == null) {
            throw new IllegalArgumentException("routeResolver cannot be null");
        }
        this.routeResolver = routeResolver;
        this.hostRouteInvoker = hostRouteInvoker;
    }

    public RouteResolution resolve(RouteHint routeHint) {
        return routeResolver.resolve(routeHint);
    }

    public HostRouteInvoker getHostRouteInvoker() {
        return hostRouteInvoker;
    }
}
