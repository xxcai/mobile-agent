package com.screenvision.sdk.internal.mlkit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.screenvision.sdk.AnalyzeCallback;
import com.screenvision.sdk.RecognitionMode;
import com.screenvision.sdk.ScreenVisionSdkConfig;
import com.screenvision.sdk.internal.ElementAggregationMode;
import com.screenvision.sdk.internal.OfflineAnalyzer;
import com.screenvision.sdk.model.BoundingBox;
import com.screenvision.sdk.model.PageAnalysisResult;
import com.screenvision.sdk.model.PageSize;
import com.screenvision.sdk.model.RecognizedTextBlock;
import com.screenvision.sdk.model.RecognizedUiElement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MlKitOfflineAnalyzer implements OfflineAnalyzer {
    private final TextRecognizer recognizer;
    private final RuleBasedUiDetector uiDetector;
    private final ScreenVisionSdkConfig config;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MlKitOfflineAnalyzer(Context context, ScreenVisionSdkConfig config) {
        context.getApplicationContext();
        this.config = config;
        this.uiDetector = new RuleBasedUiDetector();
        this.recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }

    @Override
    public void analyze(Bitmap bitmap, ElementAggregationMode aggregationMode, AnalyzeCallback callback) {
        long start = SystemClock.elapsedRealtime();
        RecognitionMode mode = config.getRecognitionMode();
        ElementAggregationMode safeAggregationMode = aggregationMode == null
                ? ElementAggregationMode.DISABLED
                : aggregationMode;
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(visionText -> executor.execute(() -> {
                    try {
                        List<RecognizedTextBlock> allTextBlocks = new ArrayList<>();
                        for (com.google.mlkit.vision.text.Text.TextBlock block : visionText.getTextBlocks()) {
                            Rect bounds = block.getBoundingBox();
                            if (bounds == null) {
                                continue;
                            }
                            String text = block.getText() == null ? "" : block.getText().trim();
                            if (text.isEmpty()) {
                                continue;
                            }
                            allTextBlocks.add(new RecognizedTextBlock(
                                    text,
                                    new BoundingBox(bounds.left, bounds.top, bounds.right, bounds.bottom),
                                    1f
                            ));
                        }

                        List<RecognizedUiElement> uiElements = mode.includesUi()
                                ? uiDetector.detect(
                                bitmap,
                                allTextBlocks,
                                config.getUiConfidenceThreshold(),
                                safeAggregationMode == ElementAggregationMode.COMPACT_ONLY
                        )
                                : new ArrayList<>();

                        List<RecognizedTextBlock> returnedTextBlocks = mode.includesText()
                                ? allTextBlocks
                                : new ArrayList<>();

                        PageAnalysisResult result = new PageAnalysisResult(
                                returnedTextBlocks,
                                uiElements,
                                new PageSize(bitmap.getWidth(), bitmap.getHeight()),
                                SystemClock.elapsedRealtime() - start,
                                mode
                        );
                        mainHandler.post(() -> callback.onSuccess(result));
                    } catch (Throwable throwable) {
                        mainHandler.post(() -> callback.onError(throwable));
                    }
                }))
                .addOnFailureListener(throwable -> mainHandler.post(() -> callback.onError(throwable)));
    }

    @Override
    public void close() {
        recognizer.close();
        uiDetector.close();
        executor.shutdown();
    }
}
