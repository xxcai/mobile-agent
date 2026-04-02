package com.hh.agent.shortcut;

import com.hh.agent.android.route.AndroidRouteRuntime;
import com.hh.agent.android.route.RouteOpenResult;
import com.hh.agent.android.route.RouteTarget;
import com.hh.agent.android.route.manifest.ManifestBackedRouteUriComposer;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

/**
 * Shortcut that opens a previously resolved route target.
 */
public final class OpenResolvedRouteShortcut implements ShortcutExecutor {
    private final AndroidRouteRuntime routeRuntime;
    private final ManifestBackedRouteUriComposer routeUriComposer;

    public OpenResolvedRouteShortcut(AndroidRouteRuntime routeRuntime,
                                     ManifestBackedRouteUriComposer routeUriComposer) {
        if (routeRuntime == null) {
            throw new IllegalArgumentException("routeRuntime cannot be null");
        }
        if (routeUriComposer == null) {
            throw new IllegalArgumentException("routeUriComposer cannot be null");
        }
        this.routeRuntime = routeRuntime;
        this.routeUriComposer = routeUriComposer;
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
                .argsSchema("{\"type\":\"object\",\"properties\":{\"targetType\":{\"type\":\"string\",\"description\":\"native 或 wecode\"},\"uri\":{\"type\":\"string\",\"description\":\"已解析的基础目标 URI\"},\"title\":{\"type\":\"string\",\"description\":\"已解析的目标标题\"},\"routeArgs\":{\"type\":\"object\",\"description\":\"可选，按参数名传入对象。每项格式为 {\\\"value\\\":\\\"...\\\",\\\"encoded\\\":true|false}；当 manifest 声明了 encode 时必须显式提供 encoded。\"}},\"required\":[\"targetType\",\"uri\",\"title\"]}")
                .argsExample("{\"targetType\":\"wecode\",\"uri\":\"h5://1001001\",\"title\":\"费控报销\"}")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            String finalUri = routeUriComposer.compose(
                    args.optString("uri", null),
                    args.optJSONObject("routeArgs"));
            RouteTarget target = new RouteTarget.Builder()
                    .targetType(args.optString("targetType", null))
                    .uri(finalUri)
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
