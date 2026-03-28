package com.hh.agent.android.route;

/**
 * Route opener skeleton used in Step 08. Real tool wiring lands later.
 */
public final class RouteOpener {
    private final ForegroundHostController foregroundHostController;
    private final HostRouteInvoker hostRouteInvoker;

    public RouteOpener(ForegroundHostController foregroundHostController,
                       HostRouteInvoker hostRouteInvoker) {
        if (foregroundHostController == null) {
            throw new IllegalArgumentException("foregroundHostController cannot be null");
        }
        this.foregroundHostController = foregroundHostController;
        this.hostRouteInvoker = hostRouteInvoker;
    }

    public RouteOpenResult open(RouteTarget target) {
        if (!isValidTarget(target)) {
            return RouteOpenResult.failure("invalid_target", "target is missing required fields", target, false, false);
        }

        HostForegroundPreparationResult preparationResult =
                foregroundHostController.prepareHostForegroundForRoute();
        if (!preparationResult.isSuccess()) {
            return RouteOpenResult.failure(
                    preparationResult.getErrorCode(),
                    "host foreground preparation failed",
                    target,
                    preparationResult.isContainerDismissed(),
                    preparationResult.isHostActivityReady()
            );
        }

        if (hostRouteInvoker == null) {
            return RouteOpenResult.success(
                    target,
                    preparationResult.isContainerDismissed(),
                    preparationResult.isHostActivityReady()
            );
        }

        try {
            hostRouteInvoker.open(target.getUri());
            return RouteOpenResult.success(
                    target,
                    preparationResult.isContainerDismissed(),
                    preparationResult.isHostActivityReady()
            );
        } catch (Exception exception) {
            return RouteOpenResult.failure(
                    "open_uri_failed",
                    exception.getMessage(),
                    target,
                    preparationResult.isContainerDismissed(),
                    preparationResult.isHostActivityReady()
            );
        }
    }

    private boolean isValidTarget(RouteTarget target) {
        return target != null
                && hasText(target.getTargetType())
                && hasText(target.getUri())
                && hasText(target.getTitle());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
