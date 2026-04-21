package com.hh.agent.screenvision;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;

import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.android.viewcontext.ScreenSnapshotAnalysis;
import com.hh.agent.android.viewcontext.ScreenSnapshotAnalyzer;
import com.hh.agent.android.viewcontext.ScreenSnapshotWarmupCapable;
import com.screenvision.sdk.AnalyzeCompactCallback;
import com.screenvision.sdk.CompactAnalysisOptions;
import com.screenvision.sdk.ScreenVisionSdk;
import com.screenvision.sdk.TaskContext;
import com.screenvision.sdk.model.CompactPageAnalysisResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adapter that captures the current activity window and feeds it into ScreenVisionSdk.
 */
public final class ScreenVisionSnapshotAnalyzer implements ScreenSnapshotAnalyzer, ScreenSnapshotWarmupCapable, AutoCloseable {

    private static final String TAG = "ScreenVisionSnapshotAnalyzer";
    private static final long PIXEL_COPY_TIMEOUT_MS = 3000L;
    private static final long ANALYZE_TIMEOUT_MS = 15000L;
    private static final long PREWARM_TIMEOUT_MS = 6000L;
    private static final int PREWARM_BITMAP_EDGE_PX = 48;
    private static final String OBSERVATION_MODE = "screenvision_compact_ocr_ui";

    private final ScreenVisionSdk sdk;
    private final HandlerThread pixelCopyThread;
    private final Handler pixelCopyHandler;
    private final ExecutorService prewarmExecutor;
    private final AtomicBoolean prewarmRequested = new AtomicBoolean(false);

    public ScreenVisionSnapshotAnalyzer(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        Context appContext = context.getApplicationContext();
        this.sdk = ScreenVisionSdk.create(appContext);
        this.pixelCopyThread = new HandlerThread("screen-vision-pixel-copy");
        this.pixelCopyThread.start();
        this.pixelCopyHandler = new Handler(pixelCopyThread.getLooper());
        this.prewarmExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public ScreenSnapshotAnalysis analyze(Activity activity, @Nullable String targetHint) throws Exception {
        if (activity == null) {
            throw new IllegalArgumentException("activity cannot be null");
        }
        long startMs = System.currentTimeMillis();
        String activityName = activity.getClass().getName();
        AgentLogs.info(TAG, "analyze_start",
                "activity=" + activityName + " target_hint=" + sanitize(targetHint));

        Bitmap bitmap = null;
        try {
            bitmap = captureActivityBitmap(activity);
            long captureEndMs = System.currentTimeMillis();
            long captureDurationMs = captureEndMs - startMs;
            AgentLogs.info(TAG, "capture_complete",
                    "activity=" + activityName
                            + " duration_ms=" + captureDurationMs
                            + " image=" + bitmap.getWidth() + "x" + bitmap.getHeight());

            CompactPageAnalysisResult result = analyzeCompactBlocking(bitmap, targetHint, ANALYZE_TIMEOUT_MS);
            long analyzeEndMs = System.currentTimeMillis();
            long analyzeDurationMs = analyzeEndMs - captureEndMs;
            long totalDurationMs = analyzeEndMs - startMs;
            String compactJson = result.toJson();
            String screenSnapshotRef = "screenvision://capture/"
                    + activity.getClass().getSimpleName()
                    + "/"
                    + System.currentTimeMillis();

            AgentLogs.info(TAG, "analyze_complete",
                    "activity=" + activityName
                            + " capture_ms=" + captureDurationMs
                            + " analyze_ms=" + analyzeDurationMs
                            + " total_ms=" + totalDurationMs
                            + " compact_length=" + compactJson.length());

            return new ScreenSnapshotAnalysis(
                    activityName,
                    OBSERVATION_MODE,
                    screenSnapshotRef,
                    compactJson,
                    null,
                    bitmap.getWidth(),
                    bitmap.getHeight()
            );
        } catch (Exception e) {
            long totalDurationMs = System.currentTimeMillis() - startMs;
            AgentLogs.error(TAG, "analyze_failed",
                    "activity=" + activityName
                            + " total_ms=" + totalDurationMs
                            + " message=" + e.getMessage(),
                    e);
            throw e;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    @Override
    public void prewarmAsync() {
        if (!prewarmRequested.compareAndSet(false, true)) {
            return;
        }
        AgentLogs.info(TAG, "prewarm_start", null);
        prewarmExecutor.execute(() -> {
            Bitmap bitmap = Bitmap.createBitmap(PREWARM_BITMAP_EDGE_PX, PREWARM_BITMAP_EDGE_PX, Bitmap.Config.ARGB_8888);
            try {
                analyzeCompactBlocking(bitmap, null, PREWARM_TIMEOUT_MS);
                AgentLogs.info(TAG, "prewarm_complete", null);
            } catch (Exception e) {
                AgentLogs.warn(TAG, "prewarm_failed", "message=" + e.getMessage());
            } finally {
                bitmap.recycle();
            }
        });
    }

    @Override
    public void close() {
        prewarmExecutor.shutdownNow();
        pixelCopyThread.quitSafely();
        sdk.close();
    }

    private CompactPageAnalysisResult analyzeCompactBlocking(Bitmap bitmap,
                                                             @Nullable String targetHint,
                                                             long timeoutMs)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CompactPageAnalysisResult> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        sdk.analyzeCompact(
                bitmap,
                CompactAnalysisOptions.newBuilder().build(),
                buildTaskContext(targetHint),
                new AnalyzeCompactCallback() {
                    @Override
                    public void onSuccess(CompactPageAnalysisResult result) {
                        resultRef.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        errorRef.set(throwable);
                        latch.countDown();
                    }
                }
        );
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("ScreenVisionSdk analyzeCompact timed out");
        }
        Throwable throwable = errorRef.get();
        if (throwable != null) {
            if (throwable instanceof Exception) {
                throw (Exception) throwable;
            }
            throw new IllegalStateException(throwable);
        }
        CompactPageAnalysisResult result = resultRef.get();
        if (result == null) {
            throw new IllegalStateException("ScreenVisionSdk returned no result");
        }
        return result;
    }

    private TaskContext buildTaskContext(@Nullable String targetHint) {
        if (targetHint == null || targetHint.trim().isEmpty()) {
            return TaskContext.empty();
        }
        return new TaskContext(targetHint);
    }

    private Bitmap captureActivityBitmap(Activity activity) throws Exception {
        Window window = activity.getWindow();
        if (window == null) {
            throw new IllegalStateException("Activity has no window");
        }
        View decorView = window.getDecorView();
        if (decorView == null) {
            throw new IllegalStateException("Activity has no decorView");
        }
        int width = decorView.getWidth();
        int height = decorView.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("DecorView has invalid size for capture");
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger resultCode = new AtomicInteger(PixelCopy.ERROR_UNKNOWN);
        PixelCopy.request(
                window,
                bitmap,
                copyResult -> {
                    resultCode.set(copyResult);
                    latch.countDown();
                },
                pixelCopyHandler
        );
        if (!latch.await(PIXEL_COPY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            bitmap.recycle();
            throw new TimeoutException("PixelCopy timed out while capturing the activity window");
        }
        if (resultCode.get() != PixelCopy.SUCCESS) {
            bitmap.recycle();
            throw new IllegalStateException("PixelCopy failed with code " + resultCode.get());
        }
        return bitmap;
    }

    private static String sanitize(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 64) {
            return normalized;
        }
        return normalized.substring(0, 64) + "...";
    }
}