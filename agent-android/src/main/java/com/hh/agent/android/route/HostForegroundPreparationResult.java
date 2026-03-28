package com.hh.agent.android.route;

/**
 * Result of preparing the host foreground before route opening.
 */
public final class HostForegroundPreparationResult {
    private final boolean success;
    private final String errorCode;
    private final boolean containerDismissed;
    private final boolean hostActivityReady;

    private HostForegroundPreparationResult(boolean success,
                                            String errorCode,
                                            boolean containerDismissed,
                                            boolean hostActivityReady) {
        this.success = success;
        this.errorCode = errorCode;
        this.containerDismissed = containerDismissed;
        this.hostActivityReady = hostActivityReady;
    }

    public static HostForegroundPreparationResult success(boolean containerDismissed,
                                                          boolean hostActivityReady) {
        return new HostForegroundPreparationResult(true, null, containerDismissed, hostActivityReady);
    }

    public static HostForegroundPreparationResult failure(String errorCode,
                                                          boolean containerDismissed,
                                                          boolean hostActivityReady) {
        return new HostForegroundPreparationResult(false, errorCode, containerDismissed, hostActivityReady);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isContainerDismissed() {
        return containerDismissed;
    }

    public boolean isHostActivityReady() {
        return hostActivityReady;
    }
}
