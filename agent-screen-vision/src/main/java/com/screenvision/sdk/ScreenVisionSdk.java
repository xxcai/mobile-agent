package com.screenvision.sdk;

import android.content.Context;
import android.graphics.Bitmap;

import com.screenvision.sdk.internal.BitmapResizer;
import com.screenvision.sdk.internal.ElementAggregationMode;
import com.screenvision.sdk.internal.OfflineAnalyzer;
import com.screenvision.sdk.internal.compact.PageContextPruner;
import com.screenvision.sdk.internal.mlkit.MlKitOfflineAnalyzer;
import com.screenvision.sdk.model.BoundingBox;
import com.screenvision.sdk.model.CompactPageAnalysisResult;
import com.screenvision.sdk.model.PageAnalysisResult;
import com.screenvision.sdk.model.PageSize;
import com.screenvision.sdk.model.RecognizedTextBlock;
import com.screenvision.sdk.model.RecognizedUiElement;

import java.util.ArrayList;
import java.util.List;

public final class ScreenVisionSdk implements AutoCloseable {
    private final ScreenVisionSdkConfig config;
    private final OfflineAnalyzer analyzer;
    private final ScreenVisionSdkInfo sdkInfo;
    private final PageContextPruner pageContextPruner;

    private ScreenVisionSdk(Context context, ScreenVisionSdkConfig config) {
        this.config = config;
        this.analyzer = new MlKitOfflineAnalyzer(context, config);
        this.sdkInfo = new ScreenVisionSdkInfo(
                BuildConfig.VERSION_NAME,
                "mlkit_bundled_ocr+mnn_ui_fusion",
                true,
                true
        );
        this.pageContextPruner = new PageContextPruner();
    }

    public static ScreenVisionSdk create(Context context) {
        return new ScreenVisionSdk(context, ScreenVisionSdkConfig.newBuilder().build());
    }

    public static ScreenVisionSdk create(Context context, ScreenVisionSdkConfig config) {
        return new ScreenVisionSdk(context, config == null ? ScreenVisionSdkConfig.newBuilder().build() : config);
    }

    public ScreenVisionSdkConfig getConfig() {
        return config;
    }

    public ScreenVisionSdkInfo getSdkInfo() {
        return sdkInfo;
    }

    public void analyze(Bitmap bitmap, AnalyzeCallback callback) {
        analyzeInternal(bitmap, ElementAggregationMode.DISABLED, callback);
    }

    private void analyzeInternal(Bitmap bitmap, ElementAggregationMode aggregationMode, AnalyzeCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }
        if (bitmap == null) {
            callback.onError(new IllegalArgumentException("bitmap == null"));
            return;
        }

