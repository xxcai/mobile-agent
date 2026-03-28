package com.hh.agent.android.route;

/**
 * Route runtime facade used by route tools.
 */
public final class AndroidRouteRuntime {
    private final RouteResolver routeResolver;
    private final HostRouteInvoker hostRouteInvoker;
    private final RouteOpener routeOpener;

    public AndroidRouteRuntime(RouteResolver routeResolver, HostRouteInvoker hostRouteInvoker) {
        this(routeResolver, hostRouteInvoker != null ? new RouteOpener(new InProcessForegroundHostController(), hostRouteInvoker) : null,
                hostRouteInvoker);
    }

    public AndroidRouteRuntime(RouteResolver routeResolver,
                               RouteOpener routeOpener,
                               HostRouteInvoker hostRouteInvoker) {
        if (routeResolver == null) {
            throw new IllegalArgumentException("routeResolver cannot be null");
        }
        this.routeResolver = routeResolver;
        this.routeOpener = routeOpener;
        this.hostRouteInvoker = hostRouteInvoker;
    }

    public RouteResolution resolve(RouteHint routeHint) {
        return routeResolver.resolve(routeHint);
    }

    public HostRouteInvoker getHostRouteInvoker() {
        return hostRouteInvoker;
    }

    public RouteOpenResult open(RouteTarget target) {
        if (routeOpener == null) {
            throw new IllegalStateException("routeOpener is not configured");
        }
        return routeOpener.open(target);
    }
}
