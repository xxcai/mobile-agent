package com.hh.agent.app.manifest;

import com.hh.agent.android.route.manifest.RouteManifestAssetSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryRouteManifestAssetSource implements RouteManifestAssetSource {
    private final String[] listing;
    private final Map<String, String> files = new LinkedHashMap<>();

    public InMemoryRouteManifestAssetSource(String... listing) {
        this.listing = listing;
    }

    public InMemoryRouteManifestAssetSource addFile(String path, String content) {
        files.put(path, content);
        return this;
    }

    @Override
    public String[] list(String path) {
        return listing;
    }

    @Override
    public InputStream open(String path) throws IOException {
        String content = files.get(path);
        if (content == null) {
            throw new IOException("Missing asset: " + path);
        }
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
