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

import com.hh.agent.android.viewcontext.ScreenSnapshotAnalysis;
import com.hh.agent.android.viewcontext.ScreenSnapshotAnalyzer;
import com.screenvision.sdk.AnalyzeCompactCallback;
import com.screenvision.sdk.CompactAnalysisOptions;
import com.screenvision.sdk.ScreenVisionSdk;
import com.screenvision.sdk.TaskContext;
import com.screenvision.sdk.model.CompactPageAnalysisResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adapter that captures the current activity window and feeds it into ScreenVisionSdk.
 */
public final class ScreenVisionSnapshotAnalyzer implements ScreenSnapshotAnalyzer, AutoCloseable {

    private static final long PIXEL_COPY_TIMEOUT_MS = 3000L;
    private static final long ANALYZE_TIMEOUT_MS = 15000L;
    private static final String OBSERVATION_MODE = "screenvision_compact_ocr_ui";

    private final ScreenVisionSdk sdk;

    public ScreenVisionSnapshotAnalyzer(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        this.sdk = ScreenVisionSdk.create(context.getApplicationContext());
    }

    @Override
    public ScreenSnapshotAnalysis analyze(Activity activity, @Nullable String targetHint) throws Exception {
        if (activity == null) {
            throw new IllegalArgumentException("activity cannot be null");
        }
        Bitmap bitmap = captureActivityBitmap(activity);
        try {
            CompactPageAnalysisResult result = analyzeCompactBlocking(bitmap, targetHint);
            String screenSnapshotRef = "screenvision://capture/"
                    + activity.getClass().getSimpleName()
                    + "/"
                    + System.currentTimeMillis();
            return new ScreenSnapshotAnalysis(
                    activity.getClass().getName(),
                    OBSERVATION_MODE,
                    screenSnapshotRef,
                    result.toJson(),
                    null,
                    bitmap.getWidth(),
                    bitmap.getHeight()
            );
        } finally {
            bitmap.recycle();
        }
    }

    @Override
    public void close() {
        sdk.close();
    }

    private CompactPageAnalysisResult analyzeCompactBlocking(Bitmap bitmap, @Nullable String targetHint)
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
        if (!latch.await(ANALYZE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
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
        HandlerThread handlerThread = new HandlerThread("screen-vision-pixel-copy");
        handlerThread.start();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger resultCode = new AtomicInteger(PixelCopy.ERROR_UNKNOWN);
            PixelCopy.request(
                    window,
                    bitmap,
                    copyResult -> {
                        resultCode.set(copyResult);
                        latch.countDown();
                    },
                    new Handler(handlerThread.getLooper())
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
        } finally {
            handlerThread.quitSafely();
        }
    }
}
