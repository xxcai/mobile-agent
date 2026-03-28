package com.hh.agent.app;

import com.hh.agent.android.route.AllowAllUriAccessPolicy;
import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.NoOpRouteScorer;
import com.hh.agent.android.route.RouteResolver;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.tool.OpenResolvedRouteTool;
import com.hh.agent.tool.ResolveRouteTool;

import java.util.HashMap;
import java.util.Map;

public final class RouteToolProvider {
    private RouteToolProvider() {
    }

    public static Map<String, ToolExecutor> createRouteTools(android.content.Context context) {
        NativeRouteRegistry nativeRouteRegistry = DefaultNativeRouteRegistry.create();
        MiniAppQuerySource miniAppQuerySource = DefaultMockMiniAppQuerySource.create();
        AndroidRouteRuntime routeRuntime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        new RegistryBackedNativeRouteBridge(nativeRouteRegistry),
                        new QuerySourceBackedMiniAppRouteBridge(miniAppQuerySource)),
                new DemoHostRouteInvoker(context));
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("resolve_route", new ResolveRouteTool(routeRuntime));
        tools.put("open_resolved_route", new OpenResolvedRouteTool(routeRuntime));
        return tools;
    }
}
