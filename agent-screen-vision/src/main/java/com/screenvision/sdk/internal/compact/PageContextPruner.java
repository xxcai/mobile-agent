package com.screenvision.sdk.internal.compact;

import com.screenvision.sdk.CompactAnalysisOptions;
import com.screenvision.sdk.TaskContext;
import com.screenvision.sdk.model.BoundingBox;
import com.screenvision.sdk.model.CompactDebugInfo;
import com.screenvision.sdk.model.CompactDebugRow;
import com.screenvision.sdk.model.CompactDebugSectionSource;
import com.screenvision.sdk.model.CompactDropCount;
import com.screenvision.sdk.model.CompactDropSummary;
import com.screenvision.sdk.model.CompactElementRole;
import com.screenvision.sdk.model.CompactListItem;
import com.screenvision.sdk.model.CompactPageAnalysisResult;
import com.screenvision.sdk.model.CompactSection;
import com.screenvision.sdk.model.CompactSectionType;
import com.screenvision.sdk.model.CompactTextBlock;
import com.screenvision.sdk.model.CompactUiElement;
import com.screenvision.sdk.model.PageAnalysisResult;
import com.screenvision.sdk.model.PageSize;
import com.screenvision.sdk.model.RecognizedTextBlock;
import com.screenvision.sdk.model.RecognizedUiElement;
import com.screenvision.sdk.model.UiElementType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PageContextPruner {
    private static final Set<UiElementType> INTERACTIVE_TYPES = new HashSet<>(Arrays.asList(
            UiElementType.BUTTON,
            UiElementType.INPUT,
            UiElementType.ICON_BUTTON,
            UiElementType.TAB,
            UiElementType.CHECKBOX,
            UiElementType.RADIO,
            UiElementType.SWITCH,
            UiElementType.TEXT_LINK
    ));
    private static final Set<UiElementType> PAIR_LABEL_TYPES = new HashSet<>(Arrays.asList(
            UiElementType.INPUT,
            UiElementType.CHECKBOX,
            UiElementType.RADIO,
            UiElementType.SWITCH,
            UiElementType.ICON_BUTTON
    ));
    private static final Set<String> GENERIC_LABELS = new HashSet<>(Arrays.asList(
            "button", "input", "image", "card", "icon_button", "tab", "checkbox", "radio", "switch", "avatar", "text_link", "row", "unknown"
    ));

    private static final Comparator<WorkingText> READING_ORDER = Comparator
            .comparingInt((WorkingText text) -> text.box.getTop())
            .thenComparingInt(text -> text.box.getLeft())
            .thenComparingInt(text -> text.box.getBottom());
    private static final Comparator<WorkingControl> READING_ORDER_CONTROL = Comparator
            .comparingInt((WorkingControl control) -> control.box.getTop())
            .thenComparingInt(control -> control.box.getLeft())
            .thenComparingInt(control -> control.box.getBottom());

    public CompactPageAnalysisResult prune(PageAnalysisResult rawResult) {
        return prune(rawResult, CompactAnalysisOptions.newBuilder().build(), TaskContext.empty());
    }

    public CompactPageAnalysisResult prune(PageAnalysisResult rawResult, CompactAnalysisOptions options, TaskContext taskContext) {
        if (rawResult == null) {
            return new CompactPageAnalysisResult(
                    new PageSize(1, 1),
                    0L,
                    0L,
                    0L,
                    "",
                    "",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null
            );
        }

        long pruneStartMs = System.nanoTime();
        CompactAnalysisOptions safeOptions = options == null ? CompactAnalysisOptions.newBuilder().build() : options;
        TaskContext safeTaskContext = taskContext == null ? TaskContext.empty() : taskContext;
        PageSize pageSize = rawResult.getPageSize() == null ? new PageSize(1, 1) : rawResult.getPageSize();
        LayoutContext context = new LayoutContext(pageSize, safeOptions, safeTaskContext);

        List<WorkingText> texts = normalizeTexts(rawResult.getTextBlocks(), context);
        List<WorkingControl> controls = normalizeControls(rawResult.getUiElements(), context);
        resolveControlTextRelations(texts, controls);

        Geometry geometry = detectGeometry(texts, controls, context);
        assignSections(texts, controls, geometry, context);
        applyTaskMatch(texts, controls, context);
        scoreElements(texts, controls, context);

        LinkedHashMap<CompactSectionType, SectionAccumulator> sections = buildSectionAccumulators(texts, controls, context);
        collapseDenseSection(sections.get(CompactSectionType.DENSE_LIST), context);
        sections = buildSectionAccumulators(texts, controls, context);
        mergeOverflowSections(texts, controls, sections, context);
        sections = buildSectionAccumulators(texts, controls, context);

        List<CompactSection> initialSections = toCompactSections(sections);
        List<CompactListItem> compactItems = buildCompactListItems(sections, texts, controls, context);
        Map<CompactSectionType, Integer> sectionOrder = buildSectionOrder(initialSections);
        Map<String, Integer> itemOrder = buildItemOrder(compactItems);
        List<CompactUiElement> compactControls = selectControls(controls, sectionOrder, itemOrder, context);
        List<CompactTextBlock> compactTexts = selectTexts(texts, sectionOrder, itemOrder, context);
        List<CompactSection> compactSections = rebuildOutputSections(initialSections, compactItems, compactTexts, compactControls);
        Map<CompactSectionType, Integer> finalSectionOrder = buildSectionOrder(compactSections);
        compactControls.sort(compactControlComparator(finalSectionOrder, itemOrder));
        compactTexts.sort(compactTextComparator(finalSectionOrder, itemOrder));
        String summary = buildSummary(compactSections, compactItems, compactControls, compactTexts);
        CompactDebugInfo debugInfo = safeOptions.isEnableDebugMetadata()
                ? buildDebugInfo(compactSections, compactItems, compactTexts, compactControls, sections, texts, controls, context)
                : null;

        long postProcessElapsedMs = Math.max(0L, (System.nanoTime() - pruneStartMs) / 1_000_000L);
        long totalElapsedMs = rawResult.getElapsedMs() + postProcessElapsedMs;

        return new CompactPageAnalysisResult(
                pageSize,
                totalElapsedMs,
                rawResult.getElapsedMs(),
                postProcessElapsedMs,
                safeTaskContext.getGoalText(),
                summary,
                compactSections,
                compactItems,
                compactTexts,
                compactControls,
                debugInfo
        );
    }

    private List<WorkingText> normalizeTexts(List<RecognizedTextBlock> rawTexts, LayoutContext context) {
        List<WorkingText> result = new ArrayList<>();
        if (rawTexts == null) {
            return result;
        }
        int index = 1;
        for (RecognizedTextBlock block : rawTexts) {
            if (block == null || block.getBoundingBox() == null) {
                continue;
            }
            String text = safeTrim(block.getText());
            if (text.isEmpty()) {
                continue;
            }
            BoundingBox box = clampBox(block.getBoundingBox(), context.pageWidth, context.pageHeight);
            if (box.getWidth() <= 0 || box.getHeight() <= 0) {
                continue;
            }
            result.add(new WorkingText("t" + index++, text, normalizeText(text), box, clamp(block.getConfidence(), 0f, 1f)));
        }
        result.sort(READING_ORDER);
        return dedupeTexts(result);
    }

    private List<WorkingControl> normalizeControls(List<RecognizedUiElement> rawControls, LayoutContext context) {
        List<WorkingControl> result = new ArrayList<>();
        if (rawControls == null) {
            return result;
        }
        int index = 1;
        for (RecognizedUiElement element : rawControls) {
            if (element == null || element.getBoundingBox() == null || element.getType() == null) {
                continue;
            }
            BoundingBox box = clampBox(element.getBoundingBox(), context.pageWidth, context.pageHeight);
            if (box.getWidth() <= 0 || box.getHeight() <= 0) {
                continue;
            }
            String label = safeTrim(element.getLabel());
            result.add(new WorkingControl(
                    "c" + index++,
                    element.getType(),
                    label,
                    normalizeText(label),
                    box,
                    clamp(element.getConfidence(), 0f, 1f)
            ));
        }
        result.sort(READING_ORDER_CONTROL);
        return dedupeControls(result);
    }

    private List<WorkingText> dedupeTexts(List<WorkingText> texts) {
        List<WorkingText> deduped = new ArrayList<>();
        for (WorkingText candidate : texts) {
            boolean duplicate = false;
            for (WorkingText existing : deduped) {
                if (candidate.normalizedText.equals(existing.normalizedText)
                        && intersectionRatio(candidate.box, existing.box) > 0.82f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduped.add(candidate);
            }
        }
        return deduped;
    }

    private List<WorkingControl> dedupeControls(List<WorkingControl> controls) {
        List<WorkingControl> deduped = new ArrayList<>();
        for (WorkingControl candidate : controls) {
            boolean duplicate = false;
            for (WorkingControl existing : deduped) {
                if (existing.type == candidate.type && intersectionRatio(existing.box, candidate.box) > 0.82f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduped.add(candidate);
            }
        }
        return deduped;
    }

    private void resolveControlTextRelations(List<WorkingText> texts, List<WorkingControl> controls) {
        for (WorkingControl control : controls) {
            List<WorkingText> insideTexts = new ArrayList<>();
            List<WorkingText> nearbyTexts = new ArrayList<>();
            for (WorkingText text : texts) {
                if (isTextInsideControl(text.box, control.box)) {
                    insideTexts.add(text);
                } else if (isNearControl(text.box, control.box, control.type)) {
                    nearbyTexts.add(text);
                }
            }
            insideTexts.sort(READING_ORDER);
            nearbyTexts.sort(Comparator.comparingDouble(text -> controlDistance(control.box, text.box)));

            if (!insideTexts.isEmpty()) {
                control.hasInsideText = true;
                for (WorkingText text : insideTexts) {
                    control.insideTextIds.add(text.id);
                    if (isConsumableTextControl(control.type)) {
                        text.consumed = true;
                        text.duplicateOfControlId = control.id;
                    }
                }
            }
            if (!nearbyTexts.isEmpty()) {
                WorkingText best = nearbyTexts.get(0);
                if (PAIR_LABEL_TYPES.contains(control.type) || isGenericLabel(control.label, control.type)) {
                    control.hasPairText = true;
                    control.pairTextIds.add(best.id);
                    best.pairedControlId = control.id;
                    best.paired = true;
                    if (control.type == UiElementType.INPUT || control.type == UiElementType.SWITCH || control.type == UiElementType.CHECKBOX || control.type == UiElementType.RADIO) {
                        best.keepStrong = true;
                    }
                }
            }

            String insideLabel = joinTextsById(texts, control.insideTextIds, 24);
            String pairLabel = joinTextsById(texts, control.pairTextIds, 32);
            if (isGenericLabel(control.label, control.type)) {
                if (!insideLabel.isEmpty()) {
                    control.label = insideLabel;
                } else if (!pairLabel.isEmpty()) {
                    control.label = pairLabel;
                } else {
                    control.label = control.type.name().toLowerCase(Locale.US);
                }
                control.normalizedLabel = normalizeText(control.label);
            }
        }
    }

    private Geometry detectGeometry(List<WorkingText> texts, List<WorkingControl> controls, LayoutContext context) {
        Geometry geometry = new Geometry();
        geometry.bottomBar = detectBottomBar(controls, context);
        geometry.header = detectHeader(texts, controls, context, geometry.bottomBar);
        geometry.modal = detectModal(texts, controls, context, geometry.header, geometry.bottomBar);
        geometry.denseList = detectDenseList(texts, controls, context, geometry.header, geometry.bottomBar, geometry.modal);
        return geometry;
    }

    private BoundingBox detectBottomBar(List<WorkingControl> controls, LayoutContext context) {
        List<BoundingBox> tabCandidates = new ArrayList<>();
        List<BoundingBox> navigationCandidates = new ArrayList<>();
        int compactNavMaxHeight = Math.max(160, context.pageHeight / 10);
        for (WorkingControl control : controls) {
            float centerYRatio = centerY(control.box) / Math.max(1f, context.pageHeight);
            float widthRatio = control.box.getWidth() / (float) Math.max(1, context.pageWidth);
            boolean bottomZone = control.box.getTop() >= context.pageHeight * 0.80f;
            boolean compactNav = control.box.getHeight() <= compactNavMaxHeight && widthRatio <= 0.34f;
            if (control.type == UiElementType.TAB && centerYRatio > 0.82f && compactNav) {
                tabCandidates.add(control.box);
                continue;
            }
            if (!bottomZone || !compactNav) {
                continue;
            }
            boolean navigationLike = control.type == UiElementType.ICON_BUTTON
                    || control.type == UiElementType.TEXT_LINK
                    || control.type == UiElementType.TAB
                    || (!isGenericLabel(control.label, control.type) && control.interactive && widthRatio <= 0.26f);
            if (navigationLike) {
                navigationCandidates.add(control.box);
            }
        }
        List<BoundingBox> chosen = tabCandidates.size() >= 2 ? tabCandidates : navigationCandidates;
        if (chosen.size() < 3) {
            return null;
        }
        BoundingBox union = union(chosen, context.pageWidth, context.pageHeight, 10, 12);
        if (union == null) {
            return null;
        }
        float topRatio = union.getTop() / (float) Math.max(1, context.pageHeight);
        float heightRatio = union.getHeight() / (float) Math.max(1, context.pageHeight);
        return union.getWidth() > context.pageWidth * 0.52f
                && topRatio >= 0.78f
                && heightRatio <= 0.16f
                ? union : null;
    }

    private BoundingBox detectHeader(List<WorkingText> texts, List<WorkingControl> controls, LayoutContext context, BoundingBox bottomBar) {
        int headerLimit = Math.min(Math.round(context.pageHeight * 0.16f), 220);
        List<BoundingBox> headerBoxes = new ArrayList<>();
        for (WorkingText text : texts) {
            if (text.box.getTop() < headerLimit) {
                headerBoxes.add(text.box);
            }
        }
        for (WorkingControl control : controls) {
            if (control.box.getTop() < headerLimit && !intersects(bottomBar, control.box)) {
                headerBoxes.add(control.box);
            }
        }
        return headerBoxes.isEmpty() ? null : union(headerBoxes, context.pageWidth, context.pageHeight, 12, 12);
    }

    private BoundingBox detectModal(
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LayoutContext context,
            BoundingBox header,
            BoundingBox bottomBar
    ) {
        List<BoundingBox> centralBoxes = new ArrayList<>();
        int interactiveCount = 0;
        int repeatedRowCandidates = 0;
        int leadingAnchorCandidates = 0;
        int totalElements = texts.size() + controls.size();
        for (WorkingControl control : controls) {
            if (intersects(header, control.box) || intersects(bottomBar, control.box)) {
                continue;
            }
            float cx = centerX(control.box) / Math.max(1f, context.pageWidth);
            float cy = centerY(control.box) / Math.max(1f, context.pageHeight);
            if (cx > 0.12f && cx < 0.88f && cy > 0.18f && cy < 0.82f) {
                centralBoxes.add(control.box);
                if (control.interactive) {
                    interactiveCount++;
                }
                if (isWideDenseListAnchor(control, context)) {
                    repeatedRowCandidates++;
                }
                if (isLeadingRowAnchor(control, context)) {
                    leadingAnchorCandidates++;
                }
            }
        }
        for (WorkingText text : texts) {
            if (intersects(header, text.box) || intersects(bottomBar, text.box)) {
                continue;
            }
            float cx = centerX(text.box) / Math.max(1f, context.pageWidth);
            float cy = centerY(text.box) / Math.max(1f, context.pageHeight);
            if (cx > 0.14f && cx < 0.86f && cy > 0.18f && cy < 0.78f) {
                centralBoxes.add(text.box);
            }
        }
        if (centralBoxes.size() < 4 || interactiveCount < 2 || totalElements == 0) {
            return null;
        }
        if (centralBoxes.size() >= Math.round(totalElements * 0.85f)) {
            return null;
        }
        if (repeatedRowCandidates >= 3 || leadingAnchorCandidates >= 3) {
            return null;
        }
        BoundingBox union = union(centralBoxes, context.pageWidth, context.pageHeight, 20, 20);
        if (union == null) {
            return null;
        }
        float widthRatio = union.getWidth() / (float) Math.max(1, context.pageWidth);
        float heightRatio = union.getHeight() / (float) Math.max(1, context.pageHeight);
        if (widthRatio < 0.34f || widthRatio > 0.92f || heightRatio < 0.18f || heightRatio > 0.72f) {
            return null;
        }
        if (union.getLeft() < context.pageWidth * 0.04f || union.getRight() > context.pageWidth * 0.96f) {
            return null;
        }
        return union;
    }

    private BoundingBox detectDenseList(
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LayoutContext context,
            BoundingBox header,
            BoundingBox bottomBar,
            BoundingBox modal
    ) {
        BoundingBox wideControlDense = detectDenseListFromWideControls(controls, context, header, bottomBar, modal);
        BoundingBox avatarDense = detectDenseListFromAvatars(texts, controls, context, header, bottomBar, modal);
        BoundingBox textDense = detectDenseListFromTexts(texts, controls, context, header, bottomBar, modal);
        return chooseDenseListCandidate(wideControlDense, avatarDense, textDense);
    }

    private boolean isWideDenseListAnchor(WorkingControl control, LayoutContext context) {
        if (control == null || control.box == null) {
            return false;
        }
        float widthRatio = control.box.getWidth() / (float) Math.max(1, context.pageWidth);
        if (widthRatio <= 0.42f) {
            return false;
        }
        float centerY = centerY(control.box);
        if (centerY <= context.pageHeight * 0.18f || centerY >= context.pageHeight * 0.86f) {
            return false;
        }
        int height = control.box.getHeight();
        int rowLikeMaxHeight = Math.max(132, context.pageHeight / 10);
        int cardLikeMaxHeight = Math.max(220, context.pageHeight / 7);
        switch (control.type) {
            case CARD:
            case BUTTON:
                return height >= 40 && height <= cardLikeMaxHeight;
            case IMAGE:
                return height >= 48 && height <= Math.max(220, context.pageHeight / 6);
            case INPUT:
                return height >= 42 && height <= rowLikeMaxHeight;
            default:
                return false;
        }
    }
    private BoundingBox detectDenseListFromWideControls(
            List<WorkingControl> controls,
            LayoutContext context,
            BoundingBox header,
            BoundingBox bottomBar,
            BoundingBox modal
    ) {
        List<WorkingControl> anchors = new ArrayList<>();
        for (WorkingControl control : controls) {
            if (intersects(header, control.box) || intersects(bottomBar, control.box) || intersects(modal, control.box)) {
                continue;
            }
            if (isWideDenseListAnchor(control, context)) {
                anchors.add(control);
            }
        }
        if (anchors.size() < 3) {
            return null;
        }
        List<WorkingControl> bestGroup = new ArrayList<>();
        for (WorkingControl anchor : anchors) {
            List<WorkingControl> group = new ArrayList<>();
            for (WorkingControl other : anchors) {
                float leftDelta = Math.abs(other.box.getLeft() - anchor.box.getLeft()) / (float) Math.max(1, context.pageWidth);
                float widthDelta = Math.abs(other.box.getWidth() - anchor.box.getWidth()) / (float) Math.max(1, anchor.box.getWidth());
                if (leftDelta < 0.08f && widthDelta < 0.18f) {
                    group.add(other);
                }
            }
            if (group.size() > bestGroup.size()) {
                bestGroup = group;
            }
        }
        return bestGroup.size() < 3 ? null : union(toBoxes(bestGroup), context.pageWidth, context.pageHeight, 12, 18);
    }

    private BoundingBox detectDenseListFromAvatars(
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LayoutContext context,
            BoundingBox header,
            BoundingBox bottomBar,
            BoundingBox modal
    ) {
        List<WorkingControl> avatars = new ArrayList<>();
        for (WorkingControl control : controls) {
            if (intersects(header, control.box) || intersects(bottomBar, control.box) || intersects(modal, control.box)) {
                continue;
            }
            if (isConversationAvatar(control, context)) {
                avatars.add(control);
            }
        }
        if (avatars.size() < 3) {
            return null;
        }
        List<WorkingControl> bestGroup = new ArrayList<>();
        for (WorkingControl anchor : avatars) {
            List<WorkingControl> group = new ArrayList<>();
            for (WorkingControl other : avatars) {
                float leftDelta = Math.abs(other.box.getLeft() - anchor.box.getLeft()) / (float) Math.max(1, context.pageWidth);
                float sizeDelta = Math.abs(other.box.getWidth() - anchor.box.getWidth()) / (float) Math.max(1, anchor.box.getWidth());
                if (leftDelta < 0.06f && sizeDelta < 0.25f) {
                    group.add(other);
                }
            }
            if (group.size() > bestGroup.size()) {
                bestGroup = group;
            }
        }
        if (bestGroup.size() < 3) {
            return null;
        }
        List<BoundingBox> boxes = new ArrayList<>(toBoxes(bestGroup));
        for (WorkingText text : texts) {
            if (intersects(header, text.box) || intersects(bottomBar, text.box) || intersects(modal, text.box)) {
                continue;
            }
            if (isLikelyConversationText(text, context) && hasNearbyConversationAvatar(text, bestGroup, context)) {
                boxes.add(text.box);
            }
        }
        BoundingBox candidate = union(boxes, context.pageWidth, context.pageHeight, 40, 22);
        return candidate != null && candidate.getHeight() > context.pageHeight * 0.22f ? candidate : null;
    }

    private BoundingBox detectDenseListFromTexts(
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LayoutContext context,
            BoundingBox header,
            BoundingBox bottomBar,
            BoundingBox modal
    ) {
        List<WorkingText> candidates = new ArrayList<>();
        for (WorkingText text : texts) {
            if (intersects(header, text.box) || intersects(bottomBar, text.box) || intersects(modal, text.box)) {
                continue;
            }
            if (isLikelyConversationText(text, context)) {
                candidates.add(text);
            }
        }
        if (candidates.size() < 6) {
            return null;
        }
        List<RowGroup> rows = new ArrayList<>();
        for (WorkingText text : candidates) {
            addTextToRow(rows, text, context.pageHeight);
        }
        rows.removeIf(row -> row.texts.isEmpty() || row.toBox().getWidth() < context.pageWidth * 0.18f);
        if (rows.size() < 4) {
            return null;
        }
        List<BoundingBox> boxes = new ArrayList<>();
        for (RowGroup row : rows) {
            boxes.add(row.toBox());
        }
        for (WorkingControl control : controls) {
            if (intersects(header, control.box) || intersects(bottomBar, control.box) || intersects(modal, control.box)) {
                continue;
            }
            if (!isConversationAvatar(control, context)) {
                continue;
            }
            for (RowGroup row : rows) {
                if (overlapRatioY(control.box, row.toBox()) > 0.30f || Math.abs(centerY(control.box) - centerY(row.toBox())) < Math.max(52f, context.pageHeight / 28f)) {
                    boxes.add(control.box);
                    break;
                }
            }
        }
        BoundingBox candidate = union(boxes, context.pageWidth, context.pageHeight, 56, 20);
        return candidate != null && candidate.getHeight() > context.pageHeight * 0.22f ? candidate : null;
    }

    private BoundingBox chooseDenseListCandidate(BoundingBox first, BoundingBox second, BoundingBox third) {
        BoundingBox best = null;
        int bestScore = -1;
        BoundingBox[] candidates = new BoundingBox[]{first, second, third};
        for (BoundingBox candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            int score = candidate.getHeight() * 2 + candidate.getWidth() / 4;
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean isConversationAvatar(WorkingControl control, LayoutContext context) {
        if (control.type != UiElementType.AVATAR
                && control.type != UiElementType.IMAGE
                && control.type != UiElementType.ICON_BUTTON) {
            return false;
        }
        float aspect = control.box.getHeight() == 0 ? 0f : control.box.getWidth() / (float) control.box.getHeight();
        return control.box.getLeft() < context.pageWidth * 0.26f
                && control.box.getWidth() >= 28
                && control.box.getWidth() <= Math.max(180, context.pageWidth / 4)
                && control.box.getHeight() >= 28
                && control.box.getHeight() <= Math.max(180, context.pageHeight / 6)
                && aspect > 0.72f
                && aspect < 1.38f
                && centerY(control.box) > context.pageHeight * 0.18f
                && centerY(control.box) < context.pageHeight * 0.90f;
    }

    private boolean isLikelyConversationText(WorkingText text, LayoutContext context) {
        float leftRatio = text.box.getLeft() / (float) Math.max(1, context.pageWidth);
        float rightRatio = text.box.getRight() / (float) Math.max(1, context.pageWidth);
        float widthRatio = text.box.getWidth() / (float) Math.max(1, context.pageWidth);
        return leftRatio > 0.10f
                && leftRatio < 0.72f
                && rightRatio < 0.96f
                && widthRatio > 0.10f
                && widthRatio < 0.70f
                && text.box.getHeight() >= 18
                && text.box.getHeight() <= 96
                && text.text.length() <= 40
                && centerY(text.box) > context.pageHeight * 0.18f
                && centerY(text.box) < context.pageHeight * 0.92f;
    }

    private boolean hasNearbyConversationAvatar(WorkingText text, List<WorkingControl> controls, LayoutContext context) {
        for (WorkingControl control : controls) {
            if (isConversationAvatar(control, context)
                    && control.box.getRight() <= text.box.getLeft()
                    && text.box.getLeft() - control.box.getRight() < context.pageWidth * 0.20f
                    && (overlapRatioY(text.box, control.box) > 0.28f || Math.abs(centerY(text.box) - centerY(control.box)) < Math.max(56f, context.pageHeight / 28f))) {
                return true;
            }
        }
        return false;
    }

    private void assignSections(List<WorkingText> texts, List<WorkingControl> controls, Geometry geometry, LayoutContext context) {
        for (WorkingControl control : controls) {
            control.sectionType = chooseSection(control.box, control.type, geometry, context);
        }
        for (WorkingText text : texts) {
            text.sectionType = chooseSection(text.box, null, geometry, context);
        }
    }

    private CompactSectionType chooseSection(BoundingBox box, UiElementType type, Geometry geometry, LayoutContext context) {
        if (intersects(geometry.modal, box)) {
            return CompactSectionType.MODAL;
        }
        if (intersects(geometry.bottomBar, box) && isBottomBarElement(box, type, geometry.bottomBar, context)) {
            return CompactSectionType.BOTTOM_BAR;
        }
        if (intersects(geometry.header, box)) {
            return CompactSectionType.HEADER;
        }
        if (type != null && isFloatingElement(type, box, context) && !intersects(geometry.denseList, box)) {
            return CompactSectionType.FLOATING;
        }
        if (intersects(geometry.denseList, box)) {
            return CompactSectionType.DENSE_LIST;
        }
        float cx = centerX(box) / Math.max(1f, context.pageWidth);
        float cy = centerY(box) / Math.max(1f, context.pageHeight);
        if (cx > 0.14f && cx < 0.86f && cy > 0.15f && cy < 0.84f) {
            return CompactSectionType.PRIMARY;
        }
        return CompactSectionType.SECONDARY;
    }

    private boolean isBottomBarElement(BoundingBox box, UiElementType type, BoundingBox bottomBar, LayoutContext context) {
        if (box == null || bottomBar == null) {
            return false;
        }
        float centerY = centerY(box);
        float centerYRatio = centerY / Math.max(1f, context.pageHeight);
        float widthRatio = box.getWidth() / (float) Math.max(1, context.pageWidth);
        boolean navigationLike = type == UiElementType.TAB
                || type == UiElementType.ICON_BUTTON
                || type == UiElementType.TEXT_LINK
                || widthRatio <= 0.30f;
        return navigationLike
                && centerYRatio >= 0.80f
                && centerY >= bottomBar.getTop() + bottomBar.getHeight() * 0.18f;
    }

    private boolean isFloatingElement(UiElementType type, BoundingBox box, LayoutContext context) {
        if (!(type == UiElementType.ICON_BUTTON || type == UiElementType.BUTTON)) {
            return false;
        }
        int area = Math.max(1, box.getWidth() * box.getHeight());
        int pageArea = Math.max(1, context.pageWidth * context.pageHeight);
        if (area > pageArea * 0.03f) {
            return false;
        }
        return box.getRight() > context.pageWidth * 0.82f && box.getBottom() > context.pageHeight * 0.72f;
    }

    private void applyTaskMatch(List<WorkingText> texts, List<WorkingControl> controls, LayoutContext context) {
        if (!context.enableTaskAwareBoost || context.taskGoalNormalized.isEmpty()) {
            return;
        }
        for (WorkingControl control : controls) {
            control.taskMatchScore = scoreGoalMatch(control.normalizedLabel, context);
        }
        for (WorkingText text : texts) {
            text.taskMatchScore = scoreGoalMatch(text.normalizedText, context);
        }
    }

    private void scoreElements(List<WorkingText> texts, List<WorkingControl> controls, LayoutContext context) {
        for (WorkingControl control : controls) {
            boolean conversationAvatar = control.sectionType == CompactSectionType.DENSE_LIST && isConversationAvatar(control, context);
            float importance = 0.14f
                    + control.confidence * 0.40f
                    + controlTypeBoost(control.type)
                    + sectionBoost(control.sectionType)
                    + (1f - distanceToPageCenter(control.box, context.pageWidth, context.pageHeight)) * 0.08f
                    + (control.hasInsideText ? 0.08f : 0f)
                    + (control.hasPairText ? 0.10f : 0f)
                    + (conversationAvatar ? 0.18f : 0f)
                    + (isCompactCriticalIcon(control) ? 0.14f : 0f)
                    + taskBoost(control.taskMatchScore, context)
                    - densePenalty(control, context);
            if ((control.type == UiElementType.IMAGE || control.type == UiElementType.AVATAR) && !conversationAvatar) {
                importance -= 0.16f;
            }
            if (control.type == UiElementType.CARD) {
                importance -= control.sectionType == CompactSectionType.DENSE_LIST ? 0.04f : 0.10f;
            }
            control.importance = clamp(importance, 0f, 1f);
            control.role = resolveControlRole(control);
        }

        for (WorkingText text : texts) {
            boolean conversationText = text.sectionType == CompactSectionType.DENSE_LIST && isLikelyConversationText(text, context);
            boolean avatarAligned = conversationText && hasNearbyConversationAvatar(text, controls, context);
            if (avatarAligned || (conversationText && text.box.getWidth() > context.pageWidth * 0.18f)) {
                text.keepStrong = true;
            }
            float titleBoost = clamp(text.box.getHeight() / (float) Math.max(1, context.pageHeight) * 20f, 0f, 0.22f);
            float lengthPenalty = text.text.length() > 32 ? 0.15f : (text.text.length() > 18 ? 0.06f : 0f);
            float importance = 0.10f
                    + text.confidence * 0.34f
                    + sectionBoost(text.sectionType)
                    + (1f - distanceToPageCenter(text.box, context.pageWidth, context.pageHeight)) * 0.06f
                    + titleBoost
                    + (text.keepStrong ? 0.16f : 0f)
                    + (text.paired ? 0.10f : 0f)
                    + (conversationText ? 0.18f : 0f)
                    + (avatarAligned ? 0.08f : 0f)
                    + taskBoost(text.taskMatchScore, context)
                    - lengthPenalty;
            if (text.consumed) {
                importance -= 0.32f;
            }
            text.importance = clamp(importance, 0f, 1f);
            text.role = resolveTextRole(text, controlForText(controls, text));
        }
    }

    private LinkedHashMap<CompactSectionType, SectionAccumulator> buildSectionAccumulators(
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LayoutContext context
    ) {
        LinkedHashMap<CompactSectionType, SectionAccumulator> map = new LinkedHashMap<>();
        for (CompactSectionType type : CompactSectionType.values()) {
            map.put(type, new SectionAccumulator(type));
        }
        for (WorkingControl control : controls) {
            map.get(control.sectionType).controls.add(control);
        }
        for (WorkingText text : texts) {
            if (!text.consumed || text.keepStrong || text.taskMatchScore > 0f) {
                map.get(text.sectionType).texts.add(text);
            }
        }
        for (SectionAccumulator accumulator : map.values()) {
            accumulator.recompute(context);
        }
        populateDenseSectionMetadata(map.get(CompactSectionType.DENSE_LIST), context);
        return map;
    }

    private void populateDenseSectionMetadata(SectionAccumulator denseSection, LayoutContext context) {
        if (denseSection == null || (denseSection.controls.isEmpty() && denseSection.texts.isEmpty())) {
            return;
        }
        List<RowGroup> rows = buildRowGroups(denseSection, context);
        if (rows.size() < 4) {
            return;
        }
        Set<RowGroup> visibleRows = new LinkedHashSet<>();
        int collapsed = 0;
        List<BoundingBox> rowBoxes = new ArrayList<>();
        for (RowGroup row : rows) {
            BoundingBox rowBox = rowOutputBox(row);
            if (rowBox != null) {
                rowBoxes.add(rowBox);
            }
            if (isCollapsedRow(row)) {
                collapsed++;
            } else {
                visibleRows.add(row);
            }
        }
        BoundingBox denseBox = exactUnion(rowBoxes);
        if (denseBox != null) {
            denseSection.box = denseBox;
        }
        denseSection.collapsedItemCount = collapsed;
        denseSection.summaryOverride = collapsed > 0 ? buildDenseSummary(rows, visibleRows) : null;
    }

    private void collapseDenseSection(SectionAccumulator denseSection, LayoutContext context) {
        if (denseSection == null || (denseSection.controls.isEmpty() && denseSection.texts.isEmpty())) {
            return;
        }
        List<RowGroup> rows = buildRowGroups(denseSection, context);
        if (rows.size() < 4) {
            return;
        }
        rows.sort(Comparator.comparingInt(row -> row.top));
        Set<RowGroup> keepRows = new LinkedHashSet<>();
        for (int index = 0; index < rows.size() && index < context.options.getDenseSectionItemCap(); index++) {
            keepRows.add(rows.get(index));
        }
        for (RowGroup row : rows) {
            if (row.taskMatched) {
                keepRows.add(row);
            }
        }
        int collapsed = 0;
        for (RowGroup row : rows) {
            if (keepRows.contains(row)) {
                continue;
            }
            collapsed++;
            WorkingControl retainedAnchor = selectCollapsedRowAnchor(row, context);
            for (WorkingControl control : row.controls) {
                control.hiddenByDenseCollapse = control != retainedAnchor;
                control.retainedAsCollapsedAnchor = control == retainedAnchor;
            }
            for (WorkingText text : row.texts) {
                text.hiddenByDenseCollapse = true;
            }
        }
        denseSection.collapsedItemCount = collapsed;
        denseSection.summaryOverride = buildDenseSummary(rows, keepRows);
    }

    private List<RowGroup> buildRowGroups(SectionAccumulator denseSection, LayoutContext context) {
        List<RowGroup> leadingRows = buildRowsFromLeadingAnchors(denseSection, context);
        if (leadingRows.size() >= 4) {
            return leadingRows;
        }
        List<RowGroup> labelRows = buildRowsFromLabelControls(denseSection, context);
        if (labelRows.size() >= 4) {
            return labelRows;
        }
        List<RowGroup> wideRows = buildRowsFromWideAnchors(denseSection, context);
        if (wideRows.size() >= 4) {
            return wideRows;
        }
        return buildRowsFromTextAnchors(denseSection, context);
    }

    private List<RowGroup> buildRowsFromLeadingAnchors(SectionAccumulator denseSection, LayoutContext context) {
        List<WorkingControl> anchors = new ArrayList<>();
        for (WorkingControl control : denseSection.controls) {
            if (isLeadingRowAnchor(control, context)) {
                anchors.add(control);
            }
        }
        if (anchors.size() < 4) {
            return Collections.emptyList();
        }
        anchors.sort(READING_ORDER_CONTROL);
        List<RowGroup> rows = new ArrayList<>();
        for (WorkingControl anchor : anchors) {
            addControlTokenToRow(rows, anchor, context.pageHeight, "leading_anchor");
        }
        if (rows.size() < 4) {
            return Collections.emptyList();
        }
        attachDenseSectionContentToRows(rows, denseSection, new LinkedHashSet<>(anchors), Collections.emptySet(), context);
        attachWideControlsToNarrowRows(rows, denseSection, context);
        rows.removeIf(row -> row.controls.isEmpty() && row.texts.isEmpty());
        return rows;
    }

    private void attachWideControlsToNarrowRows(List<RowGroup> rows, SectionAccumulator denseSection, LayoutContext context) {
        List<WorkingControl> wideControls = new ArrayList<>();
        for (WorkingControl control : denseSection.controls) {
            if (isWideDenseListAnchor(control, context)) {
                wideControls.add(control);
            }
        }
        if (wideControls.isEmpty()) {
            return;
        }
        Set<WorkingControl> used = new LinkedHashSet<>();
        for (RowGroup row : rows) {
            used.addAll(row.controls);
        }
        for (RowGroup row : rows) {
            BoundingBox rowBox = row.toBox();
            if (rowBox.getWidth() >= context.pageWidth * 0.40f) {
                continue;
            }
            WorkingControl best = null;
            float bestScore = Float.NEGATIVE_INFINITY;
            for (WorkingControl control : wideControls) {
                if (used.contains(control)) {
                    continue;
                }
                float overlapY = overlapRatioY(control.box, rowBox);
                float centerDistance = Math.abs(centerY(control.box) - centerY(rowBox));
                float tolerance = Math.max(42f, Math.max(control.box.getHeight(), rowBox.getHeight()) * 0.90f);
                if (overlapY < 0.22f && centerDistance > tolerance) {
                    continue;
                }
                float score = overlapY * 2.0f - centerDistance / Math.max(1f, context.pageHeight);
                if (best == null || score > bestScore) {
                    best = control;
                    bestScore = score;
                }
            }
            if (best != null) {
                row.controls.add(best);
                row.top = Math.min(row.top, best.box.getTop());
                row.bottom = Math.max(row.bottom, best.box.getBottom());
                row.taskMatched = row.taskMatched || best.taskMatchScore > 0f;
                used.add(best);
            }
        }
    }

    private List<RowGroup> buildRowsFromLabelControls(SectionAccumulator denseSection, LayoutContext context) {
        List<WorkingControl> tokens = new ArrayList<>();
        for (WorkingControl control : denseSection.controls) {
            if (isRowLabelControl(control, context)) {
                tokens.add(control);
            }
        }
        if (tokens.size() < 4) {
            return Collections.emptyList();
        }
        tokens.sort(READING_ORDER_CONTROL);
        List<RowGroup> rows = new ArrayList<>();
        for (WorkingControl token : tokens) {
            addControlTokenToRow(rows, token, context.pageHeight, "label_control");
        }
        if (rows.size() < 4) {
            return Collections.emptyList();
        }
        attachDenseSectionContentToRows(rows, denseSection, new LinkedHashSet<>(tokens), Collections.emptySet(), context);
        rows.removeIf(row -> row.controls.isEmpty() && row.texts.isEmpty());
        return rows;
    }

    private List<RowGroup> buildRowsFromWideAnchors(SectionAccumulator denseSection, LayoutContext context) {
        List<WorkingControl> anchors = new ArrayList<>();
        for (WorkingControl control : denseSection.controls) {
            if (isWideDenseListAnchor(control, context)) {
                anchors.add(control);
            }
        }
        if (anchors.size() < 3) {
            return Collections.emptyList();
        }
        anchors.sort(READING_ORDER_CONTROL);
        List<RowGroup> rows = new ArrayList<>();
        for (WorkingControl anchor : anchors) {
            RowGroup row = new RowGroup(anchor.box.getTop(), anchor.box.getBottom());
            row.controls.add(anchor);
            row.taskMatched = anchor.taskMatchScore > 0f;
            registerRowAnchor(row, "wide_control", anchor.id);
            rows.add(row);
        }
        attachDenseSectionContentToRows(rows, denseSection, new LinkedHashSet<>(anchors), Collections.emptySet(), context);
        rows.removeIf(row -> row.controls.isEmpty() && row.texts.isEmpty());
        return rows;
    }

    private List<RowGroup> buildRowsFromTextAnchors(SectionAccumulator denseSection, LayoutContext context) {
        List<RowGroup> rows = new ArrayList<>();
        LinkedHashSet<WorkingText> anchorTexts = new LinkedHashSet<>();
        for (WorkingText text : denseSection.texts) {
            addTextToRow(rows, text, context.pageHeight);
            anchorTexts.add(text);
        }
        rows.removeIf(row -> row.texts.isEmpty() || row.toBox().getWidth() < context.pageWidth * 0.14f);
        if (rows.size() < 4) {
            return Collections.emptyList();
        }
        attachDenseSectionContentToRows(rows, denseSection, Collections.emptySet(), anchorTexts, context);
        rows.removeIf(row -> row.controls.isEmpty() && row.texts.isEmpty());
        return rows;
    }

    private void attachDenseSectionContentToRows(
            List<RowGroup> rows,
            SectionAccumulator denseSection,
            Set<WorkingControl> skippedControls,
            Set<WorkingText> skippedTexts,
            LayoutContext context
    ) {
        for (WorkingControl control : denseSection.controls) {
            if (skippedControls.contains(control)) {
                continue;
            }
            RowGroup row = nearestRow(rows, control.box, context.pageHeight);
            if (row != null && shouldAttachControlToRow(control, row, context)) {
                if (!row.controls.contains(control)) {
                    row.controls.add(control);
                }
                row.top = Math.min(row.top, control.box.getTop());
                row.bottom = Math.max(row.bottom, control.box.getBottom());
                row.taskMatched = row.taskMatched || control.taskMatchScore > 0f;
            }
        }
        for (WorkingText text : denseSection.texts) {
            if (skippedTexts.contains(text)) {
                continue;
            }
            RowGroup row = nearestRow(rows, text.box, context.pageHeight);
            if (row != null && shouldAttachBoxToRow(text.box, row, context.pageHeight)) {
                if (!row.texts.contains(text)) {
                    row.texts.add(text);
                }
                row.top = Math.min(row.top, text.box.getTop());
                row.bottom = Math.max(row.bottom, text.box.getBottom());
                row.taskMatched = row.taskMatched || text.taskMatchScore > 0f;
            }
        }
    }

    private boolean isLeadingRowAnchor(WorkingControl control, LayoutContext context) {
        if (control == null || control.box == null) {
            return false;
        }
        if (control.type != UiElementType.AVATAR
                && control.type != UiElementType.IMAGE
                && control.type != UiElementType.ICON_BUTTON) {
            return false;
        }
        float leftRatio = control.box.getLeft() / (float) Math.max(1, context.pageWidth);
        float aspect = control.box.getHeight() == 0 ? 0f : control.box.getWidth() / (float) control.box.getHeight();
        return leftRatio < 0.28f
                && control.box.getWidth() >= 24
                && control.box.getWidth() <= Math.max(180, context.pageWidth / 4)
                && control.box.getHeight() >= 24
                && control.box.getHeight() <= Math.max(180, context.pageHeight / 7)
                && aspect > 0.55f
                && aspect < 1.65f
                && centerY(control.box) > context.pageHeight * 0.18f
                && centerY(control.box) < context.pageHeight * 0.92f;
    }

    private boolean isRowLabelControl(WorkingControl control, LayoutContext context) {
        if (control == null || control.box == null) {
            return false;
        }
        if (control.type == UiElementType.ICON_BUTTON
                || control.type == UiElementType.INPUT
                || control.type == UiElementType.CARD
                || control.type == UiElementType.IMAGE
                || control.type == UiElementType.AVATAR
                || control.type == UiElementType.TAB) {
            return false;
        }
        if (isGenericLabel(control.label, control.type)) {
            return false;
        }
        float widthRatio = control.box.getWidth() / (float) Math.max(1, context.pageWidth);
        int height = control.box.getHeight();
        return widthRatio > 0.06f
                && widthRatio < 0.78f
                && height >= 16
                && height <= Math.max(110, context.pageHeight / 16)
                && centerY(control.box) > context.pageHeight * 0.18f
                && centerY(control.box) < context.pageHeight * 0.92f;
    }

    private void addControlTokenToRow(List<RowGroup> rows, WorkingControl control, int pageHeight, String anchorType) {
        RowGroup nearest = nearestRow(rows, control.box, pageHeight);
        if (nearest == null || !shouldAttachTokenToRow(control.box, nearest, pageHeight)) {
            RowGroup row = new RowGroup(control.box.getTop(), control.box.getBottom());
            row.controls.add(control);
            row.taskMatched = control.taskMatchScore > 0f;
            registerRowAnchor(row, anchorType, control.id);
            rows.add(row);
            return;
        }
        if (!nearest.controls.contains(control)) {
            nearest.controls.add(control);
        }
        nearest.top = Math.min(nearest.top, control.box.getTop());
        nearest.bottom = Math.max(nearest.bottom, control.box.getBottom());
        nearest.taskMatched = nearest.taskMatched || control.taskMatchScore > 0f;
        registerRowAnchor(nearest, anchorType, control.id);
    }

    private void registerRowAnchor(RowGroup row, String anchorType, String anchorId) {
        if (row.anchorType.isEmpty()) {
            row.anchorType = anchorType;
        } else if (!row.anchorType.equals(anchorType)) {
            row.anchorType = "merged";
        }
        if (anchorId != null && !anchorId.isEmpty() && !row.anchorIds.contains(anchorId)) {
            row.anchorIds.add(anchorId);
        }
    }

    private boolean shouldAttachTokenToRow(BoundingBox box, RowGroup row, int pageHeight) {
        BoundingBox rowBox = row.toBox();
        float overlapY = overlapRatioY(box, rowBox);
        float centerDistance = Math.abs(centerY(rowBox) - centerY(box));
        float tolerance = Math.max(30f, Math.min(Math.max(rowBox.getHeight(), box.getHeight()) * 0.48f, pageHeight / 24f));
        return overlapY > 0.18f || centerDistance <= tolerance;
    }

    private void addTextToRow(List<RowGroup> rows, WorkingText text, int pageHeight) {
        RowGroup nearest = nearestRow(rows, text.box, pageHeight);
        if (nearest == null || !shouldAttachBoxToRow(text.box, nearest, pageHeight)) {
            RowGroup row = new RowGroup(text.box.getTop(), text.box.getBottom());
            row.texts.add(text);
            row.taskMatched = text.taskMatchScore > 0f;
            registerRowAnchor(row, "text", text.id);
            rows.add(row);
            return;
        }
        if (!nearest.texts.contains(text)) {
            nearest.texts.add(text);
        }
        nearest.top = Math.min(nearest.top, text.box.getTop());
        nearest.bottom = Math.max(nearest.bottom, text.box.getBottom());
        nearest.taskMatched = nearest.taskMatched || text.taskMatchScore > 0f;
    }

    private void mergeOverflowSections(
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LinkedHashMap<CompactSectionType, SectionAccumulator> sections,
            LayoutContext context
    ) {
        List<SectionAccumulator> populated = new ArrayList<>();
        for (SectionAccumulator section : sections.values()) {
            if (!section.isEmpty()) {
                populated.add(section);
            }
        }
        if (populated.size() <= context.options.getMaxSections()) {
            return;
        }
        populated.sort(Comparator.comparingDouble(SectionAccumulator::getImportance).reversed());
        Set<CompactSectionType> keepTypes = new HashSet<>();
        for (int index = 0; index < context.options.getMaxSections() - 1 && index < populated.size(); index++) {
            keepTypes.add(populated.get(index).type);
        }
        keepTypes.add(CompactSectionType.SECONDARY);
        for (WorkingControl control : controls) {
            if (!keepTypes.contains(control.sectionType)) {
                control.sectionType = CompactSectionType.SECONDARY;
            }
        }
        for (WorkingText text : texts) {
            if (!keepTypes.contains(text.sectionType)) {
                text.sectionType = CompactSectionType.SECONDARY;
            }
        }
    }

    private List<CompactSection> toCompactSections(LinkedHashMap<CompactSectionType, SectionAccumulator> sections) {
        List<CompactSection> result = new ArrayList<>();
        for (SectionAccumulator section : sections.values()) {
            if (!section.isEmpty()) {
                result.add(new CompactSection(section.getId(), section.type, section.box, section.importance, section.getSummaryText(), section.collapsedItemCount));
            }
        }
        result.sort(Comparator.comparingDouble(CompactSection::getImportance).reversed());
        return result;
    }

    private List<CompactSection> rebuildOutputSections(
            List<CompactSection> sections,
            List<CompactListItem> items,
            List<CompactTextBlock> texts,
            List<CompactUiElement> controls
    ) {
        Map<String, List<BoundingBox>> sectionBoxes = new HashMap<>();
        for (CompactUiElement control : controls) {
            sectionBoxes.computeIfAbsent(control.getSectionId(), key -> new ArrayList<>()).add(control.getBoundingBox());
        }
        for (CompactTextBlock text : texts) {
            sectionBoxes.computeIfAbsent(text.getSectionId(), key -> new ArrayList<>()).add(text.getBoundingBox());
        }
        for (CompactListItem item : items) {
            sectionBoxes.computeIfAbsent(item.getSectionId(), key -> new ArrayList<>()).add(item.getBoundingBox());
        }

        List<CompactSection> result = new ArrayList<>();
        for (CompactSection section : sections) {
            List<BoundingBox> boxes = sectionBoxes.get(section.getId());
            if (boxes == null || boxes.isEmpty()) {
                continue;
            }
            BoundingBox boundingBox = exactUnion(boxes);
            if (boundingBox == null) {
                continue;
            }
            result.add(new CompactSection(
                    section.getId(),
                    section.getType(),
                    boundingBox,
                    section.getImportance(),
                    section.getSummaryText(),
                    section.getCollapsedItemCount()
            ));
        }
        result.sort(Comparator.comparingDouble(CompactSection::getImportance).reversed());
        return result;
    }

    private Map<CompactSectionType, Integer> buildSectionOrder(List<CompactSection> sections) {
        Map<CompactSectionType, Integer> order = new HashMap<>();
        for (int index = 0; index < sections.size(); index++) {
            order.put(sections.get(index).getType(), index);
        }
        return order;
    }

    private List<CompactListItem> buildCompactListItems(
            LinkedHashMap<CompactSectionType, SectionAccumulator> sections,
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LayoutContext context
    ) {
        SectionAccumulator denseSection = sections.get(CompactSectionType.DENSE_LIST);
        if (denseSection == null || denseSection.isEmpty()) {
            clearItemAssignments(texts, controls);
            return Collections.emptyList();
        }
        List<RowGroup> rows = buildRowGroups(denseSection, context);
        attachCompanionControlsToRows(rows, controls, context);
        rows.sort(Comparator.comparingInt((RowGroup row) -> row.top).thenComparingInt(row -> row.toBox().getLeft()));
        BoundingBox referenceSpan = denseRowReferenceSpan(rows, context);

        clearItemAssignments(texts, controls);
        List<CompactListItem> result = new ArrayList<>();
        int rowIndex = 1;
        for (RowGroup row : rows) {
            String itemId = "item_" + rowIndex++;
            for (WorkingControl control : row.controls) {
                control.itemId = itemId;
            }
            for (WorkingText text : row.texts) {
                text.itemId = itemId;
            }
            BoundingBox itemBox = rowOutputBox(row);
            if (itemBox == null) {
                itemBox = row.toBox();
            }
            if (referenceSpan != null
                    && itemBox != null
                    && itemBox.getWidth() < context.pageWidth * 0.40f
                    && (isCollapsedRow(row) || "leading_anchor".equals(row.anchorType))) {
                itemBox = new BoundingBox(
                        Math.min(itemBox.getLeft(), referenceSpan.getLeft()),
                        itemBox.getTop(),
                        Math.max(itemBox.getRight(), referenceSpan.getRight()),
                        itemBox.getBottom()
                );
            }
            result.add(new CompactListItem(
                    itemId,
                    CompactSectionType.DENSE_LIST.toJsonName(),
                    itemBox,
                    rowImportance(row),
                    rowSummaryForItem(row),
                    collectTextIds(row),
                    collectControlIds(row)
            ));
        }
        return result;
    }

    private void attachCompanionControlsToRows(List<RowGroup> rows, List<WorkingControl> controls, LayoutContext context) {
        Set<WorkingControl> assigned = new LinkedHashSet<>();
        for (RowGroup row : rows) {
            assigned.addAll(row.controls);
        }
        for (WorkingControl control : controls) {
            if (assigned.contains(control)
                    || !isLeadingRowAnchor(control, context)
                    || control.sectionType == CompactSectionType.HEADER
                    || control.sectionType == CompactSectionType.BOTTOM_BAR
                    || control.sectionType == CompactSectionType.MODAL) {
                continue;
            }
            RowGroup nearest = nearestRow(rows, control.box, context.pageHeight);
            if (nearest == null) {
                continue;
            }
            BoundingBox rowBox = rowOutputBox(nearest);
            if (rowBox == null) {
                rowBox = nearest.toBox();
            }
            float overlapY = overlapRatioY(control.box, rowBox);
            float centerDistance = Math.abs(centerY(control.box) - centerY(rowBox));
            float tolerance = Math.max(52f, Math.max(control.box.getHeight(), rowBox.getHeight()) * 0.85f);
            if ((overlapY < 0.28f && centerDistance > tolerance)
                    || control.box.getLeft() > rowBox.getLeft()) {
                continue;
            }
            nearest.controls.add(control);
            nearest.top = Math.min(nearest.top, control.box.getTop());
            nearest.bottom = Math.max(nearest.bottom, control.box.getBottom());
            nearest.taskMatched = nearest.taskMatched || control.taskMatchScore > 0f;
            control.sectionType = CompactSectionType.DENSE_LIST;
            control.importance = Math.max(control.importance, 0.48f);
            control.role = resolveControlRole(control);
            assigned.add(control);
        }
    }
    private BoundingBox denseRowReferenceSpan(List<RowGroup> rows, LayoutContext context) {
        List<BoundingBox> wideRows = new ArrayList<>();
        for (RowGroup row : rows) {
            BoundingBox box = rowOutputBox(row);
            if (box != null && box.getWidth() >= context.pageWidth * 0.40f) {
                wideRows.add(box);
            }
        }
        return exactUnion(wideRows);
    }

    private void clearItemAssignments(List<WorkingText> texts, List<WorkingControl> controls) {
        for (WorkingText text : texts) {
            text.itemId = "";
        }
        for (WorkingControl control : controls) {
            control.itemId = "";
        }
    }

    private boolean isCollapsedRow(RowGroup row) {
        boolean hasRetainedAnchor = false;
        for (WorkingControl control : row.controls) {
            if (!control.hiddenByDenseCollapse && !control.retainedAsCollapsedAnchor) {
                return false;
            }
            hasRetainedAnchor = hasRetainedAnchor || control.retainedAsCollapsedAnchor;
        }
        for (WorkingText text : row.texts) {
            if (!text.hiddenByDenseCollapse) {
                return false;
            }
        }
        return hasRetainedAnchor;
    }

    private BoundingBox rowOutputBox(RowGroup row) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (WorkingControl control : row.controls) {
            if (!control.hiddenByDenseCollapse || control.retainedAsCollapsedAnchor) {
                boxes.add(control.box);
            }
        }
        for (WorkingText text : row.texts) {
            if (!text.hiddenByDenseCollapse) {
                boxes.add(text.box);
            }
        }
        BoundingBox visibleBox = exactUnion(boxes);
        if (!isCollapsedRow(row) || visibleBox == null) {
            return visibleBox;
        }
        return rowFullBox(row);
    }

    private BoundingBox rowFullBox(RowGroup row) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (WorkingControl control : row.controls) {
            boxes.add(control.box);
        }
        for (WorkingText text : row.texts) {
            boxes.add(text.box);
        }
        return exactUnion(boxes);
    }

    private BoundingBox exactUnion(List<BoundingBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return null;
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = 0;
        int bottom = 0;
        for (BoundingBox box : boxes) {
            if (box == null) {
                continue;
            }
            left = Math.min(left, box.getLeft());
            top = Math.min(top, box.getTop());
            right = Math.max(right, box.getRight());
            bottom = Math.max(bottom, box.getBottom());
        }
        if (left == Integer.MAX_VALUE) {
            return null;
        }
        return new BoundingBox(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
    }

    private float rowImportance(RowGroup row) {
        float importance = 0f;
        for (WorkingControl control : row.controls) {
            if (!control.hiddenByDenseCollapse || control.retainedAsCollapsedAnchor) {
                importance = Math.max(importance, control.importance);
            }
        }
        for (WorkingText text : row.texts) {
            if (!text.hiddenByDenseCollapse) {
                importance = Math.max(importance, text.importance);
            }
        }
        return clamp(importance + (row.taskMatched ? 0.06f : 0f), 0f, 1f);
    }

    private WorkingControl selectCollapsedRowAnchor(RowGroup row, LayoutContext context) {
        WorkingControl best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (WorkingControl control : row.controls) {
            float score = control.importance;
            if (isLeadingRowAnchor(control, context)) {
                score += 0.34f;
            } else if (isRowLabelControl(control, context)) {
                score += 0.26f;
            } else if (control.type == UiElementType.INPUT) {
                score += 0.22f;
            } else if (isWideDenseListAnchor(control, context)) {
                score += 0.14f;
            }
            score -= control.box.getHeight() / (float) Math.max(1, context.pageHeight) * 0.20f;
            if (best == null || score > bestScore) {
                best = control;
                bestScore = score;
            }
        }
        return best;
    }

    private String rowSummary(RowGroup row) {
        return buildRowSummary(row, false);
    }

    private String rowSummaryForItem(RowGroup row) {
        return buildRowSummary(row, true);
    }

    private String buildRowSummary(RowGroup row, boolean includeHidden) {
        List<String> parts = new ArrayList<>();
        List<WorkingText> texts = new ArrayList<>(row.texts);
        texts.sort(Comparator.comparingDouble((WorkingText text) -> text.importance).reversed());
        for (WorkingText text : texts) {
            if (includeHidden || !text.hiddenByDenseCollapse) {
                parts.add(text.text);
            }
            if (parts.size() >= 2) {
                break;
            }
        }
        if (parts.size() < 2) {
            List<WorkingControl> controls = new ArrayList<>(row.controls);
            controls.sort(Comparator.comparingDouble((WorkingControl control) -> control.importance).reversed());
            for (WorkingControl control : controls) {
                if ((includeHidden || !control.hiddenByDenseCollapse || control.retainedAsCollapsedAnchor)
                        && !isGenericLabel(control.label, control.type)
                        && !parts.contains(control.label)) {
                    parts.add(control.label);
                }
                if (parts.size() >= 2) {
                    break;
                }
            }
        }
        return limit(joinDistinct(parts, " | ", 2), 80);
    }

    private List<String> collectTextIds(RowGroup row) {
        List<String> ids = new ArrayList<>();
        for (WorkingText text : row.texts) {
            if (!text.hiddenByDenseCollapse) {
                ids.add(text.id);
            }
        }
        return ids;
    }

    private List<String> collectControlIds(RowGroup row) {
        List<String> ids = new ArrayList<>();
        for (WorkingControl control : row.controls) {
            if (!control.hiddenByDenseCollapse || control.retainedAsCollapsedAnchor) {
                ids.add(control.id);
            }
        }
        return ids;
    }

    private Map<String, Integer> buildItemOrder(List<CompactListItem> items) {
        Map<String, Integer> order = new HashMap<>();
        for (int index = 0; index < items.size(); index++) {
            order.put(items.get(index).getId(), index);
        }
        return order;
    }

    private List<CompactUiElement> selectControls(
            List<WorkingControl> controls,
            Map<CompactSectionType, Integer> sectionOrder,
            Map<String, Integer> itemOrder,
            LayoutContext context
    ) {
        List<WorkingControl> eligible = new ArrayList<>();
        for (WorkingControl control : controls) {
            boolean keepHiddenItemAnchor = control.hiddenByDenseCollapse && isDenseItemAnchor(control, context);
            if ((!control.hiddenByDenseCollapse || keepHiddenItemAnchor)
                    && control.importance >= 0.12f
                    && !isHeaderNoiseControl(control)) {
                eligible.add(control);
            }
        }
        eligible.sort(controlComparator(sectionOrder, itemOrder));

        LinkedHashSet<WorkingControl> selected = new LinkedHashSet<>();
        for (WorkingControl control : eligible) {
            if (selected.size() >= context.options.getMaxControls()) {
                break;
            }
            if (mustKeepControl(control)) {
                selected.add(control);
            }
        }
        for (WorkingControl control : eligible) {
            if (selected.size() >= context.options.getMaxControls()) {
                break;
            }
            selected.add(control);
        }

        List<CompactUiElement> result = new ArrayList<>();
        for (WorkingControl control : selected) {
            result.add(new CompactUiElement(control.id, control.sectionType.toJsonName(), control.itemId, control.type, control.label, control.box, control.confidence, control.importance, control.role));
        }
        result.sort(compactControlComparator(sectionOrder, itemOrder));
        return result;
    }

    private List<CompactTextBlock> selectTexts(
            List<WorkingText> texts,
            Map<CompactSectionType, Integer> sectionOrder,
            Map<String, Integer> itemOrder,
            LayoutContext context
    ) {
        List<WorkingText> eligible = new ArrayList<>();
        for (WorkingText text : texts) {
            if (!text.hiddenByDenseCollapse && (!text.consumed || text.keepStrong || text.taskMatchScore > 0f) && text.importance >= 0.14f) {
                eligible.add(text);
            }
        }
        eligible.sort(textComparator(sectionOrder, itemOrder));

        LinkedHashSet<WorkingText> selected = new LinkedHashSet<>();
        int totalChars = 0;
        for (WorkingText text : eligible) {
            if (selected.size() >= context.options.getMaxTexts()) {
                break;
            }
            if (!mustKeepText(text)) {
                continue;
            }
            int nextChars = totalChars + text.text.length();
            if (!selected.isEmpty() && nextChars > context.options.getMaxTextChars()) {
                continue;
            }
            selected.add(text);
            totalChars = nextChars;
        }
        for (WorkingText text : eligible) {
            if (selected.size() >= context.options.getMaxTexts()) {
                break;
            }
            if (selected.contains(text)) {
                continue;
            }
            int nextChars = totalChars + text.text.length();
            if (!selected.isEmpty() && nextChars > context.options.getMaxTextChars()) {
                continue;
            }
            selected.add(text);
            totalChars = nextChars;
        }

        List<CompactTextBlock> result = new ArrayList<>();
        for (WorkingText text : selected) {
            result.add(new CompactTextBlock(text.id, text.sectionType.toJsonName(), text.itemId, text.text, text.box, text.confidence, text.importance, text.role));
        }
        result.sort(compactTextComparator(sectionOrder, itemOrder));
        return result;
    }

    private String buildSummary(
            List<CompactSection> sections,
            List<CompactListItem> items,
            List<CompactUiElement> controls,
            List<CompactTextBlock> texts
    ) {
        List<CompactSection> prioritizedSections = new ArrayList<>(sections);
        prioritizedSections.sort(Comparator
                .comparingInt((CompactSection section) -> summaryPriority(section.getType())).reversed()
                .thenComparing(Comparator.comparingDouble(CompactSection::getImportance).reversed()));

        List<String> parts = new ArrayList<>();
        for (CompactSection section : prioritizedSections) {
            if (parts.size() >= 2) {
                break;
            }
            if (!section.getSummaryText().isEmpty()) {
                parts.add(section.getType().toJsonName() + ": " + section.getSummaryText());
            }
        }
        if (parts.isEmpty()) {
            for (int index = 0; index < items.size() && index < 2; index++) {
                parts.add(items.get(index).getSummaryText());
            }
        }
        if (parts.isEmpty()) {
            for (int index = 0; index < controls.size() && index < 3; index++) {
                parts.add(controls.get(index).getLabel());
            }
        }
        if (parts.isEmpty()) {
            for (int index = 0; index < texts.size() && index < 3; index++) {
                parts.add(texts.get(index).getText());
            }
        }
        return limit(joinDistinct(parts, " | ", 3), 180);
    }

    private int summaryPriority(CompactSectionType type) {
        switch (type) {
            case MODAL: return 7;
            case DENSE_LIST: return 6;
            case PRIMARY: return 5;
            case FLOATING: return 4;
            case SECONDARY: return 3;
            case HEADER: return 2;
            case BOTTOM_BAR: return 1;
            default: return 0;
        }
    }

    private CompactDebugInfo buildDebugInfo(
            List<CompactSection> compactSections,
            List<CompactListItem> compactItems,
            List<CompactTextBlock> compactTexts,
            List<CompactUiElement> compactControls,
            LinkedHashMap<CompactSectionType, SectionAccumulator> sections,
            List<WorkingText> texts,
            List<WorkingControl> controls,
            LayoutContext context
    ) {
        return new CompactDebugInfo(
                buildDebugSectionSources(compactSections, sections, context),
                buildDebugRows(sections.get(CompactSectionType.DENSE_LIST), compactItems, context),
                buildDropSummary(texts, controls, compactTexts, compactControls, context)
        );
    }

    private List<CompactDebugSectionSource> buildDebugSectionSources(
            List<CompactSection> compactSections,
            LinkedHashMap<CompactSectionType, SectionAccumulator> sections,
            LayoutContext context
    ) {
        List<CompactDebugSectionSource> result = new ArrayList<>();
        String denseSource = determineDenseSectionSource(sections.get(CompactSectionType.DENSE_LIST), context);
        for (CompactSection section : compactSections) {
            String source;
            switch (section.getType()) {
                case HEADER:
                    source = "header_heuristic";
                    break;
                case MODAL:
                    source = "modal_heuristic";
                    break;
                case BOTTOM_BAR:
                    source = "bottom_bar_heuristic";
                    break;
                case FLOATING:
                    source = "floating_heuristic";
                    break;
                case DENSE_LIST:
                    source = denseSource;
                    break;
                default:
                    source = "spatial_assignment";
                    break;
            }
            result.add(new CompactDebugSectionSource(section.getId(), section.getType(), source, section.getBoundingBox()));
        }
        return result;
    }

    private String determineDenseSectionSource(SectionAccumulator denseSection, LayoutContext context) {
        if (denseSection == null || (denseSection.controls.isEmpty() && denseSection.texts.isEmpty())) {
            return "spatial_assignment";
        }
        List<RowGroup> rows = buildRowGroups(denseSection, context);
        LinkedHashSet<String> anchorTypes = new LinkedHashSet<>();
        for (RowGroup row : rows) {
            if (!row.anchorType.isEmpty()) {
                anchorTypes.add(row.anchorType);
            }
        }
        if (anchorTypes.size() > 1) {
            return "merged";
        }
        if (anchorTypes.contains("leading_anchor")) {
            return "avatars";
        }
        if (anchorTypes.contains("label_control")) {
            return "label_controls";
        }
        if (anchorTypes.contains("wide_control")) {
            return "wide_controls";
        }
        if (anchorTypes.contains("text")) {
            return "texts";
        }
        return "spatial_assignment";
    }

    private List<CompactDebugRow> buildDebugRows(SectionAccumulator denseSection, List<CompactListItem> items, LayoutContext context) {
        if (denseSection == null || (denseSection.controls.isEmpty() && denseSection.texts.isEmpty())) {
            return Collections.emptyList();
        }
        List<RowGroup> rows = buildRowGroups(denseSection, context);
        Map<String, Integer> itemOrder = buildItemOrder(items);
        rows.sort(Comparator.comparingInt((RowGroup row) -> itemOrder.getOrDefault(rowItemId(row), Integer.MAX_VALUE))
                .thenComparingInt(row -> row.top)
                .thenComparingInt(row -> row.toBox().getLeft()));
        List<CompactDebugRow> result = new ArrayList<>();
        int rowIndex = 1;
        for (RowGroup row : rows) {
            BoundingBox box = rowOutputBox(row);
            if (box == null) {
                box = row.toBox();
            }
            result.add(new CompactDebugRow(
                    CompactSectionType.DENSE_LIST.toJsonName(),
                    rowItemId(row),
                    rowIndex++,
                    box,
                    row.anchorType.isEmpty() ? "none" : row.anchorType,
                    new ArrayList<>(row.anchorIds),
                    isCollapsedRow(row)
            ));
        }
        return result;
    }

    private String rowItemId(RowGroup row) {
        for (WorkingControl control : row.controls) {
            if (control.itemId != null && !control.itemId.isEmpty()) {
                return control.itemId;
            }
        }
        for (WorkingText text : row.texts) {
            if (text.itemId != null && !text.itemId.isEmpty()) {
                return text.itemId;
            }
        }
        return "";
    }

    private CompactDropSummary buildDropSummary(
            List<WorkingText> texts,
            List<WorkingControl> controls,
            List<CompactTextBlock> compactTexts,
            List<CompactUiElement> compactControls,
            LayoutContext context
    ) {
        Set<String> selectedTextIds = new HashSet<>();
        for (CompactTextBlock text : compactTexts) {
            selectedTextIds.add(text.getId());
        }
        Set<String> selectedControlIds = new HashSet<>();
        for (CompactUiElement control : compactControls) {
            selectedControlIds.add(control.getId());
        }
        Map<String, Integer> textCounts = new LinkedHashMap<>();
        for (WorkingText text : texts) {
            if (!selectedTextIds.contains(text.id)) {
                incrementCount(textCounts, reasonForDroppedText(text));
            }
        }
        Map<String, Integer> controlCounts = new LinkedHashMap<>();
        for (WorkingControl control : controls) {
            if (!selectedControlIds.contains(control.id)) {
                incrementCount(controlCounts, reasonForDroppedControl(control, context));
            }
        }
        return new CompactDropSummary(toDropCounts(textCounts), toDropCounts(controlCounts));
    }

    private String reasonForDroppedText(WorkingText text) {
        if (text.hiddenByDenseCollapse) {
            return "dense_collapse_hidden";
        }
        if (text.duplicateOfControlId != null && !text.duplicateOfControlId.isEmpty()) {
            return "duplicate_of_control";
        }
        if (text.consumed) {
            return "consumed_by_control";
        }
        if (text.importance < 0.14f) {
            return "low_importance";
        }
        return "text_budget";
    }

    private String reasonForDroppedControl(WorkingControl control, LayoutContext context) {
        if (control.hiddenByDenseCollapse && !control.retainedAsCollapsedAnchor) {
            return "dense_collapse_hidden";
        }
        if (isHeaderNoiseControl(control)) {
            return "header_noise_filtered";
        }
        if (isDecorativeControl(control, context)) {
            return "decorative_filtered";
        }
        if (control.importance < 0.12f) {
            return "low_importance";
        }
        return "control_budget";
    }

    private boolean isDecorativeControl(WorkingControl control, LayoutContext context) {
        return control.role == CompactElementRole.DECORATION
                && !control.retainedAsCollapsedAnchor
                && !isLeadingRowAnchor(control, context)
                && control.sectionType != CompactSectionType.BOTTOM_BAR
                && control.sectionType != CompactSectionType.HEADER;
    }

    private boolean isHeaderNoiseControl(WorkingControl control) {
        if (control.sectionType != CompactSectionType.HEADER
                || control.retainedAsCollapsedAnchor
                || control.taskMatchScore > 0f) {
            return false;
        }
        if (control.type == UiElementType.ICON_BUTTON
                || control.type == UiElementType.TEXT_LINK
                || control.type == UiElementType.TAB) {
            return false;
        }
        if ((control.type == UiElementType.BUTTON || control.type == UiElementType.INPUT)
                && (!isGenericLabel(control.label, control.type) || control.hasInsideText || control.hasPairText)) {
            return false;
        }
        return control.type == UiElementType.SWITCH
                || control.type == UiElementType.CHECKBOX
                || control.type == UiElementType.RADIO
                || control.type == UiElementType.INPUT
                || control.type == UiElementType.BUTTON;
    }

    private void incrementCount(Map<String, Integer> counts, String reason) {
        counts.put(reason, counts.getOrDefault(reason, 0) + 1);
    }

    private List<CompactDropCount> toDropCounts(Map<String, Integer> counts) {
        List<CompactDropCount> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            result.add(new CompactDropCount(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Comparator<WorkingControl> controlComparator(Map<CompactSectionType, Integer> sectionOrder, Map<String, Integer> itemOrder) {
        return Comparator.comparingInt((WorkingControl control) -> sectionOrder.getOrDefault(control.sectionType, 99))
                .thenComparingInt(control -> itemOrder.getOrDefault(control.itemId, 999))
                .thenComparingInt(control -> control.box.getTop())
                .thenComparingInt(control -> control.box.getLeft())
                .thenComparing(Comparator.comparingDouble((WorkingControl control) -> control.importance).reversed());
    }

    private Comparator<WorkingText> textComparator(Map<CompactSectionType, Integer> sectionOrder, Map<String, Integer> itemOrder) {
        return Comparator.comparingInt((WorkingText text) -> sectionOrder.getOrDefault(text.sectionType, 99))
                .thenComparingInt(text -> itemOrder.getOrDefault(text.itemId, 999))
                .thenComparingInt(text -> text.box.getTop())
                .thenComparingInt(text -> text.box.getLeft())
                .thenComparing(Comparator.comparingDouble((WorkingText text) -> text.importance).reversed());
    }

    private Comparator<CompactUiElement> compactControlComparator(Map<CompactSectionType, Integer> sectionOrder, Map<String, Integer> itemOrder) {
        return Comparator.comparingInt((CompactUiElement control) -> sectionOrder.getOrDefault(sectionTypeOf(control.getSectionId()), 99))
                .thenComparingInt(control -> itemOrder.getOrDefault(control.getItemId(), 999))
                .thenComparingInt(control -> control.getBoundingBox().getTop())
                .thenComparingInt(control -> control.getBoundingBox().getLeft());
    }

    private Comparator<CompactTextBlock> compactTextComparator(Map<CompactSectionType, Integer> sectionOrder, Map<String, Integer> itemOrder) {
        return Comparator.comparingInt((CompactTextBlock text) -> sectionOrder.getOrDefault(sectionTypeOf(text.getSectionId()), 99))
                .thenComparingInt(text -> itemOrder.getOrDefault(text.getItemId(), 999))
                .thenComparingInt(text -> text.getBoundingBox().getTop())
                .thenComparingInt(text -> text.getBoundingBox().getLeft());
    }

    private CompactSectionType sectionTypeOf(String sectionId) {
        for (CompactSectionType type : CompactSectionType.values()) {
            if (type.toJsonName().equals(sectionId)) {
                return type;
            }
        }
        return CompactSectionType.SECONDARY;
    }

    private boolean mustKeepControl(WorkingControl control) {
        return !isHeaderNoiseControl(control)
                && (control.retainedAsCollapsedAnchor || (control.interactive && control.confidence >= 0.34f)
                || control.sectionType == CompactSectionType.MODAL
                || control.sectionType == CompactSectionType.BOTTOM_BAR
                || control.type == UiElementType.INPUT
                || control.type == UiElementType.SWITCH
                || control.type == UiElementType.CHECKBOX
                || control.type == UiElementType.RADIO
                || control.taskMatchScore > 0f
                || isCompactCriticalIcon(control)
                || (control.sectionType == CompactSectionType.DENSE_LIST && isConversationAvatarType(control.type)));
    }

    private boolean mustKeepText(WorkingText text) {
        return text.keepStrong
                || text.taskMatchScore > 0f
                || text.sectionType == CompactSectionType.MODAL
                || text.sectionType == CompactSectionType.HEADER
                || text.sectionType == CompactSectionType.BOTTOM_BAR
                || (text.sectionType == CompactSectionType.DENSE_LIST && text.importance >= 0.44f)
                || text.importance >= 0.72f;
    }

    private float controlTypeBoost(UiElementType type) {
        switch (type) {
            case INPUT: return 0.24f;
            case BUTTON: return 0.22f;
            case SWITCH:
            case CHECKBOX:
            case RADIO: return 0.20f;
            case TAB: return 0.18f;
            case TEXT_LINK: return 0.18f;
            case ICON_BUTTON: return 0.20f;
            case CARD: return 0.04f;
            case AVATAR: return 0.10f;
            case IMAGE: return 0.04f;
            default: return 0.03f;
        }
    }

    private float sectionBoost(CompactSectionType type) {
        switch (type) {
            case MODAL: return 0.20f;
            case BOTTOM_BAR: return 0.16f;
            case HEADER: return 0.10f;
            case PRIMARY: return 0.08f;
            case FLOATING: return 0.10f;
            case DENSE_LIST: return 0.02f;
            default: return 0f;
        }
    }

    private float densePenalty(WorkingControl control, LayoutContext context) {
        if (control.sectionType != CompactSectionType.DENSE_LIST || control.taskMatchScore > 0f) {
            return 0f;
        }
        if (control.type == UiElementType.CARD) {
            return 0.08f;
        }
        if (isConversationAvatar(control, context)) {
            return 0f;
        }
        return control.type == UiElementType.IMAGE ? 0.04f : 0.01f;
    }

    private float taskBoost(float score, LayoutContext context) {
        return context.enableTaskAwareBoost ? score * 0.22f : 0f;
    }

    private float scoreGoalMatch(String normalizedText, LayoutContext context) {
        if (normalizedText == null || normalizedText.isEmpty() || context.taskGoalNormalized.isEmpty()) {
            return 0f;
        }
        String haystack = normalizedText.replace(" ", "");
        String needle = context.taskGoalNormalized.replace(" ", "");
        float score = 0f;
        if (!needle.isEmpty() && haystack.contains(needle)) {
            score = 1f;
        } else if (!haystack.isEmpty() && needle.contains(haystack) && haystack.length() >= 2) {
            score = 0.82f;
        }
        for (String keyword : context.taskKeywords) {
            if (keyword.length() >= 2 && haystack.contains(keyword)) {
                score = Math.max(score, clamp(keyword.length() / 4f, 0.45f, 0.92f));
            }
        }
        return score;
    }

    private CompactElementRole resolveControlRole(WorkingControl control) {
        switch (control.type) {
            case INPUT:
            case SWITCH:
            case CHECKBOX:
            case RADIO:
                return CompactElementRole.INPUT;
            case TAB:
                return CompactElementRole.NAVIGATION;
            case ICON_BUTTON:
                return control.sectionType == CompactSectionType.HEADER || control.sectionType == CompactSectionType.BOTTOM_BAR
                        ? CompactElementRole.NAVIGATION : CompactElementRole.SECONDARY_ACTION;
            case BUTTON:
                return control.sectionType == CompactSectionType.MODAL || control.importance >= 0.72f
                        ? CompactElementRole.PRIMARY_ACTION : CompactElementRole.SECONDARY_ACTION;
            case TEXT_LINK:
                return control.sectionType == CompactSectionType.HEADER || control.sectionType == CompactSectionType.BOTTOM_BAR
                        ? CompactElementRole.NAVIGATION : CompactElementRole.SECONDARY_ACTION;
            case IMAGE:
            case AVATAR:
                return control.sectionType == CompactSectionType.DENSE_LIST ? CompactElementRole.CONTENT : CompactElementRole.DECORATION;
            case CARD:
                return CompactElementRole.CONTENT;
            default:
                return CompactElementRole.SUPPORTING;
        }
    }

    private CompactElementRole resolveTextRole(WorkingText text, WorkingControl relatedControl) {
        if (relatedControl != null) {
            if (relatedControl.role == CompactElementRole.INPUT) {
                return CompactElementRole.INPUT;
            }
            if (relatedControl.role == CompactElementRole.NAVIGATION) {
                return CompactElementRole.NAVIGATION;
            }
            if (relatedControl.role == CompactElementRole.PRIMARY_ACTION || relatedControl.role == CompactElementRole.SECONDARY_ACTION) {
                return CompactElementRole.SUPPORTING;
            }
        }
        if (text.sectionType == CompactSectionType.HEADER || text.box.getHeight() > 44) {
            return CompactElementRole.CONTENT;
        }
        if (text.sectionType == CompactSectionType.BOTTOM_BAR) {
            return CompactElementRole.NAVIGATION;
        }
        if (text.sectionType == CompactSectionType.DENSE_LIST) {
            return CompactElementRole.CONTENT;
        }
        return text.text.length() > 24 ? CompactElementRole.CONTENT : CompactElementRole.SUPPORTING;
    }

    private boolean isCompactCriticalIcon(WorkingControl control) {
        if (control.type != UiElementType.ICON_BUTTON) {
            return false;
        }
        int area = Math.max(1, control.box.getWidth() * control.box.getHeight());
        return control.confidence >= 0.28f
                && area <= 240 * 240
                && (control.sectionType == CompactSectionType.HEADER
                || control.sectionType == CompactSectionType.BOTTOM_BAR
                || (control.sectionType == CompactSectionType.PRIMARY && control.box.getTop() < 420)
                || control.hasPairText
                || control.hasInsideText);
    }

    private boolean isConversationAvatarType(UiElementType type) {
        return type == UiElementType.AVATAR || type == UiElementType.IMAGE || type == UiElementType.ICON_BUTTON;
    }

    private WorkingControl controlForText(List<WorkingControl> controls, WorkingText text) {
        String targetId = text.pairedControlId != null ? text.pairedControlId : text.duplicateOfControlId;
        if (targetId == null) {
            return null;
        }
        for (WorkingControl control : controls) {
            if (control.id.equals(targetId)) {
                return control;
            }
        }
        return null;
    }

    private String buildDenseSummary(List<RowGroup> rows, Set<RowGroup> keptRows) {
        List<String> keywords = new ArrayList<>();
        for (RowGroup row : rows) {
            if (keptRows.contains(row)) {
                continue;
            }
            for (WorkingText text : row.texts) {
                keywords.add(text.text);
            }
            for (WorkingControl control : row.controls) {
                if (!isGenericLabel(control.label, control.type)) {
                    keywords.add(control.label);
                }
            }
        }
        String prefix = (rows.size() - keptRows.size()) + " items collapsed";
        String suffix = joinDistinct(keywords, ", ", 3);
        return suffix.isEmpty() ? prefix : prefix + ": " + suffix;
    }

    private String joinTextsById(List<WorkingText> texts, List<String> ids, int maxLength) {
        if (ids.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (String id : ids) {
            for (WorkingText text : texts) {
                if (text.id.equals(id)) {
                    values.add(text.text);
                    break;
                }
            }
        }
        return limit(joinDistinct(values, " ", 2), maxLength);
    }

    private String joinDistinct(Collection<String> items, String separator, int maxItems) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : items) {
            String value = safeTrim(item);
            if (!value.isEmpty()) {
                unique.add(value);
            }
            if (unique.size() >= maxItems) {
                break;
            }
        }
        return String.join(separator, unique);
    }

    private boolean isGenericLabel(String label, UiElementType type) {
        String normalized = normalizeText(label).replace(" ", "");
        return normalized.isEmpty() || GENERIC_LABELS.contains(normalized) || normalized.equals(type.name().toLowerCase(Locale.US));
    }

    private boolean isConsumableTextControl(UiElementType type) {
        return type == UiElementType.BUTTON || type == UiElementType.INPUT || type == UiElementType.TAB || type == UiElementType.TEXT_LINK || type == UiElementType.ICON_BUTTON;
    }

    private boolean isTextInsideControl(BoundingBox textBox, BoundingBox controlBox) {
        return (centerX(textBox) >= controlBox.getLeft() && centerX(textBox) <= controlBox.getRight() && centerY(textBox) >= controlBox.getTop() && centerY(textBox) <= controlBox.getBottom())
                || intersectionRatio(textBox, controlBox) > 0.35f;
    }

    private boolean isNearControl(BoundingBox textBox, BoundingBox controlBox, UiElementType type) {
        float overlapY = overlapRatioY(textBox, controlBox);
        float horizontalGap = Math.min(Math.abs(textBox.getRight() - controlBox.getLeft()), Math.abs(textBox.getLeft() - controlBox.getRight()));
        if (type == UiElementType.INPUT) {
            boolean above = textBox.getBottom() <= controlBox.getTop() && horizontalOverlap(textBox, controlBox) > 0.25f && controlBox.getTop() - textBox.getBottom() < 80;
            boolean beside = overlapY > 0.55f && horizontalGap < 120;
            return above || beside;
        }
        if (type == UiElementType.SWITCH || type == UiElementType.CHECKBOX || type == UiElementType.RADIO) {
            return overlapY > 0.58f && horizontalGap < 160;
        }
        if (type == UiElementType.ICON_BUTTON) {
            return overlapY > 0.50f && horizontalGap < 140;
        }
        if (type == UiElementType.BUTTON || type == UiElementType.TEXT_LINK || type == UiElementType.TAB) {
            return overlapY > 0.68f && horizontalGap < 60;
        }
        return overlapY > 0.55f && horizontalGap < 96;
    }

    private BoundingBox union(List<BoundingBox> boxes, int pageWidth, int pageHeight, int paddingX, int paddingY) {
        if (boxes == null || boxes.isEmpty()) {
            return null;
        }
        int left = pageWidth;
        int top = pageHeight;
        int right = 0;
        int bottom = 0;
        for (BoundingBox box : boxes) {
            left = Math.min(left, box.getLeft());
            top = Math.min(top, box.getTop());
            right = Math.max(right, box.getRight());
            bottom = Math.max(bottom, box.getBottom());
        }
        return clampBox(new BoundingBox(left - paddingX, top - paddingY, right + paddingX, bottom + paddingY), pageWidth, pageHeight);
    }

    private List<BoundingBox> toBoxes(List<WorkingControl> controls) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (WorkingControl control : controls) {
            boxes.add(control.box);
        }
        return boxes;
    }

    private BoundingBox clampBox(BoundingBox box, int pageWidth, int pageHeight) {
        int left = clamp(box.getLeft(), 0, Math.max(0, pageWidth - 1));
        int top = clamp(box.getTop(), 0, Math.max(0, pageHeight - 1));
        int right = clamp(box.getRight(), left + 1, Math.max(left + 1, pageWidth));
        int bottom = clamp(box.getBottom(), top + 1, Math.max(top + 1, pageHeight));
        return new BoundingBox(left, top, right, bottom);
    }

    private boolean intersects(BoundingBox first, BoundingBox second) {
        if (first == null || second == null) {
            return false;
        }
        return !(first.getRight() <= second.getLeft() || second.getRight() <= first.getLeft() || first.getBottom() <= second.getTop() || second.getBottom() <= first.getTop());
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

    private float overlapRatioY(BoundingBox first, BoundingBox second) {
        int top = Math.max(first.getTop(), second.getTop());
        int bottom = Math.min(first.getBottom(), second.getBottom());
        if (bottom <= top) {
            return 0f;
        }
        return (bottom - top) / (float) Math.max(1, Math.min(first.getHeight(), second.getHeight()));
    }

    private float horizontalOverlap(BoundingBox first, BoundingBox second) {
        int left = Math.max(first.getLeft(), second.getLeft());
        int right = Math.min(first.getRight(), second.getRight());
        if (right <= left) {
            return 0f;
        }
        return (right - left) / (float) Math.max(1, Math.min(first.getWidth(), second.getWidth()));
    }

    private float controlDistance(BoundingBox first, BoundingBox second) {
        float dx = first.getRight() < second.getLeft() ? second.getLeft() - first.getRight() : (second.getRight() < first.getLeft() ? first.getLeft() - second.getRight() : 0f);
        float dy = first.getBottom() < second.getTop() ? second.getTop() - first.getBottom() : (second.getBottom() < first.getTop() ? first.getTop() - second.getBottom() : 0f);
        return (float) Math.hypot(dx, dy);
    }

    private float distanceToPageCenter(BoundingBox box, int pageWidth, int pageHeight) {
        float dx = Math.abs(centerX(box) - pageWidth / 2f) / Math.max(1f, pageWidth / 2f);
        float dy = Math.abs(centerY(box) - pageHeight / 2f) / Math.max(1f, pageHeight / 2f);
        return clamp((dx + dy) / 2f, 0f, 1f);
    }

    private float centerX(BoundingBox box) {
        return (box.getLeft() + box.getRight()) / 2f;
    }

    private float centerY(BoundingBox box) {
        return (box.getTop() + box.getBottom()) / 2f;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private String limit(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeText(String value) {
        return LayoutContext.normalizeStatic(value);
    }

    private static final class LayoutContext {
        private final int pageWidth;
        private final int pageHeight;
        private final CompactAnalysisOptions options;
        private final String taskGoalNormalized;
        private final Set<String> taskKeywords;
        private final boolean enableTaskAwareBoost;

        private LayoutContext(PageSize pageSize, CompactAnalysisOptions options, TaskContext taskContext) {
            this.pageWidth = Math.max(1, pageSize.getWidth());
            this.pageHeight = Math.max(1, pageSize.getHeight());
            this.options = options;
            this.taskGoalNormalized = normalizeStatic(taskContext == null ? "" : taskContext.getGoalText());
            this.taskKeywords = extractKeywords(this.taskGoalNormalized);
            this.enableTaskAwareBoost = options.isEnableTaskAwareBoost() && taskContext != null && taskContext.hasGoalText();
        }

        private static String normalizeStatic(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder(value.length());
            boolean previousWhitespace = false;
            for (int index = 0; index < value.length(); index++) {
                char current = Character.toLowerCase(value.charAt(index));
                if (Character.isLetterOrDigit(current) || isStaticCjk(current)) {
                    builder.append(current);
                    previousWhitespace = false;
                } else if (!previousWhitespace) {
                    builder.append(' ');
                    previousWhitespace = true;
                }
            }
            return builder.toString().trim().replaceAll("\\s+", " ");
        }

        private static Set<String> extractKeywords(String normalizedGoal) {
            LinkedHashSet<String> keywords = new LinkedHashSet<>();
            if (normalizedGoal.isEmpty()) {
                return keywords;
            }
            String compact = normalizedGoal.replace(" ", "");
            if (compact.length() >= 2) {
                keywords.add(compact);
            }
            for (String token : normalizedGoal.split(" ")) {
                if (token.length() >= 2) {
                    keywords.add(token);
                    addCjkGrams(token, keywords);
                }
            }
            addCjkGrams(compact, keywords);
            return keywords;
        }

        private static void addCjkGrams(String token, Set<String> keywords) {
            if (token == null || token.length() < 2) {
                return;
            }
            boolean containsCjk = false;
            for (int index = 0; index < token.length(); index++) {
                if (isStaticCjk(token.charAt(index))) {
                    containsCjk = true;
                    break;
                }
            }
            if (!containsCjk) {
                return;
            }
            for (int gram = 4; gram >= 2; gram--) {
                if (token.length() < gram) {
                    continue;
                }
                for (int start = 0; start <= token.length() - gram; start++) {
                    keywords.add(token.substring(start, start + gram));
                }
            }
        }

        private static boolean isStaticCjk(char value) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
            return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
        }
    }

    private static final class Geometry {
        private BoundingBox header;
        private BoundingBox modal;
        private BoundingBox bottomBar;
        private BoundingBox denseList;
    }

    private static final class WorkingText {
        private final String id;
        private final String text;
        private final String normalizedText;
        private final BoundingBox box;
        private final float confidence;
        private boolean consumed;
        private boolean paired;
        private boolean keepStrong;
        private boolean hiddenByDenseCollapse;
        private String itemId = "";
        private String pairedControlId;
        private String duplicateOfControlId;
        private CompactSectionType sectionType = CompactSectionType.SECONDARY;
        private float importance;
        private float taskMatchScore;
        private CompactElementRole role = CompactElementRole.SUPPORTING;

        private WorkingText(String id, String text, String normalizedText, BoundingBox box, float confidence) {
            this.id = id;
            this.text = text;
            this.normalizedText = normalizedText;
            this.box = box;
            this.confidence = confidence;
        }
    }

    private static final class WorkingControl {
        private final String id;
        private final UiElementType type;
        private final BoundingBox box;
        private final float confidence;
        private final boolean interactive;
        private String label;
        private String normalizedLabel;
        private boolean hasInsideText;
        private boolean hasPairText;
        private boolean hiddenByDenseCollapse;
        private boolean retainedAsCollapsedAnchor;
        private String itemId = "";
        private final List<String> insideTextIds = new ArrayList<>();
        private final List<String> pairTextIds = new ArrayList<>();
        private CompactSectionType sectionType = CompactSectionType.SECONDARY;
        private float importance;
        private float taskMatchScore;
        private CompactElementRole role = CompactElementRole.SUPPORTING;

        private WorkingControl(String id, UiElementType type, String label, String normalizedLabel, BoundingBox box, float confidence) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.normalizedLabel = normalizedLabel;
            this.box = box;
            this.confidence = confidence;
            this.interactive = INTERACTIVE_TYPES.contains(type);
        }
    }

    private static final class SectionAccumulator {
        private final CompactSectionType type;
        private final List<WorkingText> texts = new ArrayList<>();
        private final List<WorkingControl> controls = new ArrayList<>();
        private BoundingBox box;
        private float importance;
        private int collapsedItemCount;
        private String summaryOverride;

        private SectionAccumulator(CompactSectionType type) {
            this.type = type;
        }

        private void recompute(LayoutContext context) {
            List<BoundingBox> boxes = new ArrayList<>();
            float maxImportance = 0f;
            boolean taskMatched = false;
            for (WorkingControl control : controls) {
                if (!control.hiddenByDenseCollapse || control.retainedAsCollapsedAnchor) {
                    boxes.add(control.box);
                    maxImportance = Math.max(maxImportance, control.importance);
                    taskMatched = taskMatched || control.taskMatchScore > 0f;
                }
            }
            for (WorkingText text : texts) {
                if (!text.hiddenByDenseCollapse) {
                    boxes.add(text.box);
                    maxImportance = Math.max(maxImportance, text.importance);
                    taskMatched = taskMatched || text.taskMatchScore > 0f;
                }
            }
            box = boxes.isEmpty() ? null : unionStatic(boxes, context.pageWidth, context.pageHeight, 10, 12);
            importance = clampStatic(maxImportance + sectionBase(type) + (taskMatched ? 0.08f : 0f), 0f, 1f);
        }

        private String getId() {
            return type.toJsonName();
        }

        private float getImportance() {
            return importance;
        }

        private boolean isEmpty() {
            return controls.isEmpty() && texts.isEmpty();
        }

        private String getSummaryText() {
            if (summaryOverride != null && !summaryOverride.isEmpty()) {
                return summaryOverride;
            }
            List<String> phrases = new ArrayList<>();
            List<WorkingControl> controlCopy = new ArrayList<>(controls);
            controlCopy.sort(Comparator.comparingDouble((WorkingControl control) -> control.importance).reversed());
            for (WorkingControl control : controlCopy) {
                if (!control.hiddenByDenseCollapse && !GENERIC_LABELS.contains(control.label.toLowerCase(Locale.US))) {
                    phrases.add(control.label);
                }
                if (phrases.size() >= 2) {
                    break;
                }
            }
            List<WorkingText> textCopy = new ArrayList<>(texts);
            textCopy.sort(Comparator.comparingDouble((WorkingText text) -> text.importance).reversed());
            for (WorkingText text : textCopy) {
                if (!text.hiddenByDenseCollapse && !text.consumed) {
                    phrases.add(text.text);
                }
                if (phrases.size() >= 3) {
                    break;
                }
            }
            return limitStatic(joinDistinctStatic(phrases, " / ", 3), 120);
        }

        private static float sectionBase(CompactSectionType type) {
            switch (type) {
                case MODAL: return 0.18f;
                case BOTTOM_BAR: return 0.14f;
                case HEADER: return 0.08f;
                case PRIMARY: return 0.06f;
                case FLOATING: return 0.08f;
                case DENSE_LIST: return 0.02f;
                default: return 0f;
            }
        }

        private static BoundingBox unionStatic(List<BoundingBox> boxes, int pageWidth, int pageHeight, int paddingX, int paddingY) {
            int left = pageWidth;
            int top = pageHeight;
            int right = 0;
            int bottom = 0;
            for (BoundingBox box : boxes) {
                left = Math.min(left, box.getLeft());
                top = Math.min(top, box.getTop());
                right = Math.max(right, box.getRight());
                bottom = Math.max(bottom, box.getBottom());
            }
            return new BoundingBox(Math.max(0, left - paddingX), Math.max(0, top - paddingY), Math.min(pageWidth, right + paddingX), Math.min(pageHeight, bottom + paddingY));
        }

        private static float clampStatic(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private static String joinDistinctStatic(Collection<String> values, String separator, int maxItems) {
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    unique.add(value.trim());
                }
                if (unique.size() >= maxItems) {
                    break;
                }
            }
            return String.join(separator, unique);
        }

        private static String limitStatic(String value, int maxChars) {
            if (value == null || value.length() <= maxChars) {
                return value == null ? "" : value;
            }
            return value.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
        }
    }

    private static final class RowGroup {
        private int top;
        private int bottom;
        private final List<WorkingControl> controls = new ArrayList<>();
        private final List<WorkingText> texts = new ArrayList<>();
        private final List<String> anchorIds = new ArrayList<>();
        private String anchorType = "";
        private boolean taskMatched;

        private RowGroup(int top, int bottom) {
            this.top = top;
            this.bottom = bottom;
        }

        private BoundingBox toBox() {
            int left = Integer.MAX_VALUE;
            int right = 0;
            for (WorkingControl control : controls) {
                left = Math.min(left, control.box.getLeft());
                right = Math.max(right, control.box.getRight());
            }
            for (WorkingText text : texts) {
                left = Math.min(left, text.box.getLeft());
                right = Math.max(right, text.box.getRight());
            }
            if (left == Integer.MAX_VALUE) {
                left = 0;
            }
            return new BoundingBox(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
        }
    }

    private boolean shouldAttachControlToRow(WorkingControl control, RowGroup row, LayoutContext context) {
        if (!shouldAttachBoxToRow(control.box, row, context.pageHeight)) {
            return false;
        }
        BoundingBox rowAnchorBox = row.toBox();
        int rowHeight = Math.max(1, row.bottom - row.top);
        int controlHeight = Math.max(1, control.box.getHeight());
        if (controlHeight > Math.max(Math.round(rowHeight * 1.45f), context.pageHeight / 10)) {
            return false;
        }
        if (isWideDenseListAnchor(control, context)
                && !row.anchorType.isEmpty()
                && !"wide_control".equals(row.anchorType)
                && overlapRatioY(control.box, rowAnchorBox) < 0.52f) {
            return false;
        }
        if (("leading_anchor".equals(row.anchorType) || "text".equals(row.anchorType))
                && control.box.getWidth() > context.pageWidth * 0.55f
                && control.box.getLeft() > context.pageWidth * 0.24f
                && overlapRatioY(control.box, rowAnchorBox) < 0.58f) {
            return false;
        }
        return true;
    }
    private boolean shouldAttachBoxToRow(BoundingBox box, RowGroup row, int pageHeight) {
        BoundingBox rowBox = row.toBox();
        float overlapY = overlapRatioY(box, rowBox);
        float centerDistance = Math.abs(centerY(rowBox) - centerY(box));
        float tolerance = Math.max(30f, Math.min(Math.max(rowBox.getHeight(), box.getHeight()) * 0.52f, pageHeight / 22f));
        return overlapY > 0.30f || centerDistance <= tolerance;
    }
    private boolean isDenseItemAnchor(WorkingControl control, LayoutContext context) {
        if (control.sectionType != CompactSectionType.DENSE_LIST || control.itemId == null || control.itemId.isEmpty()) {
            return false;
        }
        return isWideDenseListAnchor(control, context)
                || isConversationAvatar(control, context)
                || control.type == UiElementType.INPUT;
    }
    private RowGroup nearestRow(List<RowGroup> rows, BoundingBox box, int pageHeight) {
        RowGroup best = null;
        float bestDistance = Float.MAX_VALUE;
        for (RowGroup row : rows) {
            float distance = Math.abs(centerY(row.toBox()) - centerY(box));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = row;
            }
        }
        if (best == null) {
            return null;
        }
        return shouldAttachBoxToRow(box, best, pageHeight) ? best : null;
    }
}
