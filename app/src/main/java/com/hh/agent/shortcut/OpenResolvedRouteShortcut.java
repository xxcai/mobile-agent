package com.hh.agent.shortcut;

import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.RouteOpenResult;
import com.hh.agent.android.route.RouteTarget;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

/**
 * Shortcut that opens a previously resolved route target.
 */
public final class OpenResolvedRouteShortcut implements ShortcutExecutor {
    private final AndroidRouteRuntime routeRuntime;

    public OpenResolvedRouteShortcut(AndroidRouteRuntime routeRuntime) {
        if (routeRuntime == null) {
            throw new IllegalArgumentException("routeRuntime cannot be null");
        }
        this.routeRuntime = routeRuntime;
    }

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder(
                        "open_resolved_route",
                        "打开已解析目标",
                        "根据已解析的 targetType、uri、title 打开目标页面")
                .domain("route")
                .requiredSkill("route_navigator")
                .risk("navigate")
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
