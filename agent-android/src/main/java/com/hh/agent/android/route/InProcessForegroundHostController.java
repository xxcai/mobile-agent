package com.hh.agent.android.route;

import android.app.Activity;

import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.floating.FloatingBallLifecycleCallbacks;

/**
 * In-process foreground host controller that reuses the existing floating-ball lifecycle tracking.
 */
public final class InProcessForegroundHostController implements ForegroundHostController {
    private static final long CONTAINER_DISMISS_TIMEOUT_MS = 1500L;
    private static final long FOREGROUND_STABLE_TIMEOUT_MS = 1500L;

    @Override
    public HostForegroundPreparationResult prepareHostForegroundForRoute() {
        Activity currentForeground = FloatingBallLifecycleCallbacks.getCurrentForegroundActivity();
        if (currentForeground == null) {
            return HostForegroundPreparationResult.failure(
                    "host_activity_not_stable",
                    false,
                    false
            );
        }

        boolean containerDismissed = false;
        Activity stableForeground;
        if (currentForeground instanceof ContainerActivity) {
            containerDismissed = dismissContainerActivity(currentForeground);
            if (!containerDismissed) {
                return HostForegroundPreparationResult.failure(
                        "container_not_dismissed",
                        false,
                        false
                );
            }
            stableForeground = FloatingBallLifecycleCallbacks.awaitNextStableForegroundActivity(
                    true,
                    CONTAINER_DISMISS_TIMEOUT_MS
            );
        } else {
            stableForeground = FloatingBallLifecycleCallbacks.awaitNextStableForegroundActivity(
                    false,
                    FOREGROUND_STABLE_TIMEOUT_MS
            );
        }

        if (stableForeground == null) {
            return HostForegroundPreparationResult.failure(
                    "host_activity_not_stable",
                    containerDismissed,
                    false
            );
        }

        return HostForegroundPreparationResult.success(containerDismissed, true);
    }

    private boolean dismissContainerActivity(Activity activity) {
        try {
            activity.runOnUiThread(activity::finish);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
