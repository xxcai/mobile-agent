package com.hh.agent.app;

import com.hh.agent.android.route.AllowAllUriAccessPolicy;
import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.NoOpRouteScorer;
import com.hh.agent.android.route.RouteResolver;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.tool.ResolveRouteTool;

import java.util.HashMap;
import java.util.Map;

public final class RouteToolProvider {
    private RouteToolProvider() {
    }

    public static Map<String, ToolExecutor> createRouteTools() {
        AndroidRouteRuntime routeRuntime = new AndroidRouteRuntime(
                new RouteResolver(
                        new AllowAllUriAccessPolicy(),
                        new NoOpRouteScorer(),
                        new MockNativeRouteBridge(),
                        new MockMiniAppRouteBridge()),
                new NoOpHostRouteInvoker());
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("resolve_route", new ResolveRouteTool(routeRuntime));
        return tools;
    }
}
