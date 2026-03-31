package com.screenvision.sdk.model;

import com.screenvision.sdk.RecognitionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PageAnalysisResult {
    private final List<RecognizedTextBlock> textBlocks;
    private final List<RecognizedUiElement> uiElements;
    private final PageSize pageSize;
    private final long elapsedMs;
    private final RecognitionMode recognitionMode;

    public PageAnalysisResult(
            List<RecognizedTextBlock> textBlocks,
            List<RecognizedUiElement> uiElements,
            PageSize pageSize,
            long elapsedMs,
            RecognitionMode recognitionMode
    ) {
        this.textBlocks = Collections.unmodifiableList(new ArrayList<>(textBlocks));
        this.uiElements = Collections.unmodifiableList(new ArrayList<>(uiElements));
        this.pageSize = pageSize;
        this.elapsedMs = elapsedMs;
        this.recognitionMode = recognitionMode;
    }

    public List<RecognizedTextBlock> getTextBlocks() {
        return textBlocks;
    }

    public List<RecognizedUiElement> getUiElements() {
        return uiElements;
    }

    public PageSize getPageSize() {
        return pageSize;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public RecognitionMode getRecognitionMode() {
        return recognitionMode;
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"mode\":\"")
                .append(recognitionMode.name().toLowerCase(Locale.US))
                .append("\",");
        builder.append("\"page\":{");
        builder.append("\"width\":").append(pageSize.getWidth()).append(",");
        builder.append("\"height\":").append(pageSize.getHeight()).append("},");
        builder.append("\"elapsedMs\":").append(elapsedMs).append(",");
        builder.append("\"texts\":[");
        for (int index = 0; index < textBlocks.size(); index++) {
            RecognizedTextBlock block = textBlocks.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"text\":\"").append(escape(block.getText())).append("\",");
            appendBoundingBox(builder, block.getBoundingBox());
            builder.append(",\"confidence\":").append(block.getConfidence());
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"controls\":[");
        for (int index = 0; index < uiElements.size(); index++) {
            RecognizedUiElement element = uiElements.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"type\":\"").append(element.getType().name().toLowerCase(Locale.US)).append("\",");
            builder.append("\"label\":\"").append(escape(element.getLabel())).append("\",");
            appendBoundingBox(builder, element.getBoundingBox());
            builder.append(",\"confidence\":").append(element.getConfidence());
            builder.append("}");
        }
        builder.append("]");
        builder.append("}");
        return builder.toString();
    }

    public String toDebugJson() {
        return toJson();
    }

    private void appendBoundingBox(StringBuilder builder, BoundingBox boundingBox) {
        builder.append("\"bbox\":[")
                .append(boundingBox.getLeft()).append(",")
                .append(boundingBox.getTop()).append(",")
                .append(boundingBox.getRight()).append(",")
                .append(boundingBox.getBottom()).append("]");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
