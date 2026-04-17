package com.hh.agent.android.web;

import android.app.Activity;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.hh.agent.android.thread.MainThreadRunner;
import com.hh.agent.android.viewcontext.StableForegroundActivityProvider;
import com.hh.agent.android.viewcontext.WebViewFinder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WebViewJsBridge {

    private static final long DEFAULT_TIMEOUT_MS = 1500L;

    private final StableForegroundActivityProvider stableForegroundActivityProvider;
    private final WebViewFinder webViewFinder;
    private final MainThreadRunner mainThreadRunner;

    public WebViewJsBridge(StableForegroundActivityProvider stableForegroundActivityProvider,
                           WebViewFinder webViewFinder,
                           MainThreadRunner mainThreadRunner) {
        this.stableForegroundActivityProvider = stableForegroundActivityProvider;
        this.webViewFinder = webViewFinder;
        this.mainThreadRunner = mainThreadRunner;
    }

    public static WebViewJsBridge createDefault() {
        return new WebViewJsBridge(
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return com.hh.agent.android.viewcontext.InProcessViewHierarchyDumper.getCurrentStableForegroundActivity();
                    }
                },
                new WebViewFinder(),
                new MainThreadRunner()
        );
    }

    public WebViewHandle requireWebView() throws Exception {
        Activity activity = mainThreadRunner.call(
                new MainThreadRunner.MainThreadCallable<Activity>() {
                    @Override
                    public Activity call() {
                        return stableForegroundActivityProvider.getStableForegroundActivity();
                    }
                },
                DEFAULT_TIMEOUT_MS
        );
        WebViewFinder.WebViewFindResult findResult = mainThreadRunner.call(
                new MainThreadRunner.MainThreadCallable<WebViewFinder.WebViewFindResult>() {
                    @Override
                    public WebViewFinder.WebViewFindResult call() {
                        return webViewFinder.findPrimaryWebView(activity);
                    }
                },
                DEFAULT_TIMEOUT_MS
        );
        if (findResult == null || findResult.webView == null) {
            throw new IllegalStateException("webview_not_found");
        }
        return new WebViewHandle(activity, findResult.webView, findResult.candidateCount, findResult.selectionReason);
    }

    public String evaluate(WebView webView, String script) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resultRef = new AtomicReference<>();
        final AtomicReference<Exception> errorRef = new AtomicReference<>();
        mainThreadRunner.run(new Runnable() {
            @Override
            public void run() {
                try {
                    webView.evaluateJavascript(script, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            resultRef.set(value);
                            latch.countDown();
                        }
                    });
                } catch (Exception exception) {
                    errorRef.set(exception);
                    latch.countDown();
                }
            }
        });
        if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new MainThreadRunner.MainThreadTimeoutException("WebView evaluateJavascript timed out");
        }
        if (errorRef.get() != null) {
            throw errorRef.get();
        }
        return resultRef.get();
    }

    public NativeTapResult performNormalizedTap(WebViewHandle handle,
                                                double normalizedX,
                                                double normalizedY) throws Exception {
        if (handle == null || handle.webView == null) {
            return NativeTapResult.error("webview_not_found", "webview_not_found");
        }
        return mainThreadRunner.call(new MainThreadRunner.MainThreadCallable<NativeTapResult>() {
            @Override
            public NativeTapResult call() {
                WebView webView = handle.webView;
                int webViewWidth = webView.getWidth();
                int webViewHeight = webView.getHeight();
                if (webViewWidth <= 0 || webViewHeight <= 0) {
                    return NativeTapResult.error("webview_not_visible", "webview_not_visible");
                }

                float clampedNormalizedX = clampNormalized(normalizedX);
                float clampedNormalizedY = clampNormalized(normalizedY);
                float localTapX = clampLocalCoordinate(clampedNormalizedX * webViewWidth, webViewWidth);
                float localTapY = clampLocalCoordinate(clampedNormalizedY * webViewHeight, webViewHeight);

                int[] webViewOnScreen = new int[2];
                webView.getLocationOnScreen(webViewOnScreen);
                int screenTapX = webViewOnScreen[0] + Math.round(localTapX);
                int screenTapY = webViewOnScreen[1] + Math.round(localTapY);

                if (handle.activity == null || handle.activity.getWindow() == null) {
                    return NativeTapResult.error("activity_not_available", "activity_not_available");
                }
                View decorView = handle.activity.getWindow().getDecorView();
                if (decorView == null) {
                    return NativeTapResult.error("decor_view_not_available", "decor_view_not_available");
                }
                int[] decorOnScreen = new int[2];
                decorView.getLocationOnScreen(decorOnScreen);
                float windowTapX = screenTapX - decorOnScreen[0];
                float windowTapY = screenTapY - decorOnScreen[1];

                long downTime = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(
                        downTime,
                        downTime,
                        MotionEvent.ACTION_DOWN,
                        windowTapX,
                        windowTapY,
                        0);
                MotionEvent up = MotionEvent.obtain(
                        downTime,
                        downTime + 24L,
                        MotionEvent.ACTION_UP,
                        windowTapX,
                        windowTapY,
                        0);
                down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                up.setSource(InputDevice.SOURCE_TOUCHSCREEN);

                boolean downHandled;
                boolean upHandled;
                try {
                    downHandled = handle.activity.dispatchTouchEvent(down);
                    upHandled = handle.activity.dispatchTouchEvent(up);
                } finally {
                    down.recycle();
                    up.recycle();
                }

                return NativeTapResult.success(
                        downHandled || upHandled,
                        webViewWidth,
                        webViewHeight,
                        clampedNormalizedX,
                        clampedNormalizedY,
                        Math.round(localTapX),
                        Math.round(localTapY),
                        screenTapX,
                        screenTapY,
                        downHandled,
                        upHandled);
            }
        }, DEFAULT_TIMEOUT_MS);
    }

    @Nullable
    public String getCurrentActivityClassName(WebViewHandle handle) {
        return handle.activity != null ? handle.activity.getClass().getName() : null;
    }

    @Nullable
    public String getCurrentPageUrl(WebViewHandle handle) throws Exception {
        if (handle == null || handle.webView == null) {
            return null;
        }
        return mainThreadRunner.call(new MainThreadRunner.MainThreadCallable<String>() {
            @Override
            public String call() {
                return handle.webView.getUrl();
            }
        }, DEFAULT_TIMEOUT_MS);
    }

    @Nullable
    public String getCurrentPageTitle(WebViewHandle handle) throws Exception {
        if (handle == null || handle.webView == null) {
            return null;
        }
        return mainThreadRunner.call(new MainThreadRunner.MainThreadCallable<String>() {
            @Override
            public String call() {
                return handle.webView.getTitle();
            }
        }, DEFAULT_TIMEOUT_MS);
    }

    public static String decodeJsResult(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");
    }

    public static JSONObject parseObjectResult(@Nullable String raw) throws JSONException {
        return new JSONObject(decodeJsResult(raw));
    }

    public static RawJsResult parseRawResult(@Nullable String raw) {
        if (raw == null || "null".equals(raw)) {
            return new RawJsResult("null", JSONObject.NULL);
        }
        String decoded = decodeJsResult(raw);
        if (decoded == null) {
            return new RawJsResult("null", JSONObject.NULL);
        }
        try {
            return new RawJsResult("object", new JSONObject(decoded));
        } catch (JSONException ignored) {
        }
        try {
            return new RawJsResult("array", new JSONArray(decoded));
        } catch (JSONException ignored) {
        }
        if ("true".equals(decoded) || "false".equals(decoded)) {
            return new RawJsResult("boolean", Boolean.valueOf(decoded));
        }
        try {
            if (decoded.contains(".")) {
                return new RawJsResult("number", Double.valueOf(decoded));
            }
            return new RawJsResult("number", Long.valueOf(decoded));
        } catch (NumberFormatException ignored) {
        }
        return new RawJsResult("string", decoded);
    }

    public static final class WebViewHandle {
        public final Activity activity;
        public final WebView webView;
        public final int candidateCount;
        public final String selectionReason;

        public WebViewHandle(Activity activity, WebView webView, int candidateCount, String selectionReason) {
            this.activity = activity;
            this.webView = webView;
            this.candidateCount = candidateCount;
            this.selectionReason = selectionReason;
        }
    }

    public static final class RawJsResult {
        public final String valueType;
        public final Object value;

        public RawJsResult(String valueType, Object value) {
            this.valueType = valueType;
            this.value = value;
        }
    }

    public static final class NativeTapResult {
        public final boolean success;
        public final String error;
        public final String message;
        public final int webViewWidth;
        public final int webViewHeight;
        public final float normalizedX;
        public final float normalizedY;
        public final int localTapX;
        public final int localTapY;
        public final int screenTapX;
        public final int screenTapY;
        public final boolean downHandled;
        public final boolean upHandled;

        public NativeTapResult(boolean success,
                               String error,
                               String message,
                               int webViewWidth,
                               int webViewHeight,
                               float normalizedX,
                               float normalizedY,
                               int localTapX,
                               int localTapY,
                               int screenTapX,
                               int screenTapY,
                               boolean downHandled,
                               boolean upHandled) {
            this.success = success;
            this.error = error;
            this.message = message;
            this.webViewWidth = webViewWidth;
            this.webViewHeight = webViewHeight;
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.localTapX = localTapX;
            this.localTapY = localTapY;
            this.screenTapX = screenTapX;
            this.screenTapY = screenTapY;
            this.downHandled = downHandled;
            this.upHandled = upHandled;
        }

        public static NativeTapResult success(boolean handled,
                                              int webViewWidth,
                                              int webViewHeight,
                                              float normalizedX,
                                              float normalizedY,
                                              int localTapX,
                                              int localTapY,
                                              int screenTapX,
                                              int screenTapY,
                                              boolean downHandled,
                                              boolean upHandled) {
            return new NativeTapResult(
                    handled,
                    handled ? null : "native_tap_not_handled",
                    handled ? null : "native_tap_not_handled",
                    webViewWidth,
                    webViewHeight,
                    normalizedX,
                    normalizedY,
                    localTapX,
                    localTapY,
                    screenTapX,
                    screenTapY,
                    downHandled,
                    upHandled);
        }

        public static NativeTapResult error(String error, String message) {
            return new NativeTapResult(false, error, message,
                    0, 0, 0f, 0f, 0, 0, 0, 0, false, false);
        }
    }

    private static float clampNormalized(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5f;
        }
        return (float) Math.max(0d, Math.min(1d, value));
    }

    private static float clampLocalCoordinate(float value, int size) {
        if (size <= 1) {
            return 0f;
        }
        return Math.max(1f, Math.min(size - 1f, value));
    }
}

