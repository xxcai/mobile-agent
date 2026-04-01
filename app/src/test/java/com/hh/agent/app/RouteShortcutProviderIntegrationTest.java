package com.hh.agent.app;

import com.hh.agent.android.route.NativeRouteRegistry;
import com.hh.agent.app.manifest.ManifestBackedRouteUriComposer;
import com.hh.agent.app.manifest.RouteManifestAssetSource;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class RouteShortcutProviderIntegrationTest {

    @Test
    public void createNativeRouteRegistry_usesManifestLoaderOutput() throws Exception {
        TestFileRouteManifestAssetSource assetSource = new TestFileRouteManifestAssetSource(
                Paths.get("src/main/assets/mobile_agent/manifests"));

        NativeRouteRegistry registry = RouteShortcutProvider.createNativeRouteRegistry(assetSource);

        assertEquals(3, registry.getEntries().size());
        assertEquals("ui://myapp.im/createGroup", registry.getEntries().get(0).getUri());
        assertEquals("myapp.app", registry.getEntries().get(0).getModule());
        assertEquals("ui://myapp.expense/records", registry.getEntries().get(2).getUri());
    }

    @Test
    public void createRouteUriComposer_buildsComposerFromManifestAssets() throws Exception {
        TestFileRouteManifestAssetSource assetSource = new TestFileRouteManifestAssetSource(
                Paths.get("src/main/assets/mobile_agent/manifests"));

        ManifestBackedRouteUriComposer composer = RouteShortcutProvider.createRouteUriComposer(assetSource);
        String uri = composer.compose("ui://myapp.im/createGroup", new JSONObject()
                .put("source", new JSONObject()
                        .put("value", "agent card")
                        .put("encoded", false)));

        assertEquals("ui://myapp.im/createGroup?source=agent+card", uri);
    }

    private static final class TestFileRouteManifestAssetSource implements RouteManifestAssetSource {
        private final Path manifestDir;

        private TestFileRouteManifestAssetSource(Path manifestDir) {
            this.manifestDir = manifestDir;
        }

        @Override
        public String[] list(String path) throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(manifestDir)) {
                return java.util.stream.StreamSupport.stream(stream.spliterator(), false)
                        .map(file -> file.getFileName().toString())
                        .sorted()
                        .toArray(String[]::new);
            }
        }

        @Override
        public InputStream open(String path) throws IOException {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            return Files.newInputStream(manifestDir.resolve(fileName));
        }
    }
}
