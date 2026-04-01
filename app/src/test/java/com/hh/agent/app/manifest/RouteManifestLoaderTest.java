package com.hh.agent.app.manifest;

import com.hh.agent.android.route.NativeRouteRegistry;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RouteManifestLoaderTest {

    @Test
    public void loadManifests_parsesValidManifestAndFiltersNonJson() throws Exception {
        InMemoryRouteManifestAssetSource assetSource = new InMemoryRouteManifestAssetSource(
                "notes.txt", "b.json", "a.json")
                .addFile("mobile_agent/manifests/a.json", "{\n"
                        + "  \"module\": \"module.a\",\n"
                        + "  \"routes\": [{\"path\": \"ui://a/home\", \"description\": \"A首页\"}]\n"
                        + "}")
                .addFile("mobile_agent/manifests/b.json", "{\n"
                        + "  \"module\": \"module.b\",\n"
                        + "  \"routes\": [{\"path\": \"ui://b/detail\", \"params\": [{\"name\": \"payload\", \"encode\": \"base64\"}]}]\n"
                        + "}");

        RouteManifestLoader loader = new RouteManifestLoader(assetSource);
        List<RouteManifest> manifests = loader.loadManifests();

        assertEquals(2, manifests.size());
        assertEquals("module.a", manifests.get(0).getModule());
        assertEquals("ui://a/home", manifests.get(0).getRoutes().get(0).getPath());
        assertEquals("module.b", manifests.get(1).getModule());
        assertEquals(RouteManifestEncoding.BASE64,
                manifests.get(1).getRoutes().get(0).getParams().get(0).getEncoding());
    }

    @Test
    public void loadNativeRouteRegistry_flattensManifestRoutes() throws Exception {
        InMemoryRouteManifestAssetSource assetSource = new InMemoryRouteManifestAssetSource("app.json")
                .addFile("mobile_agent/manifests/app.json", "{\n"
                        + "  \"module\": \"myapp.app\",\n"
                        + "  \"routes\": [\n"
                        + "    {\"path\": \"ui://myapp.im/createGroup\", \"description\": \"创建群聊页面\", \"keywords\": [\"IM\", \"群聊\"]},\n"
                        + "    {\"path\": \"ui://myapp.expense/records\", \"description\": \"报销记录页面\", \"params\": [{\"name\": \"payload\", \"required\": true}]}\n"
                        + "  ]\n"
                        + "}");

        NativeRouteRegistry registry = new RouteManifestLoader(assetSource).loadNativeRouteRegistry();

        assertEquals(2, registry.getEntries().size());
        assertEquals("ui://myapp.im/createGroup", registry.getEntries().get(0).getUri());
        assertEquals("myapp.app", registry.getEntries().get(0).getModule());
        assertEquals("报销记录页面", registry.getEntries().get(1).getDescription());
    }

    @Test
    public void loadManifests_parsesKeywordsAsNormalizedDistinctList() throws Exception {
        InMemoryRouteManifestAssetSource assetSource = new InMemoryRouteManifestAssetSource("app.json")
                .addFile("mobile_agent/manifests/app.json", "{\n"
                        + "  \"module\": \"myapp.app\",\n"
                        + "  \"routes\": [{\"path\": \"ui://myapp.im/createGroup\", \"keywords\": [\"IM\", \"群聊\", \"IM\", \"  建群  \"]}]\n"
                        + "}");

        RouteManifest manifest = new RouteManifestLoader(assetSource).loadManifests().get(0);

        assertEquals(3, manifest.getRoutes().get(0).getKeywords().size());
        assertEquals("IM", manifest.getRoutes().get(0).getKeywords().get(0));
        assertEquals("群聊", manifest.getRoutes().get(0).getKeywords().get(1));
        assertEquals("建群", manifest.getRoutes().get(0).getKeywords().get(2));
    }

    @Test
    public void parse_defaultsToNoEncodingWhenEncodeMissing() throws Exception {
        InMemoryRouteManifestAssetSource assetSource = new InMemoryRouteManifestAssetSource("app.json")
                .addFile("mobile_agent/manifests/app.json", "{\n"
                        + "  \"module\": \"myapp.app\",\n"
                        + "  \"routes\": [{\"path\": \"ui://myapp.search/selectActivity\", \"params\": [{\"name\": \"source\"}]}]\n"
                        + "}");

        RouteManifest manifest = new RouteManifestLoader(assetSource).loadManifests().get(0);

        assertNull(manifest.getRoutes().get(0).getParams().get(0).getEncoding());
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadManifests_rejectsMissingModule() throws Exception {
        loadSingle("{\"routes\":[]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadManifests_rejectsMissingRoutes() throws Exception {
        loadSingle("{\"module\":\"myapp.app\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadManifests_rejectsBlankRoutePath() throws Exception {
        loadSingle("{\"module\":\"myapp.app\",\"routes\":[{\"path\":\"   \"}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadManifests_rejectsBlankParamName() throws Exception {
        loadSingle("{\"module\":\"myapp.app\",\"routes\":[{\"path\":\"ui://a\",\"params\":[{\"name\":\"\"}]}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadManifests_rejectsUnsupportedEncode() throws Exception {
        loadSingle("{\"module\":\"myapp.app\",\"routes\":[{\"path\":\"ui://a\",\"params\":[{\"name\":\"payload\",\"encode\":\"gzip\"}]}]}");
    }

    @Test
    public void loadManifests_returnsEmptyWhenDirectoryMissing() throws Exception {
        InMemoryRouteManifestAssetSource assetSource = new InMemoryRouteManifestAssetSource();

        List<RouteManifest> manifests = new RouteManifestLoader(assetSource).loadManifests();

        assertEquals(0, manifests.size());
    }

    private void loadSingle(String json) throws IOException {
        InMemoryRouteManifestAssetSource assetSource = new InMemoryRouteManifestAssetSource("app.json")
                .addFile("mobile_agent/manifests/app.json", json);
        new RouteManifestLoader(assetSource).loadManifests();
    }
}
