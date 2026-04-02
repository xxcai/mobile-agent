package com.hh.agent.android.webaction;

import com.hh.agent.android.channel.WebActionToolChannel;
import com.hh.agent.android.viewcontext.ViewObservationSnapshot;
import com.hh.agent.android.viewcontext.ViewObservationSnapshotRegistry;
import com.hh.agent.android.web.WebDomScriptFactory;
import com.hh.agent.android.web.WebViewJsBridge;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

public final class RealWebActionExecutor implements WebActionExecutor {

    private final WebViewJsBridge jsBridge;

    public RealWebActionExecutor(WebViewJsBridge jsBridge) {
        this.jsBridge = jsBridge;
    }

    public static WebActionExecutor createDefault() {
        return new RealWebActionExecutor(WebViewJsBridge.createDefault());
    }

    @Override
    public ToolResult execute(WebActionRequest request) {
        try {
            if (jsBridge == null) {
                return ToolResult.error("execution_failed", "web js bridge unavailable")
                        .with("channel", WebActionToolChannel.CHANNEL_NAME)
                        .with("domain", "web");
            }
            WebViewJsBridge.WebViewHandle handle = jsBridge.requireWebView();
            ViewObservationSnapshot snapshot = validateSnapshot(request, handle);
            if (snapshot == null) {
                return staleObservation();
            }
            if ("eval_js".equals(request.action)) {
                WebViewJsBridge.RawJsResult result = WebViewJsBridge.parseRawResult(
                        jsBridge.evaluate(handle.webView, request.script)
                );
                return baseSuccess(request)
                        .with("action", request.action)
                        .with("valueType", result.valueType)
                        .with("value", result.value == null || result.value == JSONObject.NULL ? null : result.value.toString());
            }

            String script;
            if ("click".equals(request.action)) {
                script = WebDomScriptFactory.buildClickScript(request.ref, request.selector);
            } else if ("input".equals(request.action)) {
                script = WebDomScriptFactory.buildInputScript(request.ref, request.selector, request.text);
            } else if ("scroll_to_bottom".equals(request.action)) {
                script = WebDomScriptFactory.buildScrollToBottomScript();
            } else {
                return ToolResult.error("invalid_args", "Unsupported web action '" + request.action + "'")
                        .with("channel", WebActionToolChannel.CHANNEL_NAME)
                        .with("domain", "web");
            }

            JSONObject payload = WebViewJsBridge.parseObjectResult(
                    jsBridge.evaluate(handle.webView, script)
            );
            if (!payload.optBoolean("ok", false)) {
                String error = payload.optString("error", "web_action_failed");
                if (request.ref != null && "element_not_found".equals(error)) {
                    return staleObservation();
                }
                return ToolResult.error("web_action_failed", error)
                        .with("channel", WebActionToolChannel.CHANNEL_NAME)
                        .with("domain", "web");
            }

            ToolResult result = baseSuccess(request)
                    .with("action", request.action)
                    .with("ref", payload.optString("ref", request.ref))
                    .with("selector", request.selector);
            if (payload.has("tag")) {
                result.with("tag", payload.optString("tag", null));
            }
            if (payload.has("before")) {
                result.with("before", payload.optLong("before"));
            }
            if (payload.has("after")) {
                result.with("after", payload.optLong("after"));
            }
            if (payload.has("height")) {
                result.with("height", payload.optLong("height"));
            }
            if (payload.has("value")) {
                result.with("value", payload.optString("value", null));
            }
            return result;
        } catch (Exception exception) {
            return ToolResult.error("execution_failed", exception.getMessage())
                    .with("channel", WebActionToolChannel.CHANNEL_NAME)
                    .with("domain", "web");
        }
    }

    private ViewObservationSnapshot validateSnapshot(WebActionRequest request,
                                                     WebViewJsBridge.WebViewHandle handle) {
        if (request.observation == null) {
            return null;
        }
        String snapshotId = request.observation.optString("snapshotId", "").trim();
        if (snapshotId.isEmpty()) {
            return null;
        }
        ViewObservationSnapshot latest = ViewObservationSnapshotRegistry.getLatestSnapshot();
        if (latest == null) {
            return null;
        }
        if (!snapshotId.equals(latest.snapshotId)) {
            return null;
        }
        if (!"web_dom".equals(latest.source) || !"web".equals(latest.interactionDomain)) {
            return null;
        }
        String currentActivityClassName = jsBridge.getCurrentActivityClassName(handle);
        if (latest.activityClassName != null
                && currentActivityClassName != null
                && !latest.activityClassName.equals(currentActivityClassName)) {
            return null;
        }
        String currentPageUrl = jsBridge.getCurrentPageUrl(handle);
        if (latest.pageUrl != null
                && currentPageUrl != null
                && !latest.pageUrl.equals(currentPageUrl)) {
            return null;
        }
        return latest;
    }

    private ToolResult staleObservation() {
        return ToolResult.error("stale_web_observation", "web observation is stale, capture a fresh web_dom snapshot")
                .with("channel", WebActionToolChannel.CHANNEL_NAME)
                .with("domain", "web");
    }

    private ToolResult baseSuccess(WebActionRequest request) {
        return ToolResult.success()
                .with("channel", WebActionToolChannel.CHANNEL_NAME)
                .with("domain", "web")
                .with("mock", false)
                .with("message", "Real web action executor completed the request")
                .with("observationSnapshotId",
                        request.observation != null ? request.observation.optString("snapshotId", null) : null)
                .with("selector", request.selector);
    }
}
