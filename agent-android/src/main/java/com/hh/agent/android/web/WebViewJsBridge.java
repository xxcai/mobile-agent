package com.hh.agent.android.web;

import android.app.Activity;
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

    @Nullable
    public String getCurrentActivityClassName(WebViewHandle handle) {
        return handle.activity != null ? handle.activity.getClass().getName() : null;
    }

    @Nullable
    public String getCurrentPageUrl(WebViewHandle handle) {
        return handle.webView != null ? handle.webView.getUrl() : null;
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
}
