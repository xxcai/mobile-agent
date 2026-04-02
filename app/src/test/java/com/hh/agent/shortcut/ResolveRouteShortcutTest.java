package com.hh.agent.shortcut;

import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.AllowAllUriAccessPolicy;
import com.hh.agent.android.route.NativeRouteBridge;
import com.hh.agent.android.route.NativeRouteRecord;
import com.hh.agent.android.route.NoOpHostRouteInvoker;
import com.hh.agent.android.route.NoOpRouteScorer;
import com.hh.agent.android.route.RouteOpener;
import com.hh.agent.android.route.RouteResolver;
import com.hh.agent.android.route.WeCodeRouteBridge;
import com.hh.agent.android.route.manifest.ManifestBackedRouteModuleResolver;
import com.hh.agent.app.manifest.InMemoryRouteManifestAssetSource;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ResolveRouteShortcutTest {

    @Test
    public void execute_infersNativeModuleFromManifestKeywordsBeforeResolving() throws Exception {
        CapturingNativeRouteBridge nativeRouteBridge = new CapturingNativeRouteBridge();
        ResolveRouteShortcut shortcut = new ResolveRouteShortcut(
                runtime(nativeRouteBridge),
                ManifestBackedRouteModuleResolver.fromAssetSource(
                        new InMemoryRouteManifestAssetSource("app.json")
                                .addFile("mobile_agent/manifests/app.json", "{\n"
                                        + "  \"module\": \"myapp.im\",\n"
                                        + "  \"routes\": [{\"path\": \"ui://myapp.im/createGroup\", \"keywords\": [\"IM\", \"群聊\", \"建群\"]}]\n"
                                        + "}")));

        shortcut.execute(new JSONObject().put("keywords_csv", "创建群聊,建群"));

        assertEquals("myapp.im", nativeRouteBridge.lastModule);
        assertEquals("创建群聊", nativeRouteBridge.lastKeywords.get(0));
        assertEquals("建群", nativeRouteBridge.lastKeywords.get(1));
    }

    @Test
    public void execute_keepsGlobalKeywordSearchWhenModuleCannotBeInferred() throws Exception {
        CapturingNativeRouteBridge nativeRouteBridge = new CapturingNativeRouteBridge();
        ResolveRouteShortcut shortcut = new ResolveRouteShortcut(
                runtime(nativeRouteBridge),
                ManifestBackedRouteModuleResolver.fromAssetSource(
                        new InMemoryRouteManifestAssetSource("a.json", "b.json")
                                .addFile("mobile_agent/manifests/a.json", "{\n"
                                        + "  \"module\": \"myapp.login\",\n"
                                        + "  \"routes\": [{\"path\": \"ui://myapp.login/resetPassword\", \"keywords\": [\"密码\"]}]\n"
                                        + "}")
                                .addFile("mobile_agent/manifests/b.json", "{\n"
                                        + "  \"module\": \"myapp.settings\",\n"
                                        + "  \"routes\": [{\"path\": \"ui://myapp.settings/changePassword\", \"keywords\": [\"密码\"]}]\n"
                                        + "}")));

        shortcut.execute(new JSONObject().put("keywords_csv", "密码"));

        assertEquals(null, nativeRouteBridge.lastModule);
        assertEquals("密码", nativeRouteBridge.lastGlobalKeywords.get(0));
    }

    private AndroidRouteRuntime runtime(NativeRouteBridge nativeRouteBridge) {
        RouteResolver routeResolver = new RouteResolver(
                new AllowAllUriAccessPolicy(),
                new NoOpRouteScorer(),
                nativeRouteBridge,
                emptyWeCodeBridge());
        RouteOpener routeOpener = new RouteOpener(
                () -> com.hh.agent.android.route.HostForegroundPreparationResult.success(true, true),
                new NoOpHostRouteInvoker());
        return new AndroidRouteRuntime(routeResolver, routeOpener, new NoOpHostRouteInvoker());
    }

    private WeCodeRouteBridge emptyWeCodeBridge() {
        return query -> Collections.emptyList();
    }

    private static final class CapturingNativeRouteBridge implements NativeRouteBridge {
        private String lastModule;
        private List<String> lastKeywords;
        private List<String> lastGlobalKeywords;

        @Override
        public List<NativeRouteRecord> findByUri(String uri) {
            return Collections.emptyList();
        }

        @Override
        public List<NativeRouteRecord> searchByModule(String module, List<String> keywords) {
            this.lastModule = module;
            this.lastKeywords = keywords;
            return Collections.singletonList(new NativeRouteRecord(
                    "ui://myapp.im/createGroup",
                    module,
                    "createGroup",
                    "创建群聊页面"));
        }

        @Override
        public List<NativeRouteRecord> searchByKeywords(List<String> keywords) {
            this.lastGlobalKeywords = keywords;
            return Collections.emptyList();
        }
    }
}
