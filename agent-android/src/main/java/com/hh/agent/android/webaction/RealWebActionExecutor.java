package com.hh.agent.android.webaction;

import com.hh.agent.android.channel.WebActionToolChannel;
import com.hh.agent.android.viewcontext.ViewObservationSnapshot;
import com.hh.agent.android.viewcontext.ViewObservationSnapshotRegistry;
import com.hh.agent.android.web.WebDomScriptFactory;
import com.hh.agent.android.web.WebViewJsBridge;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RealWebActionExecutor implements WebActionExecutor {
    private static final Pattern STAR_INDEX_PATTERN = Pattern.compile("([1-5])");
    private static final int DEFAULT_BUS_RATING_STARS = 3;

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

            if ("click".equals(request.action)) {
                return executeNativeClick(request, handle, snapshot);
            }

            String script;
            if ("input".equals(request.action)) {
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

    private ToolResult executeNativeClick(WebActionRequest request,
                                          WebViewJsBridge.WebViewHandle handle,
                                          ViewObservationSnapshot snapshot) throws Exception {
        JSONObject payload = WebViewJsBridge.parseObjectResult(
                jsBridge.evaluate(handle.webView,
                        WebDomScriptFactory.buildResolveClickTargetScript(request.ref, request.selector))
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

        WebTapTarget tapTarget = resolveTapTarget(snapshot, request, payload);
        WebViewJsBridge.NativeTapResult nativeTapResult = jsBridge.performNormalizedTap(
                handle,
                tapTarget.normalizedX,
                tapTarget.normalizedY
        );
        if (!nativeTapResult.success) {
            return ToolResult.error("native_web_tap_failed", nativeTapResult.message)
                    .with("channel", WebActionToolChannel.CHANNEL_NAME)
                    .with("domain", "web")
                    .with("ref", payload.optString("ref", request.ref))
                    .with("selector", request.selector)
                    .with("normalizedX", nativeTapResult.normalizedX)
                    .with("normalizedY", nativeTapResult.normalizedY);
        }

        ToolResult result = baseSuccess(request)
                .with("action", request.action)
                .with("ref", payload.optString("ref", request.ref))
                .with("selector", request.selector)
                .with("tag", payload.optString("tag", null))
                .with("tapMode", "native_injection")
                .with("normalizedX", nativeTapResult.normalizedX)
                .with("normalizedY", nativeTapResult.normalizedY)
                .with("localTapX", nativeTapResult.localTapX)
                .with("localTapY", nativeTapResult.localTapY)
                .with("screenTapX", nativeTapResult.screenTapX)
                .with("screenTapY", nativeTapResult.screenTapY)
                .with("webViewWidth", nativeTapResult.webViewWidth)
                .with("webViewHeight", nativeTapResult.webViewHeight)
                .with("tapTargetSource", tapTarget.source)
                .with("downHandled", nativeTapResult.downHandled)
                .with("upHandled", nativeTapResult.upHandled);
        if (payload.has("text")) {
            result.with("text", payload.optString("text", null));
        }
        return result;
    }

    private WebTapTarget resolveTapTarget(ViewObservationSnapshot snapshot,
                                          WebActionRequest request,
                                          JSONObject payload) {
        double normalizedX = payload.optDouble("normalizedX", 0.5d);
        double normalizedY = payload.optDouble("normalizedY", 0.5d);
        String source = "element_center";

        if (isBusRatingPage(snapshot) && isBusRatingRow(request, payload)) {
            JSONObject bounds = payload.optJSONObject("bounds");
            double viewportWidth = payload.optDouble("viewportWidth", 0d);
            double heuristicX = computeBusRatingStarNormalizedX(
                    bounds,
                    viewportWidth,
                    parseDesiredStars(request));
            if (!Double.isNaN(heuristicX)) {
                normalizedX = heuristicX;
                source = "bus_rating_star_heuristic";
            }
        }
        return new WebTapTarget(normalizedX, normalizedY, source);
    }

    private boolean isBusRatingPage(ViewObservationSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String pageUrl = snapshot.pageUrl != null ? snapshot.pageUrl : "";
        String pageTitle = snapshot.pageTitle != null ? snapshot.pageTitle : "";
        return pageUrl.contains("shuttleeval") || pageTitle.contains("班车评价");
    }

    private boolean isBusRatingRow(WebActionRequest request, JSONObject payload) {
        String targetDescriptor = request.observation != null
                ? request.observation.optString("targetDescriptor", "")
                : "";
        String text = payload.optString("text", "");
        return containsBusRatingLabel(targetDescriptor) || containsBusRatingLabel(text);
    }

    private boolean containsBusRatingLabel(String value) {
        return value != null
                && (value.contains("车辆状况")
                || value.contains("司机服务")
                || value.contains("客服调度"));
    }

    private int parseDesiredStars(WebActionRequest request) {
        String targetDescriptor = request.observation != null
                ? request.observation.optString("targetDescriptor", "")
                : "";
        Matcher matcher = STAR_INDEX_PATTERN.matcher(targetDescriptor);
        while (matcher.find()) {
            int starIndex = Integer.parseInt(matcher.group(1));
            if (starIndex >= 1 && starIndex <= 5) {
                return starIndex;
            }
        }
        return DEFAULT_BUS_RATING_STARS;
    }

    private double computeBusRatingStarNormalizedX(JSONObject bounds, double viewportWidth, int desiredStars) {
        if (bounds == null || viewportWidth <= 0d) {
            return Double.NaN;
        }
        double left = bounds.optDouble("left", Double.NaN);
        double width = bounds.optDouble("width", Double.NaN);
        if (Double.isNaN(left) || Double.isNaN(width) || width <= 0d) {
            return Double.NaN;
        }
        int clampedStars = Math.max(1, Math.min(5, desiredStars));
        double localRatio = 0.54d + (((clampedStars - 0.5d) / 5.0d) * 0.40d);
        double absoluteX = left + (width * localRatio);
        return Math.max(0d, Math.min(1d, absoluteX / viewportWidth));
    }

    private ViewObservationSnapshot validateSnapshot(WebActionRequest request,
                                                     WebViewJsBridge.WebViewHandle handle) throws Exception {
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
                && !urlsReferToSameLocalAssetPage(latest.pageUrl, currentPageUrl)
                && !latest.pageUrl.equals(currentPageUrl)) {
            return null;
        }
        return latest;
    }

    private boolean urlsReferToSameLocalAssetPage(String snapshotPageUrl, String currentPageUrl) {
        return snapshotPageUrl.startsWith("file:///android_asset/")
                && "about:blank".equals(currentPageUrl);
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

    private static final class WebTapTarget {
        private final double normalizedX;
        private final double normalizedY;
        private final String source;

        private WebTapTarget(double normalizedX, double normalizedY, String source) {
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.source = source;
        }
    }
}

