package com.hh.agent.tool;

import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.RouteOpenResult;
import com.hh.agent.android.route.RouteTarget;
import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

/**
 * Legacy ToolExecutor-based route opener retained for compatibility/debug paths.
 * New route runtime integration should prefer OpenResolvedRouteShortcut.
 */
@Deprecated
public final class OpenResolvedRouteTool implements ToolExecutor {
    private final AndroidRouteRuntime routeRuntime;

    public OpenResolvedRouteTool(AndroidRouteRuntime routeRuntime) {
        if (routeRuntime == null) {
            throw new IllegalArgumentException("routeRuntime cannot be null");
        }
        this.routeRuntime = routeRuntime;
    }

    @Override
    public String getName() {
        return "open_resolved_route";
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder("打开已解析目标", "根据已解析的 targetType、uri、title 打开目标页面")
                .intentExamples("打开已经解析好的报销页面", "打开刚刚解析出的创建群聊页面")
                .stringParam("targetType", "native 或 miniapp", true, "miniapp")
                .stringParam("uri", "已解析的目标 URI", true, "h5://1001001")
                .stringParam("title", "已解析的目标标题", true, "费控报销")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            RouteTarget target = new RouteTarget.Builder()
                    .targetType(args.optString("targetType", null))
                    .uri(args.optString("uri", null))
                    .title(args.optString("title", null))
                    .build();
            RouteOpenResult result = routeRuntime.open(target);
            if (result.isSuccess()) {
                return ToolResult.success().withJson("result", result.toJson().toString());
            }
            JSONObject resultJson = result.toJson();
            return ToolResult.error(result.getErrorCode(), resultJson.optString("message", null))
                    .withJson("result", resultJson.toString());
        } catch (IllegalArgumentException exception) {
            return ToolResult.error("invalid_target", exception.getMessage());
        } catch (Exception exception) {
            return ToolResult.error("execution_failed", exception.getMessage());
        }
    }
}
