package com.hh.agent.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.hh.agent.BusinessWebActivity;
import com.hh.agent.FloatingBallHiddenActivity;
import com.hh.agent.android.AgentInitializer;
import com.hh.agent.android.floating.FloatingBallLifecycleCallbacks;
import com.hh.agent.android.web.WebViewJsBridge;
import com.hh.agent.h5bench.H5BenchmarkManifest;
import com.hh.agent.h5bench.H5BenchmarkManifestRepository;
import com.hh.agent.h5bench.MiniWoBAgentRunDriver;
import com.hh.agent.h5bench.MiniWoBBenchmarkRunner;
import com.hh.agent.h5bench.MiniWoBInProcessPageController;
import com.hh.agent.h5bench.MiniWoBJsProbe;
import com.hh.agent.h5bench.MiniWoBRunOrchestrator;
import com.hh.agent.h5bench.MiniWoBRunRecord;
import com.hh.agent.h5bench.MiniWoBSuiteRunner;
import com.hh.agent.h5bench.MiniWoBTaskRegistry;
import com.hh.agent.shortcut.AppShortcutProvider;
import com.hh.agent.voice.MockVoiceRecognizer;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

/**
 * Application类
 * 初始化悬浮球、Agent 和生命周期观察者
 */
public class App extends Application implements MiniWoBRunOrchestrator.Provider, MiniWoBRunOrchestrator.ExecutorProvider {

    private static final String TAG = "App";
    private static final long UI_ACTION_TIMEOUT_MS = 5000L;
    private static final long PAGE_READY_TIMEOUT_MS = 8000L;
    private static final long PAGE_READY_POLL_INTERVAL_MS = 200L;
    private static final int DEFAULT_BENCHMARK_MAX_STEPS = 15;
    private static final long DEFAULT_BENCHMARK_TIMEOUT_MS = 30000L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static App instance;
    private final Executor miniWoBRunExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.d(TAG, "App onCreate");

