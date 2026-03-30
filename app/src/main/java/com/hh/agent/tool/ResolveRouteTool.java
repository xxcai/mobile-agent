package com.hh.agent.tool;

import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.RouteHint;
import com.hh.agent.android.route.RouteResolution;
import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Legacy ToolExecutor-based route resolver retained for compatibility/debug paths.
 * New route runtime integration should prefer ResolveRouteShortcut.
 */
@Deprecated
public final class ResolveRouteTool implements ToolExecutor {
    private final AndroidRouteRuntime routeRuntime;

    public ResolveRouteTool(AndroidRouteRuntime routeRuntime) {
        if (routeRuntime == null) {
            throw new IllegalArgumentException("routeRuntime cannot be null");
        }
        this.routeRuntime = routeRuntime;
    }

    @Override
    public String getName() {
        return "resolve_route";
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder("解析跳转目标", "根据 URI、原生模块、小程序名称或关键词解析跳转目标")
                .intentExamples("打开报销入口", "进入创建群聊页面", "打开 ui://myapp.search/selectActivity")
                .stringParam("targetTypeHint", "可选，native/miniapp/unknown", false, "miniapp")
                .stringParam("uri", "已知精确 URI", false, "ui://myapp.search/selectActivity")
                .stringParam("nativeModule", "已知原生模块", false, "myapp.im")
                .stringParam("miniAppName", "已知小程序名称", false, "报销")
                .stringParam("keywords_csv", "可选关键词，多个用英文逗号分隔", false, "报销,费用报销")
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
