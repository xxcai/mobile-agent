package com.hh.agent.app.manifest;

import com.hh.agent.android.route.NativeRouteRegistry;
import com.hh.agent.android.route.NativeRouteRegistryEntry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RouteManifestLoader {
    static final String MANIFEST_DIR = "mobile_agent/manifests";

    private final RouteManifestAssetSource assetSource;
    private final RouteManifestParser parser;

    public RouteManifestLoader(RouteManifestAssetSource assetSource) {
        this(assetSource, new RouteManifestParser());
    }

    RouteManifestLoader(RouteManifestAssetSource assetSource, RouteManifestParser parser) {
        if (assetSource == null) {
            throw new IllegalArgumentException("assetSource cannot be null");
        }
        if (parser == null) {
            throw new IllegalArgumentException("parser cannot be null");
        }
        this.assetSource = assetSource;
        this.parser = parser;
    }

    public List<RouteManifest> loadManifests() throws IOException {
        String[] fileNames = assetSource.list(MANIFEST_DIR);
        if (fileNames == null || fileNames.length == 0) {
            return Collections.emptyList();
        }

        List<String> sortedNames = new ArrayList<>(Arrays.asList(fileNames));
        Collections.sort(sortedNames);
        List<RouteManifest> manifests = new ArrayList<>();
        for (String fileName : sortedNames) {
            if (fileName == null || !fileName.endsWith(".json")) {
                continue;
            }
            manifests.add(parser.parse(readAsset(MANIFEST_DIR + "/" + fileName)));
        }
        return Collections.unmodifiableList(manifests);
    }

    public NativeRouteRegistry loadNativeRouteRegistry() throws IOException {
        List<NativeRouteRegistryEntry> entries = new ArrayList<>();
        for (RouteManifest manifest : loadManifests()) {
            entries.addAll(manifest.toNativeRouteRegistry().getEntries());
        }
        return new NativeRouteRegistry(entries);
    }

    private String readAsset(String path) throws IOException {
        try (InputStream inputStream = assetSource.open(path);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