        Bitmap processedBitmap = BitmapResizer.scaleDownIfNeeded(bitmap, config.getMaxImageSidePx());
        boolean ownsProcessedBitmap = processedBitmap != bitmap;
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        analyzer.analyze(processedBitmap, aggregationMode, new AnalyzeCallback() {
            @Override
            public void onSuccess(PageAnalysisResult result) {
                if (ownsProcessedBitmap) {
                    processedBitmap.recycle();
                }
                callback.onSuccess(mapResultToOriginalSize(result, originalWidth, originalHeight));
            }

            @Override
            public void onError(Throwable throwable) {
                if (ownsProcessedBitmap) {
                    processedBitmap.recycle();
                }
                callback.onError(throwable);
            }
        });
    }

    public void analyzeToJson(Bitmap bitmap, AnalyzeJsonCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }
        analyze(bitmap, new AnalyzeCallback() {
            @Override
            public void onSuccess(PageAnalysisResult result) {
                callback.onSuccess(result.toJson());
            }

            @Override
            public void onError(Throwable throwable) {
                callback.onError(throwable);
            }
        });
    }

    public void analyzeCompact(Bitmap bitmap, AnalyzeCompactCallback callback) {
        analyzeCompact(bitmap, CompactAnalysisOptions.newBuilder().build(), TaskContext.empty(), callback);
    }

    public void analyzeCompact(Bitmap bitmap, TaskContext taskContext, AnalyzeCompactCallback callback) {
        analyzeCompact(bitmap, CompactAnalysisOptions.newBuilder().build(), taskContext, callback);
    }

    public void analyzeCompact(Bitmap bitmap, CompactAnalysisOptions options, AnalyzeCompactCallback callback) {
        analyzeCompact(bitmap, options, TaskContext.empty(), callback);
    }

    public void analyzeCompact(Bitmap bitmap, CompactAnalysisOptions options, TaskContext taskContext, AnalyzeCompactCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }
        analyzeInternal(bitmap, ElementAggregationMode.COMPACT_ONLY, new AnalyzeCallback() {
            @Override
            public void onSuccess(PageAnalysisResult result) {
                CompactAnalysisOptions safeOptions = options == null ? CompactAnalysisOptions.newBuilder().build() : options;
                TaskContext safeTaskContext = taskContext == null ? TaskContext.empty() : taskContext;
                callback.onSuccess(pageContextPruner.prune(result, safeOptions, safeTaskContext));
            }

            @Override
            public void onError(Throwable throwable) {
                callback.onError(throwable);
            }
        });
    }

    public void analyzeCompactToJson(Bitmap bitmap, AnalyzeJsonCallback callback) {
        analyzeCompactToJson(bitmap, CompactAnalysisOptions.newBuilder().build(), TaskContext.empty(), callback);
    }

    public void analyzeCompactToJson(Bitmap bitmap, TaskContext taskContext, AnalyzeJsonCallback callback) {
        analyzeCompactToJson(bitmap, CompactAnalysisOptions.newBuilder().build(), taskContext, callback);
    }

    public void analyzeCompactToJson(Bitmap bitmap, CompactAnalysisOptions options, AnalyzeJsonCallback callback) {
        analyzeCompactToJson(bitmap, options, TaskContext.empty(), callback);
    }

    public void analyzeCompactToJson(Bitmap bitmap, CompactAnalysisOptions options, TaskContext taskContext, AnalyzeJsonCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback == null");
        }
        analyzeCompact(bitmap, options, taskContext, new AnalyzeCompactCallback() {
            @Override
            public void onSuccess(CompactPageAnalysisResult result) {
                callback.onSuccess(result.toJson());
            }

            @Override
            public void onError(Throwable throwable) {
                callback.onError(throwable);
            }
        });
    }

    @Override
    public void close() {
        analyzer.close();
    }

    private PageAnalysisResult mapResultToOriginalSize(PageAnalysisResult result, int originalWidth, int originalHeight) {
        if (result == null || result.getPageSize() == null) {
            return result;
        }
        PageSize pageSize = result.getPageSize();
        if (pageSize.getWidth() == originalWidth && pageSize.getHeight() == originalHeight) {
            return result;
        }

        float scaleX = originalWidth / (float) Math.max(1, pageSize.getWidth());
        float scaleY = originalHeight / (float) Math.max(1, pageSize.getHeight());
        List<RecognizedTextBlock> scaledTextBlocks = new ArrayList<>(result.getTextBlocks().size());
        for (RecognizedTextBlock textBlock : result.getTextBlocks()) {
            scaledTextBlocks.add(new RecognizedTextBlock(
                    textBlock.getText(),
                    scaleBoundingBox(textBlock.getBoundingBox(), scaleX, scaleY, originalWidth, originalHeight),
                    textBlock.getConfidence()
            ));
        }

        List<RecognizedUiElement> scaledUiElements = new ArrayList<>(result.getUiElements().size());
        for (RecognizedUiElement uiElement : result.getUiElements()) {
            scaledUiElements.add(new RecognizedUiElement(
                    uiElement.getType(),
                    scaleBoundingBox(uiElement.getBoundingBox(), scaleX, scaleY, originalWidth, originalHeight),
                    uiElement.getConfidence(),
                    uiElement.getLabel()
            ));
        }

        return new PageAnalysisResult(
                scaledTextBlocks,
                scaledUiElements,
                new PageSize(originalWidth, originalHeight),
                result.getElapsedMs(),
                result.getRecognitionMode()
        );
    }

    private BoundingBox scaleBoundingBox(BoundingBox source, float scaleX, float scaleY, int maxWidth, int maxHeight) {
        int safeMaxWidth = Math.max(1, maxWidth);
        int safeMaxHeight = Math.max(1, maxHeight);
        int left = clamp(Math.round(source.getLeft() * scaleX), 0, safeMaxWidth - 1);
        int top = clamp(Math.round(source.getTop() * scaleY), 0, safeMaxHeight - 1);
        int right = clamp(Math.round(source.getRight() * scaleX), left + 1, safeMaxWidth);
        int bottom = clamp(Math.round(source.getBottom() * scaleY), top + 1, safeMaxHeight);
        return new BoundingBox(left, top, right, bottom);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
