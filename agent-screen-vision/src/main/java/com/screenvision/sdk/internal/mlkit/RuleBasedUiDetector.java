package com.screenvision.sdk.internal.mlkit;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.screenvision.sdk.internal.mnn.MnnUiClassifier;
import com.screenvision.sdk.model.BoundingBox;
import com.screenvision.sdk.model.RecognizedTextBlock;
import com.screenvision.sdk.model.RecognizedUiElement;
import com.screenvision.sdk.model.UiElementType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuleBasedUiDetector implements AutoCloseable {
    private static final int CLASS_BACKGROUND = 0;
    private static final int CLASS_BUTTON = 1;
    private static final int CLASS_INPUT = 2;
    private static final int CLASS_CARD = 3;
    private static final int CLASS_IMAGE = 4;

    private static final int FEATURE_BUTTON_KEYWORD = 5;
    private static final int FEATURE_INPUT_KEYWORD = 6;
    private static final int FEATURE_NO_TEXT = 15;
    private static final int FEATURE_IMAGE_SOURCE = 16;
    private static final int FEATURE_VISUAL_SOURCE = 17;
    private static final int FEATURE_TEXT_SOURCE = 18;
    private static final int FEATURE_CARD_LAYOUT = 19;
    private static final int FEATURE_SQUARE_SHAPE = 20;

    private static final String[] BUTTON_KEYWORDS = new String[]{
            "\u767b\u5f55", "\u786e\u5b9a", "\u63d0\u4ea4", "\u4e0b\u4e00\u6b65", "\u5b8c\u6210", "\u4fdd\u5b58", "\u53d1\u9001", "\u641c\u7d22", "\u7ee7\u7eed", "\u786e\u8ba4", "\u53d6\u6d88", "\u5f00\u59cb",
            "login", "sign in", "submit", "confirm", "next", "continue", "save", "open", "search", "cancel", "start", "ok"
    };
    private static final String[] INPUT_KEYWORDS = new String[]{
            "\u8bf7\u8f93\u5165", "\u8f93\u5165", "\u641c\u7d22", "\u624b\u673a\u53f7", "\u7528\u6237\u540d", "\u5bc6\u7801", "\u9a8c\u8bc1\u7801", "\u90ae\u7bb1", "\u8d26\u53f7", "\u5173\u952e\u5b57",
            "input", "search", "phone", "email", "password", "name", "account", "keyword"
    };
    private static final String[] LINK_KEYWORDS = new String[]{
            "\u66f4\u591a", "\u67e5\u770b", "\u8be6\u60c5", "\u67e5\u770b\u5168\u90e8", "\u5c55\u5f00", "\u6536\u8d77", "\u8df3\u8fc7", "\u6ce8\u518c", "\u5fd8\u8bb0\u5bc6\u7801", "\u5e2e\u52a9", "\u8fd4\u56de", "\u7f16\u8f91", "\u5220\u9664", "\u7ba1\u7406", "\u5237\u65b0", "\u91cd\u8bd5",
            "more", "view", "details", "learn more", "skip", "register", "forgot", "help", "edit", "delete", "manage", "retry", "refresh"
    };
    private static final String[] SWITCH_KEYWORDS = new String[]{
            "\u5f00\u542f", "\u5173\u95ed", "\u901a\u77e5", "\u5141\u8bb8", "\u81ea\u52a8", "\u8bb0\u4f4f", "\u540c\u610f", "\u8ba2\u9605", "\u9690\u79c1", "\u5b9a\u4f4d", "\u84dd\u7259", "wifi", "switch", "toggle", "enable", "allow", "remember", "notify", "notification", "auto", "privacy"
    };
    private static final String[] ICON_HINT_KEYWORDS = new String[]{
            "\u7535\u8bdd", "\u62e8\u53f7", "\u8054\u7cfb", "\u7535\u8bdd\u53f7\u7801", "\u65b0\u5efa", "\u6dfb\u52a0", "\u521b\u5efa", "\u53d1\u8d77", "\u66f4\u591a", "\u6253\u5f00", "\u5206\u4eab",
            "phone", "call", "contact", "dial", "add", "new", "create", "open", "more", "share", "plus"
    };

    private final MnnUiClassifier classifier;

    public RuleBasedUiDetector() {
        this(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
    }

    public RuleBasedUiDetector(int numThreads) {
        classifier = new MnnUiClassifier(Math.max(1, numThreads));
    }

    public List<RecognizedUiElement> detect(
            Bitmap bitmap,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence,
            boolean enableElementAggregation
    ) {
        if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return new ArrayList<>();
        }

        BitmapInspector inspector = new BitmapInspector(bitmap);
        List<RecognizedUiElement> candidates = new ArrayList<>();
        candidates.addAll(detectTextAnchoredControls(bitmap, inspector, textBlocks, minConfidence));
        candidates.addAll(detectVisualContainers(bitmap, inspector, textBlocks, minConfidence));
        candidates.addAll(detectImageLikeRegions(bitmap, inspector, textBlocks, minConfidence));
        candidates.addAll(detectCompactIcons(bitmap, inspector, textBlocks, minConfidence));
        candidates.addAll(detectTextAccessoryIcons(bitmap, inspector, textBlocks, minConfidence));
        candidates.addAll(detectLabeledAccessoryControls(bitmap, inspector, textBlocks, minConfidence));
        candidates.addAll(detectTabLikeElements(bitmap, textBlocks, minConfidence));
        candidates.addAll(detectTextLinks(bitmap, textBlocks, minConfidence));
        List<RecognizedUiElement> preparedCandidates = enableElementAggregation
                ? mergeCandidates(candidates)
                : dedupeRawCandidates(candidates);
        return refineCandidateTypes(preparedCandidates, inspector, textBlocks, bitmap);
    }

    public List<RecognizedUiElement> detect(Bitmap bitmap, List<RecognizedTextBlock> textBlocks, float minConfidence) {
        return detect(bitmap, textBlocks, minConfidence, true);
    }
    @Override
    public void close() {
        classifier.close();
    }

    private List<RecognizedUiElement> detectLabeledAccessoryControls(
            Bitmap bitmap,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        List<RecognizedUiElement> result = new ArrayList<>();
        List<Rect> seenProposals = new ArrayList<>();
        for (RecognizedTextBlock block : textBlocks) {
            Rect textRect = toRect(block.getBoundingBox());
            if (textRect.height() < 14 || textRect.height() > 72) {
                continue;
            }
            String label = block.getText() == null ? "" : block.getText().trim();
            if (label.isEmpty()) {
                continue;
            }

            int centerY = Math.round(textRect.exactCenterY());
            int controlSize = Math.max(18, Math.min(56, Math.round(textRect.height() * 0.95f)));
            int gap = Math.max(6, Math.min(18, Math.round(textRect.height() * 0.45f)));
            Rect leftCandidate = new Rect(
                    textRect.left - gap - controlSize,
                    centerY - controlSize / 2,
                    textRect.left - gap,
                    centerY + controlSize / 2
            );
            if (leftCandidate.left >= 0 && leftCandidate.top >= 0 && leftCandidate.right <= bitmap.getWidth() && leftCandidate.bottom <= bitmap.getHeight()) {
                if (!intersectsText(leftCandidate, textBlocks) && !containsSimilarRect(seenProposals, leftCandidate)) {
                    RecognizedUiElement choice = buildChoiceAccessoryCandidate(inspector, leftCandidate, label, minConfidence);
                    if (choice != null) {
                        seenProposals.add(new Rect(leftCandidate));
                        result.add(choice);
                    }
                }
            }

            int switchHeight = Math.max(18, Math.min(36, Math.round(textRect.height() * 0.92f)));
            int switchWidth = Math.max(30, Math.min(72, Math.round(switchHeight * 1.85f)));
            Rect rightCandidate = new Rect(
                    textRect.right + gap,
                    centerY - switchHeight / 2,
                    textRect.right + gap + switchWidth,
                    centerY + switchHeight / 2
            );
            if (rightCandidate.left >= 0 && rightCandidate.top >= 0 && rightCandidate.right <= bitmap.getWidth() && rightCandidate.bottom <= bitmap.getHeight()) {
                if (!intersectsText(rightCandidate, textBlocks) && !containsSimilarRect(seenProposals, rightCandidate)) {
                    RecognizedUiElement toggle = buildSwitchAccessoryCandidate(inspector, rightCandidate, label, minConfidence);
                    if (toggle != null) {
                        seenProposals.add(new Rect(rightCandidate));
                        result.add(toggle);
                    }
                }
            }
        }
        return result;
    }

    private RecognizedUiElement buildChoiceAccessoryCandidate(
            BitmapInspector inspector,
            Rect rect,
            String label,
            float minConfidence
    ) {
        RectMetrics metrics = inspector.measure(rect);
        float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
        float edgeDensity = inspector.edgeDensity(rect);
        float cornerEdgeDensity = inspector.cornerEdgeDensity(rect);
        float contrastScore = clamp((dominantContrast - 4.5f) / 12f, 0f, 1f);
        float confidence = clamp(
                0.40f
                        + contrastScore * 0.24f
                        + clamp(edgeDensity / 0.45f, 0f, 1f) * 0.20f,
                0f,
                0.90f
        );
        if (confidence < Math.max(0.44f, minConfidence)) {
            return null;
        }
        if (edgeDensity > 0.34f && metrics.fillVariance > 1400f && cornerEdgeDensity > edgeDensity * 0.72f) {
            return null;
        }

        UiElementType type = cornerEdgeDensity < edgeDensity * 0.58f
                ? UiElementType.RADIO
                : UiElementType.CHECKBOX;
        String normalizedLabel = normalizeText(label);
        if (keywordScore(normalizedLabel, SWITCH_KEYWORDS) > 0.6f) {
            confidence = clamp(confidence - 0.06f, 0f, 0.86f);
        }
        return new RecognizedUiElement(type, toBoundingBox(rect), confidence, label);
    }

    private RecognizedUiElement buildSwitchAccessoryCandidate(
            BitmapInspector inspector,
            Rect rect,
            String label,
            float minConfidence
    ) {
        RectMetrics metrics = inspector.measure(rect);
        float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
        float edgeDensity = inspector.edgeDensity(rect);
        float aspect = rect.width() / (float) Math.max(1, rect.height());
        float contrastScore = clamp((dominantContrast - 4f) / 12f, 0f, 1f);
        float shapeScore = 1f - clamp(Math.abs(aspect - 1.9f) / 1.2f, 0f, 1f);
        float keywordBoost = keywordScore(normalizeText(label), SWITCH_KEYWORDS);
        float confidence = clamp(
                0.38f
                        + contrastScore * 0.18f
                        + clamp(edgeDensity / 0.35f, 0f, 1f) * 0.14f
                        + shapeScore * 0.18f
                        + keywordBoost * 0.12f,
                0f,
                0.90f
        );
        if (confidence < Math.max(minConfidence + 0.03f, 0.46f)) {
            return null;
        }
        return new RecognizedUiElement(UiElementType.SWITCH, toBoundingBox(rect), confidence, label);
    }

    private List<RecognizedUiElement> detectTabLikeElements(
            Bitmap bitmap,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        List<RecognizedUiElement> result = new ArrayList<>();
        List<Rect> seenProposals = new ArrayList<>();
        for (RecognizedTextBlock block : textBlocks) {
            Rect textRect = toRect(block.getBoundingBox());
            String label = block.getText() == null ? "" : block.getText().trim();
            if (label.isEmpty() || textRect.width() < 20 || textRect.height() < 12) {
                continue;
            }

            int rowCount = 0;
            int minLeft = textRect.left;
            int maxRight = textRect.right;
            for (RecognizedTextBlock other : textBlocks) {
                Rect otherRect = toRect(other.getBoundingBox());
                if (!isSameTextRow(textRect, otherRect)) {
                    continue;
                }
                rowCount++;
                minLeft = Math.min(minLeft, otherRect.left);
                maxRight = Math.max(maxRight, otherRect.right);
            }
            if (rowCount < 2) {
                continue;
            }

            float spanRatio = (maxRight - minLeft) / (float) Math.max(1, bitmap.getWidth());
            if (spanRatio < 0.35f) {
                continue;
            }

            Rect candidate = expandRect(
                    textRect,
                    bitmap,
                    Math.max(10, Math.round(textRect.width() * 0.28f)),
                    Math.max(8, Math.round(textRect.height() * 0.65f))
            );
            if (containsSimilarRect(seenProposals, candidate)) {
                continue;
            }

            float rowScore = clamp((rowCount - 2f) / 3f, 0f, 1f);
            float spanScore = clamp((spanRatio - 0.35f) / 0.45f, 0f, 1f);
            float shortTextScore = 1f - clamp((label.length() - 6f) / 10f, 0f, 1f);
            float confidence = clamp(
                    0.50f + rowScore * 0.16f + spanScore * 0.08f + shortTextScore * 0.06f,
                    0f,
                    0.88f
            );
            if (confidence < Math.max(minConfidence + 0.06f, 0.54f)) {
                continue;
            }
            seenProposals.add(new Rect(candidate));
            result.add(new RecognizedUiElement(UiElementType.TAB, toBoundingBox(candidate), confidence, label));
        }
        return result;
    }

    private boolean isSameTextRow(Rect first, Rect second) {
        float centerDistance = Math.abs(first.exactCenterY() - second.exactCenterY());
        float maxHeight = Math.max(first.height(), second.height());
        return centerDistance <= maxHeight * 0.7f;
    }

    private List<RecognizedUiElement> detectCompactIcons(
            Bitmap bitmap,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        List<RecognizedUiElement> result = new ArrayList<>();
        List<Rect> seenProposals = new ArrayList<>();
        int[] columnOptions = new int[]{24, 32, 40, 48};
        for (int columns : columnOptions) {
            result.addAll(detectCompactIconsAtScale(bitmap, inspector, textBlocks, minConfidence, columns, seenProposals));
        }
        return result;
    }

    private List<RecognizedUiElement> detectCompactIconsAtScale(
            Bitmap bitmap,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence,
            int columns,
            List<Rect> seenProposals
    ) {
        int rows = Math.max(columns, Math.round(columns * bitmap.getHeight() / (float) bitmap.getWidth()));
        int cellWidth = Math.max(1, bitmap.getWidth() / columns);
        int cellHeight = Math.max(1, bitmap.getHeight() / rows);
        boolean[][] occupied = new boolean[rows][columns];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                Rect cell = new Rect(
                        col * cellWidth,
                        row * cellHeight,
                        Math.min(bitmap.getWidth(), (col + 1) * cellWidth),
                        Math.min(bitmap.getHeight(), (row + 1) * cellHeight)
                );
                if (cell.width() < 4 || cell.height() < 4) {
                    continue;
                }

                float overlapRatio = textOverlapRatio(cell, textBlocks);
                if (overlapRatio > 0.26f) {
                    continue;
                }

                RectMetrics metrics = inspector.measure(cell);
                float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
                float edgeDensity = inspector.edgeDensity(cell);
                float areaRatio = cell.width() * cell.height() / (float) Math.max(1, bitmap.getWidth() * bitmap.getHeight());
                float aspect = cell.width() / (float) Math.max(1, cell.height());
                boolean likelyIconCell = (dominantContrast >= 5.6f
                        && edgeDensity >= 0.10f
                        && areaRatio <= 0.0038f
                        && aspect >= 0.28f
                        && aspect <= 3.40f)
                        || (dominantContrast >= 4.8f
                        && edgeDensity >= 0.18f
                        && overlapRatio <= 0.08f
                        && areaRatio <= 0.0025f
                        && aspect >= 0.35f
                        && aspect <= 2.80f);
                if (likelyIconCell) {
                    occupied[row][col] = true;
                }
            }
        }

        boolean[][] visited = new boolean[rows][columns];
        List<RecognizedUiElement> result = new ArrayList<>();
        int[][] directions = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (!occupied[row][col] || visited[row][col]) {
                    continue;
                }

                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{row, col});
                visited[row][col] = true;

                int minRow = row;
                int maxRow = row;
                int minCol = col;
                int maxCol = col;
                int cellCount = 0;

                while (!queue.isEmpty()) {
                    int[] current = queue.removeFirst();
                    int currentRow = current[0];
                    int currentCol = current[1];
                    cellCount++;
                    minRow = Math.min(minRow, currentRow);
                    maxRow = Math.max(maxRow, currentRow);
                    minCol = Math.min(minCol, currentCol);
                    maxCol = Math.max(maxCol, currentCol);

                    for (int[] direction : directions) {
                        int nextRow = currentRow + direction[0];
                        int nextCol = currentCol + direction[1];
                        if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= columns) {
                            continue;
                        }
                        if (!occupied[nextRow][nextCol] || visited[nextRow][nextCol]) {
                            continue;
                        }
                        visited[nextRow][nextCol] = true;
                        queue.add(new int[]{nextRow, nextCol});
                    }
                }

                if (cellCount > 32) {
                    continue;
                }

                Rect rect = new Rect(
                        minCol * cellWidth,
                        minRow * cellHeight,
                        Math.min(bitmap.getWidth(), (maxCol + 1) * cellWidth),
                        Math.min(bitmap.getHeight(), (maxRow + 1) * cellHeight)
                );
                int padX = Math.min(12, Math.max(2, Math.round(cellWidth * 0.35f)));
                int padY = Math.min(12, Math.max(2, Math.round(cellHeight * 0.35f)));
                rect = new Rect(
                        Math.max(0, rect.left - padX),
                        Math.max(0, rect.top - padY),
                        Math.min(bitmap.getWidth(), rect.right + padX),
                        Math.min(bitmap.getHeight(), rect.bottom + padY)
                );
                if (containsSimilarRect(seenProposals, rect)) {
                    continue;
                }
                RecognizedUiElement icon = buildCompactIconCandidate(bitmap, inspector, rect, textBlocks, minConfidence);
                if (icon != null) {
                    seenProposals.add(toRect(icon.getBoundingBox()));
                    result.add(icon);
                }
            }
        }
        return result;
    }

    private List<RecognizedUiElement> detectTextAccessoryIcons(
            Bitmap bitmap,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        List<RecognizedUiElement> result = new ArrayList<>();
        List<Rect> seenProposals = new ArrayList<>();
        for (RecognizedTextBlock block : textBlocks) {
            Rect textRect = toRect(block.getBoundingBox());
            String label = block.getText() == null ? "" : block.getText().trim();
            if (label.isEmpty() || textRect.height() < 14 || textRect.height() > 68) {
                continue;
            }

            int iconSize = Math.max(16, Math.min(54, Math.round(textRect.height() * 1.15f)));
            int gap = Math.max(4, Math.min(18, Math.round(textRect.height() * 0.40f)));
            Rect[] candidates = new Rect[]{
                    new Rect(
                            textRect.left - gap - iconSize,
                            Math.round(textRect.exactCenterY()) - iconSize / 2,
                            textRect.left - gap,
                            Math.round(textRect.exactCenterY()) + iconSize / 2
                    ),
                    new Rect(
                            textRect.right + gap,
                            Math.round(textRect.exactCenterY()) - iconSize / 2,
                            textRect.right + gap + iconSize,
                            Math.round(textRect.exactCenterY()) + iconSize / 2
                    )
            };

            for (Rect candidate : candidates) {
                if (candidate.left < 0 || candidate.top < 0 || candidate.right > bitmap.getWidth() || candidate.bottom > bitmap.getHeight()) {
                    continue;
                }
                if (textOverlapRatio(candidate, textBlocks) > 0.10f || containsSimilarRect(seenProposals, candidate)) {
                    continue;
                }
                RecognizedUiElement icon = buildTextAccessoryIconCandidate(inspector, candidate, label, minConfidence);
                if (icon != null) {
                    seenProposals.add(toRect(icon.getBoundingBox()));
                    result.add(icon);
                }
            }
        }
        return result;
    }

    private RecognizedUiElement buildCompactIconCandidate(
            Bitmap bitmap,
            BitmapInspector inspector,
            Rect rect,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        if (rect.width() < 8 || rect.height() < 8) {
            return null;
        }
        if (rect.width() > bitmap.getWidth() / 3 || rect.height() > bitmap.getHeight() / 3) {
            return null;
        }

        float aspect = rect.width() / (float) Math.max(1, rect.height());
        float areaRatio = rect.width() * rect.height() / (float) Math.max(1, bitmap.getWidth() * bitmap.getHeight());
        if (areaRatio > 0.018f || aspect < 0.25f || aspect > 3.60f) {
            return null;
        }

        RectMetrics metrics = inspector.measure(rect);
        float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
        float edgeDensity = inspector.edgeDensity(rect);
        float cornerEdgeDensity = inspector.cornerEdgeDensity(rect);
        String nearbyLabel = normalizeText(buildNearbyText(rect, textBlocks));
        float hintScore = keywordScore(nearbyLabel, ICON_HINT_KEYWORDS);
        boolean nearbyText = !nearbyLabel.isEmpty() || hasAlignedTextToRight(rect, textBlocks);
        float squareScore = 1f - clamp(Math.abs(aspect - 1f) / 1.25f, 0f, 1f);
        float contrastScore = clamp((dominantContrast - 4.6f) / 13.5f, 0f, 1f);
        float densityScore = clamp((edgeDensity - 0.09f) / 0.28f, 0f, 1f);
        float cornerScore = clamp(cornerEdgeDensity / Math.max(0.10f, edgeDensity + 0.02f), 0f, 1f);
        float confidence = clamp(
                0.32f
                        + contrastScore * 0.18f
                        + densityScore * 0.20f
                        + squareScore * 0.08f
                        + cornerScore * 0.10f
                        + (nearbyText ? 0.08f : 0f)
                        + hintScore * 0.12f,
                0f,
                0.94f
        );
        float threshold = Math.max(minConfidence - 0.04f, 0.42f - hintScore * 0.06f);
        if (dominantContrast < 4.6f || edgeDensity < 0.09f || confidence < threshold) {
            return null;
        }

        UiElementType type = UiElementType.ICON_BUTTON;
        String label = "icon_button";
        if (!nearbyText && hintScore < 0.20f && areaRatio > 0.007f && aspect > 1.55f && edgeDensity < 0.20f) {
            type = UiElementType.IMAGE;
            label = "image";
            confidence = clamp(confidence - 0.05f, 0f, 0.90f);
        }
        return new RecognizedUiElement(type, toBoundingBox(rect), confidence, label);
    }

    private RecognizedUiElement buildTextAccessoryIconCandidate(
            BitmapInspector inspector,
            Rect rect,
            String label,
            float minConfidence
    ) {
        RectMetrics metrics = inspector.measure(rect);
        float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
        float edgeDensity = inspector.edgeDensity(rect);
        float cornerEdgeDensity = inspector.cornerEdgeDensity(rect);
        float aspect = rect.width() / (float) Math.max(1, rect.height());
        float hintScore = keywordScore(normalizeText(label), ICON_HINT_KEYWORDS);
        float squareScore = 1f - clamp(Math.abs(aspect - 1f) / 1.40f, 0f, 1f);
        float confidence = clamp(
                0.34f
                        + clamp((dominantContrast - 4.2f) / 12f, 0f, 1f) * 0.18f
                        + clamp((edgeDensity - 0.08f) / 0.26f, 0f, 1f) * 0.18f
                        + clamp(cornerEdgeDensity / Math.max(0.10f, edgeDensity + 0.02f), 0f, 1f) * 0.10f
                        + squareScore * 0.08f
                        + hintScore * 0.18f,
                0f,
                0.94f
        );
        float threshold = Math.max(minConfidence, 0.44f - hintScore * 0.08f);
        if (dominantContrast < 4.2f || edgeDensity < 0.08f || confidence < threshold) {
            return null;
        }
        String resolvedLabel = hintScore >= 0.45f ? label : "icon_button";
        return new RecognizedUiElement(UiElementType.ICON_BUTTON, toBoundingBox(rect), confidence, resolvedLabel);
    }

    private String buildNearbyText(Rect rect, List<RecognizedTextBlock> textBlocks) {
        Rect searchRect = new Rect(
                Math.max(0, rect.left - rect.width() * 2),
                Math.max(0, rect.top - rect.height()),
                rect.right + rect.width() * 3,
                rect.bottom + rect.height()
        );
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            if (Rect.intersects(rect, textRect) || !Rect.intersects(searchRect, textRect)) {
                continue;
            }
            String text = textBlock.getText() == null ? "" : textBlock.getText().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(text);
            count++;
            if (count >= 3 || builder.length() >= 32) {
                break;
            }
        }
        return builder.toString();
    }

    private boolean hasNearbyText(Rect rect, List<RecognizedTextBlock> textBlocks) {
        return !buildNearbyText(rect, textBlocks).isEmpty();
    }

    private float textOverlapRatio(Rect rect, List<RecognizedTextBlock> textBlocks) {
        int overlapArea = 0;
        int rectArea = Math.max(1, rect.width() * rect.height());
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            int left = Math.max(rect.left, textRect.left);
            int top = Math.max(rect.top, textRect.top);
            int right = Math.min(rect.right, textRect.right);
            int bottom = Math.min(rect.bottom, textRect.bottom);
            if (right <= left || bottom <= top) {
                continue;
            }
            overlapArea += (right - left) * (bottom - top);
        }
        return overlapArea / (float) rectArea;
    }

    private List<RecognizedUiElement> detectTextLinks(
            Bitmap bitmap,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        List<RecognizedUiElement> result = new ArrayList<>();
        List<Rect> seenProposals = new ArrayList<>();
        for (RecognizedTextBlock block : textBlocks) {
            Rect textRect = toRect(block.getBoundingBox());
            String label = block.getText() == null ? "" : block.getText().trim();
            if (label.isEmpty() || textRect.width() < 18 || textRect.height() < 10) {
                continue;
            }
            if (textRect.width() > bitmap.getWidth() * 0.35f || textRect.height() > bitmap.getHeight() * 0.08f) {
                continue;
            }

            String normalizedLabel = normalizeText(label);
            float linkKeywordScore = keywordScore(normalizedLabel, LINK_KEYWORDS);
            float aspect = textRect.width() / (float) Math.max(1, textRect.height());
            float shortTextScore = 1f - clamp((label.length() - 8f) / 10f, 0f, 1f);
            if (linkKeywordScore < 0.45f && (shortTextScore < 0.55f || aspect < 1.8f)) {
                continue;
            }

            Rect candidate = expandRect(
                    textRect,
                    bitmap,
                    Math.max(8, Math.round(textRect.width() * 0.16f)),
                    Math.max(6, Math.round(textRect.height() * 0.35f))
            );
            if (containsSimilarRect(seenProposals, candidate)) {
                continue;
            }
            float aspectScore = clamp((aspect - 1.8f) / 3.5f, 0f, 1f);
            float confidence = clamp(
                    0.42f + linkKeywordScore * 0.26f + shortTextScore * 0.12f + aspectScore * 0.08f,
                    0f,
                    0.86f
            );
            if (confidence < Math.max(minConfidence, 0.48f)) {
                continue;
            }
            seenProposals.add(new Rect(candidate));
            result.add(new RecognizedUiElement(UiElementType.TEXT_LINK, toBoundingBox(candidate), confidence, label));
        }
        return result;
    }

    private List<RecognizedUiElement> refineCandidateTypes(
            List<RecognizedUiElement> candidates,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            Bitmap bitmap
    ) {
        List<RecognizedUiElement> result = new ArrayList<>(candidates.size());
        for (RecognizedUiElement candidate : candidates) {
            result.add(refineCandidateType(candidate, inspector, textBlocks, bitmap));
        }
        return result;
    }

    private RecognizedUiElement refineCandidateType(
            RecognizedUiElement candidate,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            Bitmap bitmap
    ) {
        UiElementType type = candidate.getType();
        if (type != UiElementType.IMAGE && type != UiElementType.BUTTON) {
            return candidate;
        }

        Rect rect = toRect(candidate.getBoundingBox());
        float aspect = rect.width() / (float) Math.max(1, rect.height());
        float areaRatio = rect.width() * rect.height() / (float) Math.max(1, bitmap.getWidth() * bitmap.getHeight());
        int textCount = countTextInside(rect, textBlocks);
        float edgeDensity = inspector.edgeDensity(rect);
        float confidence = candidate.getConfidence();

        if (textCount == 0 && aspect >= 0.75f && aspect <= 1.35f) {
            if (hasAlignedTextToRight(rect, textBlocks) && areaRatio >= 0.0004f && areaRatio <= 0.03f) {
                return new RecognizedUiElement(UiElementType.AVATAR, candidate.getBoundingBox(), clamp(confidence + 0.04f, 0f, 0.99f), "avatar");
            }
            if (areaRatio <= 0.008f && edgeDensity >= 0.16f) {
                return new RecognizedUiElement(UiElementType.ICON_BUTTON, candidate.getBoundingBox(), clamp(confidence + 0.03f, 0f, 0.97f), "icon_button");
            }
        }

        if (type == UiElementType.BUTTON && textCount == 0 && areaRatio <= 0.012f && aspect >= 0.8f && aspect <= 1.4f) {
            return new RecognizedUiElement(UiElementType.ICON_BUTTON, candidate.getBoundingBox(), clamp(confidence + 0.02f, 0f, 0.95f), "icon_button");
        }
        return candidate;
    }

    private boolean hasAlignedTextToRight(Rect rect, List<RecognizedTextBlock> textBlocks) {
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            if (textRect.left < rect.right) {
                continue;
            }
            if (textRect.left - rect.right > rect.width() * 3) {
                continue;
            }
            int overlapTop = Math.max(rect.top, textRect.top);
            int overlapBottom = Math.min(rect.bottom, textRect.bottom);
            if (overlapBottom <= overlapTop) {
                continue;
            }
            float overlapRatio = (overlapBottom - overlapTop) / (float) Math.max(1, Math.min(rect.height(), textRect.height()));
            if (overlapRatio >= 0.35f) {
                return true;
            }
        }
        return false;
    }
    private List<RecognizedUiElement> detectTextAnchoredControls(
            Bitmap bitmap,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        List<RecognizedUiElement> result = new ArrayList<>();
        List<Rect> seenProposals = new ArrayList<>();
        for (RecognizedTextBlock block : textBlocks) {
            Rect textRect = toRect(block.getBoundingBox());
            if (textRect.width() < 24 || textRect.height() < 12) {
                continue;
            }

            String normalizedText = normalizeText(block.getText());
            float buttonKeywordScore = keywordScore(normalizedText, BUTTON_KEYWORDS);
            float inputKeywordScore = keywordScore(normalizedText, INPUT_KEYWORDS);
            List<Rect> proposals = buildTextAnchoredProposals(bitmap, textRect, buttonKeywordScore, inputKeywordScore);
            for (Rect candidate : proposals) {
                if (candidate.width() < 72 || candidate.height() < 36) {
                    continue;
                }
                if (containsSimilarRect(seenProposals, candidate)) {
                    continue;
                }
                seenProposals.add(new Rect(candidate));

                RectMetrics metrics = inspector.measure(candidate);
                float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
                if (dominantContrast < 5.5f || metrics.fillVariance > 9200f) {
                    continue;
                }

                RecognizedUiElement element = createCandidate(
                        candidate,
                        bitmap,
                        inspector,
                        metrics,
                        textBlocks,
                        minConfidence,
                        ProposalSource.TEXT_ANCHOR
                );
                if (element != null) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    private List<Rect> buildTextAnchoredProposals(
            Bitmap bitmap,
            Rect textRect,
            float buttonKeywordScore,
            float inputKeywordScore
    ) {
        List<Rect> proposals = new ArrayList<>();
        float aspect = textRect.width() / (float) Math.max(1, textRect.height());
        proposals.add(expandRect(
                textRect,
                bitmap,
                Math.max(14, Math.round(textRect.width() * 0.32f)),
                Math.max(10, Math.round(textRect.height() * 0.65f))
        ));
        proposals.add(expandRect(
                textRect,
                bitmap,
                Math.max(18, Math.round(textRect.width() * 0.48f)),
                Math.max(12, Math.round(textRect.height() * 0.90f))
        ));
        if (inputKeywordScore >= 0.45f || aspect >= 3.2f) {
            proposals.add(expandRect(
                    textRect,
                    bitmap,
                    Math.max(30, Math.round(textRect.width() * 0.95f)),
                    Math.max(14, Math.round(textRect.height() * 1.05f))
            ));
        }
        if (buttonKeywordScore >= 0.45f || aspect <= 4.8f) {
            proposals.add(expandRect(
                    textRect,
                    bitmap,
                    Math.max(20, Math.round(textRect.width() * 0.42f)),
                    Math.max(12, Math.round(textRect.height() * 0.80f))
            ));
        }
        return proposals;
    }

    private Rect expandRect(Rect textRect, Bitmap bitmap, int paddingX, int paddingY) {
        return new Rect(
                Math.max(0, textRect.left - paddingX),
                Math.max(0, textRect.top - paddingY),
                Math.min(bitmap.getWidth(), textRect.right + paddingX),
                Math.min(bitmap.getHeight(), textRect.bottom + paddingY)
        );
    }

    private boolean containsSimilarRect(List<Rect> existingRects, Rect candidate) {
        for (Rect existing : existingRects) {
            float overlap = rectIntersectionRatio(existing, candidate);
            float iou = rectIntersectionOverUnion(existing, candidate);
            if (overlap > 0.82f || iou > 0.62f) {
                return true;
            }
        }
        return false;
    }

    private float rectIntersectionRatio(Rect first, Rect second) {
        int left = Math.max(first.left, second.left);
        int top = Math.max(first.top, second.top);
        int right = Math.min(first.right, second.right);
        int bottom = Math.min(first.bottom, second.bottom);
        if (right <= left || bottom <= top) {
            return 0f;
        }
        int overlapArea = (right - left) * (bottom - top);
        int minArea = Math.max(1, Math.min(first.width() * first.height(), second.width() * second.height()));
        return overlapArea / (float) minArea;
    }

    private float rectIntersectionOverUnion(Rect first, Rect second) {
        int left = Math.max(first.left, second.left);
        int top = Math.max(first.top, second.top);
        int right = Math.min(first.right, second.right);
        int bottom = Math.min(first.bottom, second.bottom);
        if (right <= left || bottom <= top) {
            return 0f;
        }
        int overlapArea = (right - left) * (bottom - top);
        int firstArea = Math.max(1, first.width() * first.height());
        int secondArea = Math.max(1, second.width() * second.height());
        int unionArea = Math.max(1, firstArea + secondArea - overlapArea);
        return overlapArea / (float) unionArea;
    }

    private List<RecognizedUiElement> detectVisualContainers(
            Bitmap bitmap,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        int columns = 16;
        int rows = Math.max(18, Math.round(columns * bitmap.getHeight() / (float) bitmap.getWidth()));
        int cellWidth = Math.max(1, bitmap.getWidth() / columns);
        int cellHeight = Math.max(1, bitmap.getHeight() / rows);
        boolean[][] occupied = new boolean[rows][columns];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                Rect cell = new Rect(
                        col * cellWidth,
                        row * cellHeight,
                        Math.min(bitmap.getWidth(), (col + 1) * cellWidth),
                        Math.min(bitmap.getHeight(), (row + 1) * cellHeight)
                );
                if (cell.width() < 8 || cell.height() < 8) {
                    continue;
                }

                RectMetrics metrics = inspector.measure(cell);
                float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
                boolean stableFill = metrics.fillVariance <= 4200f;
                boolean highlighted = dominantContrast >= 6.5f;
                if (stableFill && highlighted) {
                    occupied[row][col] = true;
                }
            }
        }

        boolean[][] visited = new boolean[rows][columns];
        List<RecognizedUiElement> result = new ArrayList<>();
        int[][] directions = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (!occupied[row][col] || visited[row][col]) {
                    continue;
                }

                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{row, col});
                visited[row][col] = true;

                int minRow = row;
                int maxRow = row;
                int minCol = col;
                int maxCol = col;
                int cellCount = 0;

                while (!queue.isEmpty()) {
                    int[] current = queue.removeFirst();
                    int currentRow = current[0];
                    int currentCol = current[1];
                    cellCount++;
                    minRow = Math.min(minRow, currentRow);
                    maxRow = Math.max(maxRow, currentRow);
                    minCol = Math.min(minCol, currentCol);
                    maxCol = Math.max(maxCol, currentCol);

                    for (int[] direction : directions) {
                        int nextRow = currentRow + direction[0];
                        int nextCol = currentCol + direction[1];
                        if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= columns) {
                            continue;
                        }
                        if (!occupied[nextRow][nextCol] || visited[nextRow][nextCol]) {
                            continue;
                        }
                        visited[nextRow][nextCol] = true;
                        queue.add(new int[]{nextRow, nextCol});
                    }
                }

                if (cellCount < 2) {
                    continue;
                }

                Rect rect = new Rect(
                        minCol * cellWidth,
                        minRow * cellHeight,
                        Math.min(bitmap.getWidth(), (maxCol + 1) * cellWidth),
                        Math.min(bitmap.getHeight(), (maxRow + 1) * cellHeight)
                );
                if (rect.width() < bitmap.getWidth() / 8 || rect.height() < bitmap.getHeight() / 30) {
                    continue;
                }
                if (rect.width() > bitmap.getWidth() * 0.95f && rect.height() > bitmap.getHeight() * 0.65f) {
                    continue;
                }

                RectMetrics metrics = inspector.measure(rect);
                float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
                if (dominantContrast < 7f || metrics.fillVariance > 7200f) {
                    continue;
                }

                RecognizedUiElement element = createCandidate(
                        rect,
                        bitmap,
                        inspector,
                        metrics,
                        textBlocks,
                        minConfidence,
                        ProposalSource.VISUAL_CONTAINER
                );
                if (element != null) {
                    result.add(element);
                }
            }
        }

        return result;
    }

    private List<RecognizedUiElement> detectImageLikeRegions(
            Bitmap bitmap,
            BitmapInspector inspector,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence
    ) {
        int columns = 12;
        int rows = Math.max(16, Math.round(columns * bitmap.getHeight() / (float) bitmap.getWidth()));
        int cellWidth = Math.max(1, bitmap.getWidth() / columns);
        int cellHeight = Math.max(1, bitmap.getHeight() / rows);
        boolean[][] occupied = new boolean[rows][columns];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                Rect cell = new Rect(
                        col * cellWidth,
                        row * cellHeight,
                        Math.min(bitmap.getWidth(), (col + 1) * cellWidth),
                        Math.min(bitmap.getHeight(), (row + 1) * cellHeight)
                );
                if (cell.width() < 1 || cell.height() < 1 || intersectsText(cell, textBlocks)) {
                    continue;
                }

                RectMetrics metrics = inspector.measure(cell);
                if (metrics.fillVariance > 3200f && Math.max(metrics.getBorderContrast(), metrics.getOuterContrast()) > 7f) {
                    occupied[row][col] = true;
                }
            }
        }
        boolean[][] visited = new boolean[rows][columns];
        List<RecognizedUiElement> images = new ArrayList<>();
        int[][] directions = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (!occupied[row][col] || visited[row][col]) {
                    continue;
                }

                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{row, col});
                visited[row][col] = true;

                int minRow = row;
                int maxRow = row;
                int minCol = col;
                int maxCol = col;
                int cellCount = 0;

                while (!queue.isEmpty()) {
                    int[] current = queue.removeFirst();
                    int currentRow = current[0];
                    int currentCol = current[1];
                    cellCount++;
                    minRow = Math.min(minRow, currentRow);
                    maxRow = Math.max(maxRow, currentRow);
                    minCol = Math.min(minCol, currentCol);
                    maxCol = Math.max(maxCol, currentCol);

                    for (int[] direction : directions) {
                        int nextRow = currentRow + direction[0];
                        int nextCol = currentCol + direction[1];
                        if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= columns) {
                            continue;
                        }
                        if (!occupied[nextRow][nextCol] || visited[nextRow][nextCol]) {
                            continue;
                        }
                        visited[nextRow][nextCol] = true;
                        queue.add(new int[]{nextRow, nextCol});
                    }
                }

                if (cellCount < 3) {
                    continue;
                }

                Rect rect = new Rect(
                        minCol * cellWidth,
                        minRow * cellHeight,
                        Math.min(bitmap.getWidth(), (maxCol + 1) * cellWidth),
                        Math.min(bitmap.getHeight(), (maxRow + 1) * cellHeight)
                );

                if (rect.width() < bitmap.getWidth() / 6 || rect.height() < bitmap.getHeight() / 10) {
                    continue;
                }

                float aspect = rect.width() / (float) Math.max(1, rect.height());
                if (aspect < 0.6f || aspect > 3.8f) {
                    continue;
                }

                RectMetrics metrics = inspector.measure(rect);
                RecognizedUiElement element = createCandidate(
                        rect,
                        bitmap,
                        inspector,
                        metrics,
                        textBlocks,
                        minConfidence,
                        ProposalSource.IMAGE_REGION
                );
                if (element != null) {
                    images.add(element);
                }
            }
        }

        return images;
    }

    private RecognizedUiElement createCandidate(
            Rect rect,
            Bitmap bitmap,
            BitmapInspector inspector,
            RectMetrics metrics,
            List<RecognizedTextBlock> textBlocks,
            float minConfidence,
            ProposalSource source
    ) {
        int textCount = countTextInside(rect, textBlocks);
        float textCountScore = clamp(textCount / 4f, 0f, 1f);
        float textWidthRatio = totalTextWidthRatio(rect, textBlocks);
        float textCoverageRatio = textCoverageRatio(rect, textBlocks);
        float centerScore = textCenterScore(rect, textBlocks);
        float leftAlignedScore = textLeftAlignedScore(rect, textBlocks);
        float cardLayoutScore = cardTextLayoutScore(rect, textBlocks);
        String primaryText = normalizeText(buildPrimaryText(rect, textBlocks));

        float aspect = rect.width() / (float) Math.max(1, rect.height());
        float areaRatio = rect.width() * rect.height() / (float) Math.max(1, bitmap.getWidth() * bitmap.getHeight());
        float dominantContrast = Math.max(metrics.getBorderContrast(), metrics.getOuterContrast());
        float contrastScore = clamp((dominantContrast - 4f) / 14f, 0f, 1f);
        float edgeDensity = inspector.edgeDensity(rect);
        float lowVarianceScore = 1f - clamp(metrics.fillVariance / 9000f, 0f, 1f);
        float highVarianceScore = clamp(metrics.fillVariance / 9000f, 0f, 1f);
        float wideShapeScore = clamp((aspect - 1f) / 3f, 0f, 1f);
        float extraWideScore = clamp((aspect - 2.6f) / 3f, 0f, 1f);
        float largeAreaScore = clamp((areaRatio - 0.04f) / 0.20f, 0f, 1f);
        float mediumAreaScore = 1f - clamp(Math.abs(areaRatio - 0.06f) / 0.06f, 0f, 1f);
        float noTextScore = textCount == 0 ? 1f : 0f;
        float buttonKeywordScore = keywordScore(primaryText, BUTTON_KEYWORDS);
        float inputKeywordScore = keywordScore(primaryText, INPUT_KEYWORDS);
        float squareShapeScore = 1f - clamp(Math.abs(aspect - 1f) / 1.1f, 0f, 1f);

        float[] featureVector = new float[]{
                textCountScore,
                textCoverageRatio,
                textWidthRatio,
                centerScore,
                leftAlignedScore,
                buttonKeywordScore,
                inputKeywordScore,
                contrastScore,
                edgeDensity,
                lowVarianceScore,
                highVarianceScore,
                wideShapeScore,
                extraWideScore,
                largeAreaScore,
                mediumAreaScore,
                noTextScore,
                source == ProposalSource.IMAGE_REGION ? 1f : 0f,
                source == ProposalSource.VISUAL_CONTAINER ? 1f : 0f,
                source == ProposalSource.TEXT_ANCHOR ? 1f : 0f,
                cardLayoutScore,
                squareShapeScore,
        };

        if (classifier.isReady()) {
            float[] scores = classifier.predict(featureVector);
            if (scores != null) {
                return createModelCandidate(rect, scores, featureVector, minConfidence);
            }
        }

        return createHeuristicCandidate(
                rect,
                textCount,
                textCoverageRatio,
                textWidthRatio,
                centerScore,
                leftAlignedScore,
                cardLayoutScore,
                buttonKeywordScore,
                inputKeywordScore,
                contrastScore,
                lowVarianceScore,
                wideShapeScore,
                extraWideScore,
                largeAreaScore,
                mediumAreaScore,
                noTextScore,
                minConfidence
        );
    }

    private RecognizedUiElement createModelCandidate(
            Rect rect,
            float[] scores,
            float[] featureVector,
            float minConfidence
    ) {
        if (scores.length < MnnUiClassifier.OUTPUT_DIM) {
            return null;
        }

        float backgroundScore = clamp(scores[CLASS_BACKGROUND], 0f, 1f);
        float buttonScore = clamp(
                scores[CLASS_BUTTON]
                        + featureVector[FEATURE_BUTTON_KEYWORD] * 0.06f
                        + featureVector[FEATURE_TEXT_SOURCE] * 0.03f,
                0f,
                1f
        );
        float inputScore = clamp(
                scores[CLASS_INPUT]
                        + featureVector[FEATURE_INPUT_KEYWORD] * 0.06f
                        + featureVector[FEATURE_TEXT_SOURCE] * 0.02f,
                0f,
                1f
        );
        float cardScore = clamp(
                scores[CLASS_CARD]
                        + featureVector[FEATURE_CARD_LAYOUT] * 0.05f
                        + featureVector[FEATURE_VISUAL_SOURCE] * 0.03f,
                0f,
                1f
        );
        float imageScore = clamp(
                scores[CLASS_IMAGE]
                        + featureVector[FEATURE_IMAGE_SOURCE] * 0.05f
                        + featureVector[FEATURE_NO_TEXT] * 0.04f
                        + featureVector[FEATURE_SQUARE_SHAPE] * 0.02f,
                0f,
                1f
        );

        UiElementType type = UiElementType.BUTTON;
        float confidence = buttonScore;
        String label = "button";
        if (inputScore > confidence) {
            type = UiElementType.INPUT;
            confidence = inputScore;
            label = "input";
        }
        if (cardScore > confidence) {
            type = UiElementType.CARD;
            confidence = cardScore;
            label = "card";
        }
        if (imageScore > confidence) {
            type = UiElementType.IMAGE;
            confidence = imageScore;
            label = "image";
        }

        if (confidence < minConfidence) {
            return null;
        }
        if (backgroundScore > confidence && confidence < Math.min(0.78f, minConfidence + 0.18f)) {
            return null;
        }

        float calibratedConfidence = clamp(confidence - backgroundScore * 0.08f + 0.02f, 0f, 0.99f);
        if (calibratedConfidence < minConfidence) {
            return null;
        }
        return new RecognizedUiElement(type, toBoundingBox(rect), calibratedConfidence, label);
    }

    private RecognizedUiElement createHeuristicCandidate(
            Rect rect,
            int textCount,
            float textCoverageRatio,
            float textWidthRatio,
            float centerScore,
            float leftAlignedScore,
            float cardLayoutScore,
            float buttonKeywordScore,
            float inputKeywordScore,
            float contrastScore,
            float lowVarianceScore,
            float wideShapeScore,
            float extraWideScore,
            float largeAreaScore,
            float mediumAreaScore,
            float noTextScore,
            float minConfidence
    ) {        float buttonScore = sigmoid(
                -1.25f
                        + textCoverageRatio * 0.55f
                        + textWidthRatio * 0.90f
                        + centerScore * 1.00f
                        + buttonKeywordScore * 1.25f
                        + contrastScore * 0.70f
                        + lowVarianceScore * 0.45f
                        + wideShapeScore * 0.95f
                        + mediumAreaScore * 0.50f
                        - noTextScore * 0.75f
                        - cardLayoutScore * 0.55f
        );
        float inputScore = sigmoid(
                -1.35f
                        + leftAlignedScore * 1.10f
                        + inputKeywordScore * 1.30f
                        + contrastScore * 0.55f
                        + lowVarianceScore * 0.85f
                        + wideShapeScore * 1.05f
                        + extraWideScore * 0.60f
                        + mediumAreaScore * 0.35f
                        + noTextScore * 0.20f
                        - centerScore * 0.40f
                        - clamp((textCount - 1) / 3f, 0f, 1f) * 0.30f
        );
        float cardScore = sigmoid(
                -1.40f
                        + clamp(textCount / 4f, 0f, 1f) * 0.40f
                        + textCoverageRatio * 0.25f
                        + contrastScore * 0.65f
                        + largeAreaScore * 1.10f
                        + mediumAreaScore * 0.45f
                        + cardLayoutScore * 1.15f
                        + leftAlignedScore * 0.20f
        );

        buttonScore = clamp(buttonScore + buttonKeywordScore * 0.08f + centerScore * 0.04f, 0f, 0.97f);
        inputScore = clamp(inputScore + inputKeywordScore * 0.10f + leftAlignedScore * 0.05f, 0f, 0.96f);
        cardScore = clamp(cardScore + cardLayoutScore * 0.08f + largeAreaScore * 0.05f, 0f, 0.95f);

        UiElementType type = UiElementType.BUTTON;
        float confidence = buttonScore;
        if (inputScore > confidence) {
            type = UiElementType.INPUT;
            confidence = inputScore;
        }
        if (cardScore > confidence) {
            type = UiElementType.CARD;
            confidence = cardScore;
        }

        if (confidence < minConfidence) {
            return null;
        }
        return new RecognizedUiElement(type, toBoundingBox(rect), confidence, type.name().toLowerCase(Locale.US));
    }

    private float textCoverageRatio(Rect rect, List<RecognizedTextBlock> textBlocks) {
        int overlapArea = 0;
        int rectArea = Math.max(1, rect.width() * rect.height());
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            int left = Math.max(rect.left, textRect.left);
            int top = Math.max(rect.top, textRect.top);
            int right = Math.min(rect.right, textRect.right);
            int bottom = Math.min(rect.bottom, textRect.bottom);
            if (right <= left || bottom <= top) {
                continue;
            }
            overlapArea += (right - left) * (bottom - top);
        }
        return overlapArea / (float) rectArea;
    }

    private float textCenterScore(Rect rect, List<RecognizedTextBlock> textBlocks) {
        int count = 0;
        float centerX = rect.exactCenterX();
        float centerY = rect.exactCenterY();
        float halfWidth = Math.max(1f, rect.width() / 2f);
        float halfHeight = Math.max(1f, rect.height() / 2f);
        float sum = 0f;
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            if (!Rect.intersects(rect, textRect)) {
                continue;
            }
            float dx = Math.abs(textRect.exactCenterX() - centerX) / halfWidth;
            float dy = Math.abs(textRect.exactCenterY() - centerY) / halfHeight;
            sum += 1f - clamp((dx + dy) / 2f, 0f, 1f);
            count++;
        }
        return count == 0 ? 0f : clamp(sum / count, 0f, 1f);
    }

    private float textLeftAlignedScore(Rect rect, List<RecognizedTextBlock> textBlocks) {
        int count = 0;
        float marginSum = 0f;
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            if (!Rect.intersects(rect, textRect)) {
                continue;
            }
            marginSum += clamp((textRect.left - rect.left) / (float) Math.max(1, rect.width()), 0f, 1f);
            count++;
        }
        if (count == 0) {
            return 0f;
        }
        float meanMargin = marginSum / count;
        return 1f - clamp(Math.abs(meanMargin - 0.18f) / 0.18f, 0f, 1f);
    }

    private float cardTextLayoutScore(Rect rect, List<RecognizedTextBlock> textBlocks) {
        int count = 0;
        float minCenterY = Float.MAX_VALUE;
        float maxCenterY = Float.MIN_VALUE;
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            if (!Rect.intersects(rect, textRect)) {
                continue;
            }
            minCenterY = Math.min(minCenterY, textRect.exactCenterY());
            maxCenterY = Math.max(maxCenterY, textRect.exactCenterY());
            count++;
        }
        if (count < 2) {
            return 0f;
        }
        float spanScore = clamp((maxCenterY - minCenterY) / Math.max(1f, rect.height()), 0f, 1f);
        float countScore = clamp(count / 4f, 0f, 1f);
        return clamp(spanScore * 0.6f + countScore * 0.4f, 0f, 1f);
    }

    private String buildPrimaryText(Rect rect, List<RecognizedTextBlock> textBlocks) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            if (!Rect.intersects(rect, textRect)) {
                continue;
            }
            String text = textBlock.getText() == null ? "" : textBlock.getText().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(text);
            count++;
            if (count >= 3 || builder.length() >= 40) {
                break;
            }
        }
        return builder.toString();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.US).replace('\n', ' ').replace('\r', ' ').trim();
    }

    private float keywordScore(String text, String[] keywords) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        float score = 0f;
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.US))) {
                score = Math.max(score, keyword.length() <= 2 ? 0.45f : 0.90f);
            }
        }
        return score;
    }

    private List<RecognizedUiElement> mergeCandidates(List<RecognizedUiElement> candidates) {
        candidates.sort(
                Comparator.comparingDouble(RecognizedUiElement::getConfidence).reversed()
                        .thenComparingInt(candidate -> candidate.getBoundingBox().getWidth() * candidate.getBoundingBox().getHeight())
        );
        List<RecognizedUiElement> result = new ArrayList<>();
        for (RecognizedUiElement candidate : candidates) {
            boolean overlaps = false;
            boolean candidateCompact = isCompactElement(candidate);
            for (RecognizedUiElement existing : result) {
                float overlap = intersectionRatio(existing.getBoundingBox(), candidate.getBoundingBox());
                float areaRatio = areaRatio(existing.getBoundingBox(), candidate.getBoundingBox());
                boolean existingCompact = isCompactElement(existing);
                boolean sameType = existing.getType() == candidate.getType();
                if ((sameType && overlap > 0.68f)
                        || (overlap > 0.82f && areaRatio < 4.5f)
                        || (!existingCompact && !candidateCompact && overlap > 0.55f && areaRatio < 4f)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                result.add(candidate);
            }
        }
        return result;
    }

    private List<RecognizedUiElement> dedupeRawCandidates(List<RecognizedUiElement> candidates) {
        candidates.sort(
                Comparator.comparingInt((RecognizedUiElement candidate) -> candidate.getBoundingBox().getTop())
                        .thenComparingInt(candidate -> candidate.getBoundingBox().getLeft())
                        .thenComparing(RecognizedUiElement::getType)
                        .thenComparing(RecognizedUiElement::getLabel)
                        .thenComparing(Comparator.comparingDouble(RecognizedUiElement::getConfidence).reversed())
        );
        List<RecognizedUiElement> result = new ArrayList<>();
        for (RecognizedUiElement candidate : candidates) {
            boolean duplicate = false;
            for (RecognizedUiElement existing : result) {
                if (existing.getType() != candidate.getType()) {
                    continue;
                }
                float overlap = intersectionRatio(existing.getBoundingBox(), candidate.getBoundingBox());
                float iou = intersectionOverUnion(existing.getBoundingBox(), candidate.getBoundingBox());
                float areaRatio = areaRatio(existing.getBoundingBox(), candidate.getBoundingBox());
                boolean sameLabel = normalizeText(existing.getLabel()).equals(normalizeText(candidate.getLabel()));
                if ((sameLabel && overlap > 0.92f && areaRatio < 1.6f)
                        || (iou > 0.88f && areaRatio < 1.35f)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(candidate);
            }
        }
        return result;
    }

    private boolean isCompactElement(RecognizedUiElement element) {
        UiElementType type = element.getType();
        return type == UiElementType.ICON_BUTTON
                || type == UiElementType.CHECKBOX
                || type == UiElementType.RADIO
                || type == UiElementType.SWITCH
                || type == UiElementType.TEXT_LINK;
    }

    private boolean intersectsText(Rect rect, List<RecognizedTextBlock> textBlocks) {
        for (RecognizedTextBlock textBlock : textBlocks) {
            if (Rect.intersects(rect, toRect(textBlock.getBoundingBox()))) {
                return true;
            }
        }
        return false;
    }

    private int countTextInside(Rect rect, List<RecognizedTextBlock> textBlocks) {
        int count = 0;
        for (RecognizedTextBlock textBlock : textBlocks) {
            if (Rect.intersects(rect, toRect(textBlock.getBoundingBox()))) {
                count++;
            }
        }
        return count;
    }

    private float totalTextWidthRatio(Rect rect, List<RecognizedTextBlock> textBlocks) {
        int totalWidth = 0;
        for (RecognizedTextBlock textBlock : textBlocks) {
            Rect textRect = toRect(textBlock.getBoundingBox());
            if (!Rect.intersects(rect, textRect)) {
                continue;
            }
            totalWidth += Math.min(rect.right, textRect.right) - Math.max(rect.left, textRect.left);
        }
        return totalWidth / (float) Math.max(1, rect.width());
    }

    private float intersectionRatio(BoundingBox first, BoundingBox second) {
        int left = Math.max(first.getLeft(), second.getLeft());
        int top = Math.max(first.getTop(), second.getTop());
        int right = Math.min(first.getRight(), second.getRight());
        int bottom = Math.min(first.getBottom(), second.getBottom());
        if (right <= left || bottom <= top) {
            return 0f;
        }
        int overlapArea = (right - left) * (bottom - top);
        int minArea = Math.max(1, Math.min(first.getWidth() * first.getHeight(), second.getWidth() * second.getHeight()));
        return overlapArea / (float) minArea;
    }

    private float areaRatio(BoundingBox first, BoundingBox second) {
        int area1 = Math.max(1, first.getWidth() * first.getHeight());
        int area2 = Math.max(1, second.getWidth() * second.getHeight());
        return Math.max(area1, area2) / (float) Math.min(area1, area2);
    }

    private float intersectionOverUnion(BoundingBox first, BoundingBox second) {
        int left = Math.max(first.getLeft(), second.getLeft());
        int top = Math.max(first.getTop(), second.getTop());
        int right = Math.min(first.getRight(), second.getRight());
        int bottom = Math.min(first.getBottom(), second.getBottom());
        if (right <= left || bottom <= top) {
            return 0f;
        }
        int overlapArea = (right - left) * (bottom - top);
        int unionArea = Math.max(
                1,
                first.getWidth() * first.getHeight() + second.getWidth() * second.getHeight() - overlapArea
        );
        return overlapArea / (float) unionArea;
    }

    private float sigmoid(float value) {
        return (float) (1d / (1d + Math.exp(-value)));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private Rect normalize(Rect rect) {
        int normalizedLeft = Math.min(rect.left, rect.right - 1);
        int normalizedTop = Math.min(rect.top, rect.bottom - 1);
        int normalizedRight = Math.max(normalizedLeft + 1, rect.right);
        int normalizedBottom = Math.max(normalizedTop + 1, rect.bottom);
        return new Rect(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom);
    }

    private Rect toRect(BoundingBox boundingBox) {
        return new Rect(
                boundingBox.getLeft(),
                boundingBox.getTop(),
                boundingBox.getRight(),
                boundingBox.getBottom()
        );
    }

    private BoundingBox toBoundingBox(Rect rect) {
        return new BoundingBox(rect.left, rect.top, rect.right, rect.bottom);
    }
    private enum ProposalSource {
        TEXT_ANCHOR,
        VISUAL_CONTAINER,
        IMAGE_REGION
    }

    private static final class RectMetrics {
        private final float fillMean;
        private final float borderMean;
        private final float outerMean;
        private final float fillVariance;

        private RectMetrics(float fillMean, float borderMean, float outerMean, float fillVariance) {
            this.fillMean = fillMean;
            this.borderMean = borderMean;
            this.outerMean = outerMean;
            this.fillVariance = fillVariance;
        }

        private float getBorderContrast() {
            return Math.abs(borderMean - fillMean);
        }

        private float getOuterContrast() {
            return Math.abs(outerMean - fillMean);
        }
    }

    private final class BitmapInspector {
        private final int width;
        private final int height;
        private final float[] lumas;
        private final Map<Long, RectMetrics> measureCache = new HashMap<>();
        private final Map<Long, Float> edgeDensityCache = new HashMap<>();
        private final Map<Long, Float> cornerEdgeDensityCache = new HashMap<>();

        private BitmapInspector(Bitmap bitmap) {
            width = bitmap.getWidth();
            height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            lumas = new float[pixels.length];
            for (int index = 0; index < pixels.length; index++) {
                int color = pixels[index];
                int red = (color >> 16) & 0xFF;
                int green = (color >> 8) & 0xFF;
                int blue = color & 0xFF;
                lumas[index] = red * 0.299f + green * 0.587f + blue * 0.114f;
            }
        }

        private RectMetrics measure(Rect rect) {
            Rect safeRect = normalize(new Rect(
                    clampInt(rect.left, 0, width - 1),
                    clampInt(rect.top, 0, height - 1),
                    clampInt(rect.right, 1, width),
                    clampInt(rect.bottom, 1, height)
            ));
            long key = rectKey(safeRect);
            RectMetrics cached = measureCache.get(key);
            if (cached != null) {
                return cached;
            }
            int borderSize = Math.max(2, Math.min(safeRect.width(), safeRect.height()) / 12);
            Rect fillRect = normalize(new Rect(
                    safeRect.left + borderSize,
                    safeRect.top + borderSize,
                    safeRect.right - borderSize,
                    safeRect.bottom - borderSize
            ));

            float[] fillStats = sampleStats(fillRect);
            float[] topStats = sampleStats(new Rect(safeRect.left, safeRect.top, safeRect.right, Math.min(safeRect.bottom, safeRect.top + borderSize)));
            float[] bottomStats = sampleStats(new Rect(safeRect.left, Math.max(safeRect.top, safeRect.bottom - borderSize), safeRect.right, safeRect.bottom));
            float[] leftStats = sampleStats(new Rect(safeRect.left, safeRect.top, Math.min(safeRect.right, safeRect.left + borderSize), safeRect.bottom));
            float[] rightStats = sampleStats(new Rect(Math.max(safeRect.left, safeRect.right - borderSize), safeRect.top, safeRect.right, safeRect.bottom));
            float borderMean = averageOf(topStats[0], bottomStats[0], leftStats[0], rightStats[0]);

            int outerSize = Math.max(4, Math.min(safeRect.width(), safeRect.height()) / 6);
            Rect expanded = new Rect(
                    clampInt(safeRect.left - outerSize, 0, width),
                    clampInt(safeRect.top - outerSize, 0, height),
                    clampInt(safeRect.right + outerSize, 0, width),
                    clampInt(safeRect.bottom + outerSize, 0, height)
            );
            float[] outerTopStats = sampleStats(new Rect(expanded.left, expanded.top, expanded.right, safeRect.top));
            float[] outerBottomStats = sampleStats(new Rect(expanded.left, safeRect.bottom, expanded.right, expanded.bottom));
            float[] outerLeftStats = sampleStats(new Rect(expanded.left, safeRect.top, safeRect.left, safeRect.bottom));
            float[] outerRightStats = sampleStats(new Rect(safeRect.right, safeRect.top, expanded.right, safeRect.bottom));
            float outerMean = weightedAverage(
                    outerTopStats[0], (int) outerTopStats[2],
                    outerBottomStats[0], (int) outerBottomStats[2],
                    outerLeftStats[0], (int) outerLeftStats[2],
                    outerRightStats[0], (int) outerRightStats[2],
                    borderMean
            );

            RectMetrics metrics = new RectMetrics(fillStats[0], borderMean, outerMean, fillStats[1]);
            measureCache.put(key, metrics);
            return metrics;
        }

        private float edgeDensity(Rect rect) {
            Rect safe = normalize(new Rect(
                    clampInt(rect.left, 0, width - 1),
                    clampInt(rect.top, 0, height - 1),
                    clampInt(rect.right, 1, width),
                    clampInt(rect.bottom, 1, height)
            ));
            long key = rectKey(safe);
            Float cached = edgeDensityCache.get(key);
            if (cached != null) {
                return cached;
            }
            int stepX = Math.max(1, safe.width() / 24);
            int stepY = Math.max(1, safe.height() / 24);
            int transitions = 0;
            int comparisons = 0;

            for (int y = safe.top; y < safe.bottom; y += stepY) {
                for (int x = safe.left; x < safe.right; x += stepX) {
                    float current = lumaAt(x, y);
                    int nextX = Math.min(width - 1, x + stepX);
                    int nextY = Math.min(height - 1, y + stepY);
                    if (nextX != x) {
                        if (Math.abs(current - lumaAt(nextX, y)) > 18f) {
                            transitions++;
                        }
                        comparisons++;
                    }
                    if (nextY != y) {
                        if (Math.abs(current - lumaAt(x, nextY)) > 18f) {
                            transitions++;
                        }
                        comparisons++;
                    }
                }
            }

            float density = comparisons == 0 ? 0f : clamp(transitions / (float) comparisons, 0f, 1f);
            edgeDensityCache.put(key, density);
            return density;
        }

        private float cornerEdgeDensity(Rect rect) {
            Rect safe = normalize(new Rect(
                    clampInt(rect.left, 0, width - 1),
                    clampInt(rect.top, 0, height - 1),
                    clampInt(rect.right, 1, width),
                    clampInt(rect.bottom, 1, height)
            ));
            long key = rectKey(safe);
            Float cached = cornerEdgeDensityCache.get(key);
            if (cached != null) {
                return cached;
            }
            int sampleWidth = Math.max(2, safe.width() / 3);
            int sampleHeight = Math.max(2, safe.height() / 3);
            float density = averageOf(
                    edgeDensity(new Rect(safe.left, safe.top, Math.min(safe.right, safe.left + sampleWidth), Math.min(safe.bottom, safe.top + sampleHeight))),
                    edgeDensity(new Rect(Math.max(safe.left, safe.right - sampleWidth), safe.top, safe.right, Math.min(safe.bottom, safe.top + sampleHeight))),
                    edgeDensity(new Rect(safe.left, Math.max(safe.top, safe.bottom - sampleHeight), Math.min(safe.right, safe.left + sampleWidth), safe.bottom)),
                    edgeDensity(new Rect(Math.max(safe.left, safe.right - sampleWidth), Math.max(safe.top, safe.bottom - sampleHeight), safe.right, safe.bottom))
            );
            cornerEdgeDensityCache.put(key, density);
            return density;
        }

        private float[] sampleStats(Rect rect) {
            if (rect.width() <= 0 || rect.height() <= 0) {
                return new float[]{0f, 0f, 0f};
            }

            Rect safe = normalize(rect);
            int stepX = Math.max(1, safe.width() / 24);
            int stepY = Math.max(1, safe.height() / 24);
            int count = 0;
            float sum = 0f;
            float sumSquared = 0f;

            for (int y = safe.top; y < safe.bottom; y += stepY) {
                for (int x = safe.left; x < safe.right; x += stepX) {
                    float luma = lumaAt(x, y);
                    sum += luma;
                    sumSquared += luma * luma;
                    count++;
                }
            }

            if (count == 0) {
                return new float[]{0f, 0f, 0f};
            }

            float mean = sum / count;
            float variance = Math.max(0f, sumSquared / count - mean * mean);
            return new float[]{mean, variance, count};
        }

        private float averageOf(float... values) {
            float sum = 0f;
            int count = 0;
            for (float value : values) {
                if (value > 0f) {
                    sum += value;
                    count++;
                }
            }
            return count == 0 ? 0f : sum / count;
        }

        private float weightedAverage(
                float mean1, int count1,
                float mean2, int count2,
                float mean3, int count3,
                float mean4, int count4,
                float fallback
        ) {
            float weightedSum = mean1 * count1 + mean2 * count2 + mean3 * count3 + mean4 * count4;
            int totalCount = count1 + count2 + count3 + count4;
            return totalCount == 0 ? fallback : weightedSum / totalCount;
        }

        private float lumaAt(int x, int y) {
            return lumas[y * width + x];
        }

        private long rectKey(Rect rect) {
            long left = rect.left & 0xFFFFL;
            long top = rect.top & 0xFFFFL;
            long right = rect.right & 0xFFFFL;
            long bottom = rect.bottom & 0xFFFFL;
            return (left << 48) | (top << 32) | (right << 16) | bottom;
        }

        private int clampInt(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}

