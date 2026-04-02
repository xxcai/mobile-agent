package com.hh.agent.app;

import com.hh.agent.android.route.AllowAllUriAccessPolicy;
import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.NativeRouteRegistry;
import com.hh.agent.android.route.NoOpRouteScorer;
import com.hh.agent.android.route.RegistryBackedNativeRouteBridge;
import com.hh.agent.android.route.RouteResolver;
import com.hh.agent.android.route.QuerySourceBackedWeCodeRouteBridge;
import com.hh.agent.android.route.WeCodeQuerySource;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.shortcut.OpenResolvedRouteShortcut;
import com.hh.agent.shortcut.ResolveRouteShortcut;

import java.util.ArrayList;
import java.util.List;

public final class RouteShortcutProvider {

    private RouteShortcutProvider() {
    }

    public static List<ShortcutExecutor> createShortcuts(android.content.Context context) {
        NativeRouteRegistry nativeRouteRegistry = DefaultNativeRouteRegistry.create();
        WeCodeQuerySource weCodeQuerySource = DefaultMockWeCodeQuerySource.create();
        AndroidRouteRuntime routeRuntime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        new RegistryBackedNativeRouteBridge(nativeRouteRegistry),
                        new QuerySourceBackedWeCodeRouteBridge(weCodeQuerySource)),
                new DemoHostRouteInvoker(context));

        List<ShortcutExecutor> shortcuts = new ArrayList<>();
        shortcuts.add(new ResolveRouteShortcut(routeRuntime));
        shortcuts.add(new OpenResolvedRouteShortcut(routeRuntime));
        return shortcuts;
    }
}
