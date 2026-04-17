package com.screenvision.sdk;

public final class ScreenVisionSdkInfo {
    private final String sdkVersion;
    private final String backendName;
    private final boolean offlineOnly;
    private final boolean packagedModel;

    public ScreenVisionSdkInfo(String sdkVersion, String backendName, boolean offlineOnly, boolean packagedModel) {
        this.sdkVersion = sdkVersion;
        this.backendName = backendName;
        this.offlineOnly = offlineOnly;
        this.packagedModel = packagedModel;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public String getBackendName() {
        return backendName;
    }

    public boolean isOfflineOnly() {
        return offlineOnly;
    }

    public boolean isPackagedModel() {
        return packagedModel;
    }
}

