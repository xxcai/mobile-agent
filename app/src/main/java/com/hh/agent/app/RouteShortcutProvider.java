package com.hh.agent.app;

import com.hh.agent.android.route.AllowAllUriAccessPolicy;
import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.NativeRouteRegistry;
import com.hh.agent.android.route.NoOpRouteScorer;
import com.hh.agent.android.route.QuerySourceBackedWeCodeRouteBridge;
import com.hh.agent.android.route.RegistryBackedNativeRouteBridge;
import com.hh.agent.android.route.RouteResolver;
import com.hh.agent.android.route.WeCodeQuerySource;
import com.hh.agent.app.manifest.AndroidRouteManifestAssetSource;
import com.hh.agent.app.manifest.ManifestBackedRouteModuleResolver;
import com.hh.agent.app.manifest.ManifestBackedRouteUriComposer;
import com.hh.agent.app.manifest.RouteManifestAssetSource;
import com.hh.agent.app.manifest.RouteManifestLoader;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.shortcut.OpenResolvedRouteShortcut;
import com.hh.agent.shortcut.ResolveRouteShortcut;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class RouteShortcutProvider {

    private RouteShortcutProvider() {
    }

    public static List<ShortcutExecutor> createShortcuts(android.content.Context context) {
        AndroidRouteManifestAssetSource assetSource = new AndroidRouteManifestAssetSource(context);
        NativeRouteRegistry nativeRouteRegistry = createNativeRouteRegistry(assetSource);
        ManifestBackedRouteModuleResolver routeModuleResolver = createRouteModuleResolver(assetSource);
        ManifestBackedRouteUriComposer routeUriComposer = createRouteUriComposer(assetSource);
        WeCodeQuerySource weCodeQuerySource = DefaultMockWeCodeQuerySource.create();
        AndroidRouteRuntime routeRuntime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        new RegistryBackedNativeRouteBridge(nativeRouteRegistry),
                        new QuerySourceBackedWeCodeRouteBridge(weCodeQuerySource)),
                new DemoHostRouteInvoker(context));

        List<ShortcutExecutor> shortcuts = new ArrayList<>();
        shortcuts.add(new ResolveRouteShortcut(routeRuntime, routeModuleResolver));
        shortcuts.add(new OpenResolvedRouteShortcut(routeRuntime, routeUriComposer));
        return shortcuts;
    }

    static NativeRouteRegistry createNativeRouteRegistry(android.content.Context context) {
        return createNativeRouteRegistry(new AndroidRouteManifestAssetSource(context));
    }

    static NativeRouteRegistry createNativeRouteRegistry(RouteManifestAssetSource assetSource) {
        try {
            return new RouteManifestLoader(assetSource)
                    .loadNativeRouteRegistry();
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load route manifests", exception);
        }
    }

    static ManifestBackedRouteUriComposer createRouteUriComposer(RouteManifestAssetSource assetSource) {
        try {
            return ManifestBackedRouteUriComposer.fromAssetSource(assetSource);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to build route uri composer", exception);
        }
    }

    static ManifestBackedRouteModuleResolver createRouteModuleResolver(RouteManifestAssetSource assetSource) {
        try {
            return ManifestBackedRouteModuleResolver.fromAssetSource(assetSource);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to build route module resolver", exception);
        }
    }
}