        // 初始化 Agent（语音识别器通过注入方式在 AgentInitializer 内部设置）
        AgentInitializer.initialize(
                this,
                new MockVoiceRecognizer(),
                AppShortcutProvider.createShortcuts(this),
                DefaultActivityViewContextSourcePolicy.create(),
                () -> {
                    Log.d(TAG, "Agent initialized successfully");

                    // 初始化悬浮球，并演示宿主追加隐藏页面的接入方式。
                    AgentInitializer.initializeFloatingBall(
                            App.this,
                            Collections.singletonList(FloatingBallHiddenActivity.class.getName())
                    );
                });
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public MiniWoBRunOrchestrator getMiniWoBRunOrchestrator() {
        WebViewJsBridge jsBridge = WebViewJsBridge.createDefault();
        MiniWoBInProcessPageController.TaskHost taskHost = new MiniWoBInProcessPageController.TaskHost() {
            @Override
            public void openTaskPage(String assetPath) throws Exception {
                closeForegroundBenchmarkHost();
                Activity previousForeground = FloatingBallLifecycleCallbacks.getCurrentForegroundActivity();
                runOnMainThread(() -> {
                    Intent intent = new Intent(App.this, BusinessWebActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(BusinessWebActivity.EXTRA_TITLE, "H5基准测试");
                    intent.putExtra(BusinessWebActivity.EXTRA_BENCHMARK_MODE_ENABLED, true);
                    intent.putExtra(BusinessWebActivity.EXTRA_BENCHMARK_ASSET_PATH, assetPath);
                    startActivity(intent);
                });
                Activity stable = awaitNewForegroundBusinessWebActivity(
                        FloatingBallLifecycleCallbacks::getCurrentForegroundActivity,
                        previousForeground,
                        UI_ACTION_TIMEOUT_MS,
                        PAGE_READY_POLL_INTERVAL_MS
                );
                if (!(stable instanceof BusinessWebActivity)) {
                    throw new IllegalStateException("benchmark_activity_not_ready");
                }
            }
        };
        MiniWoBInProcessPageController.Runtime runtime = new MiniWoBInProcessPageController.Runtime() {
            @Override
            public void waitForReady() throws Exception {
                long deadline = System.currentTimeMillis() + PAGE_READY_TIMEOUT_MS;
                while (System.currentTimeMillis() <= deadline) {
                    WebViewJsBridge.WebViewHandle handle = jsBridge.requireWebView();
                    JSONObject ready = WebViewJsBridge.parseObjectResult(jsBridge.evaluate(
                            handle.webView,
                            "(function(){return JSON.stringify({"
                                    + "ready: document.readyState === 'complete',"
                                    + "hasStartEpisode: !!(window.core && typeof core.startEpisodeReal === 'function')"
                                    + "});})();"
                    ));
                    if (ready.optBoolean("ready", false) && ready.optBoolean("hasStartEpisode", false)) {
                        return;
                    }
                    Thread.sleep(PAGE_READY_POLL_INTERVAL_MS);
                }
                throw new IllegalStateException("benchmark_page_not_ready");
            }

            @Override
            public void clearStorage() throws Exception {
                WebViewJsBridge.WebViewHandle handle = jsBridge.requireWebView();
                JSONObject payload = WebViewJsBridge.parseObjectResult(jsBridge.evaluate(
                        handle.webView,
                        "(function(){"
                                + "try { localStorage.clear(); sessionStorage.clear(); return JSON.stringify({ok:true}); }"
                                + "catch (e) { return JSON.stringify({ok:false,error:String(e)}); }"
                                + "})();"
                ));
                if (!payload.optBoolean("ok", false)) {
                    throw new IllegalStateException(payload.optString("error", "clear_storage_failed"));
                }
            }

            @Override
            public void startEpisode(int seed) throws Exception {
                WebViewJsBridge.WebViewHandle handle = jsBridge.requireWebView();
                JSONObject payload = WebViewJsBridge.parseObjectResult(jsBridge.evaluate(
                        handle.webView,
                        MiniWoBJsProbe.buildStartEpisodeScript(seed)
                ));
                if (!payload.optBoolean("ok", false)) {
                    throw new IllegalStateException(payload.optString("error", "start_episode_failed"));
                }
            }

            @Override
            public MiniWoBBenchmarkRunner.MiniWoBPageStatus readStatus() throws Exception {
                WebViewJsBridge.WebViewHandle handle = jsBridge.requireWebView();
                JSONObject payload = WebViewJsBridge.parseObjectResult(jsBridge.evaluate(
                        handle.webView,
                        MiniWoBJsProbe.buildReadStatusScript()
                ));
                return new MiniWoBBenchmarkRunner.MiniWoBPageStatus(
                        payload.optBoolean("done", false),
                        payload.optDouble("reward", 0.0),
                        payload.optDouble("rawReward", 0.0),
                        payload.optString("episodeId", null),
                        0L
                );
            }
        };
        MiniWoBBenchmarkRunner benchmarkRunner = new MiniWoBBenchmarkRunner(
                new MiniWoBInProcessPageController(taskHost, runtime, PAGE_READY_POLL_INTERVAL_MS),
                new MiniWoBAgentRunDriver()
        );
        MiniWoBSuiteRunner suiteRunner = new MiniWoBSuiteRunner(
                assetPath -> new MiniWoBTaskRegistry(App.this).loadSuite(assetPath),
                benchmarkRunner
        );
        H5BenchmarkManifest manifest = loadSharedBenchmarkManifest();
        return () -> {
            String runId = "miniwob-" + UUID.randomUUID();
            MiniWoBRunRecord runRecord = suiteRunner.runSuite(
                    resolveSuiteAssetPath(manifest),
                    runId,
                    "android-agent-default",
                    "in_app",
                    "workspace@v1",
                    manifest.getSuiteId(),
                    manifest.getSuiteId() + "@v1",
                    DEFAULT_BENCHMARK_MAX_STEPS,
                    DEFAULT_BENCHMARK_TIMEOUT_MS,
                    resolveAppVersion()
            );
            closeForegroundBenchmarkHost();
            return Collections.singletonList(runRecord);
        };
    }

    @Override
    public Executor getMiniWoBRunExecutor() {
        return miniWoBRunExecutor;
    }

    private void closeForegroundBenchmarkHost() throws Exception {
        Activity activity = FloatingBallLifecycleCallbacks.getCurrentForegroundActivity();
        if (!(activity instanceof BusinessWebActivity)) {
            return;
        }
        runOnMainThread(activity::finish);
        FloatingBallLifecycleCallbacks.awaitNextStableForegroundActivity(false, UI_ACTION_TIMEOUT_MS);
    }

    private void runOnMainThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = new Exception[1];
        MAIN_HANDLER.post(() -> {
            try {
                action.run();
            } catch (Exception exception) {
                error[0] = exception;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(UI_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("ui_action_timeout");
        }
        if (error[0] != null) {
            throw error[0];
        }
    }

    static Activity awaitNewForegroundBusinessWebActivity(ForegroundActivitySupplier foregroundActivitySupplier,
                                                          Activity previousForeground,
                                                          long timeoutMs,
                                                          long pollIntervalMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (true) {
            Activity currentForeground = foregroundActivitySupplier.get();
            if (currentForeground instanceof BusinessWebActivity
                    && currentForeground != previousForeground
                    && !currentForeground.isFinishing()
                    && !currentForeground.isDestroyed()) {
                return currentForeground;
            }
            if (System.currentTimeMillis() > deadline) {
                return null;
            }
            if (pollIntervalMs > 0L) {
                Thread.sleep(pollIntervalMs);
            }
        }
    }

    interface ForegroundActivitySupplier {
        Activity get();
    }

    private String resolveAppVersion() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (packageInfo.versionName != null && !packageInfo.versionName.trim().isEmpty()) {
                return packageInfo.versionName;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return "unknown";
    }

    private H5BenchmarkManifest loadSharedBenchmarkManifest() {
        try {
            return new H5BenchmarkManifestRepository(this).loadBaseline20();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load shared H5 benchmark manifest", exception);
        }
    }

    private String resolveSuiteAssetPath(H5BenchmarkManifest manifest) {
        String assetPath = manifest.getTaskListAssetPath();
        return assetPath == null || assetPath.trim().isEmpty()
                ? H5BenchmarkManifestRepository.BASELINE_20_ASSET_PATH
                : assetPath;
    }
}
