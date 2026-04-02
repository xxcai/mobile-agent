package com.hh.agent.shortcut;

import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.RouteHint;
import com.hh.agent.android.route.RouteResolution;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Shortcut that resolves route hints into structured route targets.
 */
public final class ResolveRouteShortcut implements ShortcutExecutor {
    private final AndroidRouteRuntime routeRuntime;

    public ResolveRouteShortcut(AndroidRouteRuntime routeRuntime) {
        if (routeRuntime == null) {
            throw new IllegalArgumentException("routeRuntime cannot be null");
        }
        this.routeRuntime = routeRuntime;
    }

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder(
                        "resolve_route",
                        "解析跳转目标",
                        "根据 URI、原生模块、we码名称或关键词解析跳转目标")
                .domain("route")
                .requiredSkill("route_navigator")
                .argsSchema("{\"type\":\"object\",\"properties\":{\"targetTypeHint\":{\"type\":\"string\",\"description\":\"可选，native/wecode/unknown\"},\"uri\":{\"type\":\"string\",\"description\":\"已知精确 URI\"},\"nativeModule\":{\"type\":\"string\",\"description\":\"已知原生模块\"},\"weCodeName\":{\"type\":\"string\",\"description\":\"已知 we码名称\"},\"keywords_csv\":{\"type\":\"string\",\"description\":\"可选关键词，多个用英文逗号分隔\"}},\"required\":[]}")
                .argsExample("{\"targetTypeHint\":\"wecode\",\"weCodeName\":\"报销\",\"keywords_csv\":\"报销,费用报销\"}")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            RouteHint routeHint = RouteHint.fromJson(expandKeywords(args));
            RouteResolution result = routeRuntime.resolve(routeHint);
            return ToolResult.success().withJson("result", result.toJson().toString());
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }

    private JSONObject expandKeywords(JSONObject args) {
        try {
            JSONObject normalized = new JSONObject(args.toString());
            String keywordsCsv = normalized.optString("keywords_csv", null);
            if (keywordsCsv == null || keywordsCsv.trim().isEmpty()) {
                return normalized;
            }
            JSONArray keywords = new JSONArray();
            for (String part : keywordsCsv.split(",")) {
                keywords.put(part);
            }
            normalized.remove("keywords_csv");
            normalized.put("keywords", keywords);
            return normalized;
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to expand keywords_csv", exception);
        }
    }
}
