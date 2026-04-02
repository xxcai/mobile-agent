package com.hh.agent.android.route.manifest;

import java.io.IOException;
import java.io.InputStream;

public interface RouteManifestAssetSource {
    String[] list(String path) throws IOException;

    InputStream open(String path) throws IOException;
}
