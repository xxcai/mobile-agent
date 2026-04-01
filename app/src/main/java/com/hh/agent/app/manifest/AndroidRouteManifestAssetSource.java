package com.hh.agent.app.manifest;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

public final class AndroidRouteManifestAssetSource implements RouteManifestAssetSource {
    private final AssetManager assetManager;

    public AndroidRouteManifestAssetSource(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        this.assetManager = context.getAssets();
    }

    @Override
    public String[] list(String path) throws IOException {
        return assetManager.list(path);
    }

    @Override
    public InputStream open(String path) throws IOException {
        return assetManager.open(path);
    }
}
