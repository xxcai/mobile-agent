package com.screenvision.sdk.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CompactPageAnalysisResult {
    private final PageSize pageSize;
    private final long elapsedMs;
    private final long recognitionElapsedMs;
    private final long postProcessElapsedMs;
    private final String taskGoal;
    private final String summary;
    private final List<CompactSection> sections;
    private final List<CompactListItem> items;
    private final List<CompactTextBlock> textBlocks;
    private final List<CompactUiElement> uiElements;
    private final CompactDebugInfo debugInfo;

    public CompactPageAnalysisResult(
            PageSize pageSize,
            long elapsedMs,
            long recognitionElapsedMs,
            long postProcessElapsedMs,
            String taskGoal,
            String summary,
            List<CompactSection> sections,
            List<CompactListItem> items,
            List<CompactTextBlock> textBlocks,
            List<CompactUiElement> uiElements,
            CompactDebugInfo debugInfo
    ) {
        this.pageSize = pageSize;
        this.elapsedMs = Math.max(0L, elapsedMs);
        this.recognitionElapsedMs = Math.max(0L, recognitionElapsedMs);
        this.postProcessElapsedMs = Math.max(0L, postProcessElapsedMs);
        this.taskGoal = taskGoal == null ? "" : taskGoal;
        this.summary = summary == null ? "" : summary;
        this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.textBlocks = Collections.unmodifiableList(new ArrayList<>(textBlocks));
        this.uiElements = Collections.unmodifiableList(new ArrayList<>(uiElements));
        this.debugInfo = debugInfo;
    }

    public PageSize getPageSize() {
        return pageSize;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public long getRecognitionElapsedMs() {
        return recognitionElapsedMs;
    }

    public long getPostProcessElapsedMs() {
        return postProcessElapsedMs;
    }

    public String getTaskGoal() {
        return taskGoal;
    }

    public String getSummary() {
        return summary;
    }

    public List<CompactSection> getSections() {
        return sections;
    }

    public List<CompactListItem> getItems() {
        return items;
    }

    public List<CompactTextBlock> getTextBlocks() {
        return textBlocks;
    }

    public List<CompactUiElement> getUiElements() {
        return uiElements;
    }

    public CompactDebugInfo getDebugInfo() {
        return debugInfo;
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"page\":{");
        builder.append("\"width\":").append(pageSize.getWidth()).append(",");
        builder.append("\"height\":").append(pageSize.getHeight()).append("},");
        builder.append("\"elapsedMs\":").append(elapsedMs).append(",");
        builder.append("\"recognitionElapsedMs\":").append(recognitionElapsedMs).append(",");
        builder.append("\"postProcessElapsedMs\":").append(postProcessElapsedMs).append(",");
        builder.append("\"taskGoal\":\"").append(escape(taskGoal)).append("\",");
        builder.append("\"summary\":\"").append(escape(summary)).append("\",");
        builder.append("\"sections\":[");
        for (int index = 0; index < sections.size(); index++) {
            CompactSection section = sections.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"id\":\"").append(escape(section.getId())).append("\",");
            builder.append("\"type\":\"").append(section.getType().toJsonName()).append("\",");
            appendBoundingBox(builder, section.getBoundingBox());
            builder.append(",\"importance\":").append(formatFloat(section.getImportance()));
            builder.append(",\"summaryText\":\"").append(escape(section.getSummaryText())).append("\",");
            builder.append("\"collapsedItemCount\":").append(section.getCollapsedItemCount());
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"items\":[");
        for (int index = 0; index < items.size(); index++) {
            CompactListItem item = items.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"id\":\"").append(escape(item.getId())).append("\",");
            builder.append("\"sectionId\":\"").append(escape(item.getSectionId())).append("\",");
            appendBoundingBox(builder, item.getBoundingBox());
            builder.append(",\"importance\":").append(formatFloat(item.getImportance()));
            builder.append(",\"summaryText\":\"").append(escape(item.getSummaryText())).append("\",");
            appendStringArray(builder, "textIds", item.getTextIds());
            builder.append(",");
            appendStringArray(builder, "controlIds", item.getControlIds());
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"texts\":[");
        for (int index = 0; index < textBlocks.size(); index++) {
            CompactTextBlock block = textBlocks.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"id\":\"").append(escape(block.getId())).append("\",");
            builder.append("\"sectionId\":\"").append(escape(block.getSectionId())).append("\",");
            builder.append("\"itemId\":\"").append(escape(block.getItemId())).append("\",");
            builder.append("\"text\":\"").append(escape(block.getText())).append("\",");
            appendBoundingBox(builder, block.getBoundingBox());
            builder.append(",\"confidence\":").append(formatFloat(block.getConfidence()));
            builder.append(",\"importance\":").append(formatFloat(block.getImportance()));
            builder.append(",\"role\":\"").append(block.getRole().toJsonName()).append("\"");
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"controls\":[");
        for (int index = 0; index < uiElements.size(); index++) {
            CompactUiElement element = uiElements.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"id\":\"").append(escape(element.getId())).append("\",");
            builder.append("\"sectionId\":\"").append(escape(element.getSectionId())).append("\",");
            builder.append("\"itemId\":\"").append(escape(element.getItemId())).append("\",");
            builder.append("\"type\":\"").append(element.getType().name().toLowerCase(Locale.US)).append("\",");
            builder.append("\"label\":\"").append(escape(element.getLabel())).append("\",");
            appendBoundingBox(builder, element.getBoundingBox());
            builder.append(",\"confidence\":").append(formatFloat(element.getConfidence()));
            builder.append(",\"importance\":").append(formatFloat(element.getImportance()));
            builder.append(",\"role\":\"").append(element.getRole().toJsonName()).append("\"");
            builder.append("}");
        }
        builder.append("]");
        if (debugInfo != null) {
            builder.append(",\"debug\":{");
            appendDebugInfo(builder, debugInfo);
            builder.append("}");
        }
        builder.append("}");
        return builder.toString();
    }

    private void appendDebugInfo(StringBuilder builder, CompactDebugInfo debugInfo) {
        builder.append("\"sectionSources\":[");
        List<CompactDebugSectionSource> sectionSources = debugInfo.getSectionSources();
        for (int index = 0; index < sectionSources.size(); index++) {
            CompactDebugSectionSource source = sectionSources.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"sectionId\":\"").append(escape(source.getSectionId())).append("\",");
            builder.append("\"type\":\"").append(source.getType().toJsonName()).append("\",");
            builder.append("\"source\":\"").append(escape(source.getSource())).append("\"");
            if (source.getBoundingBox() != null) {
                builder.append(",");
                appendBoundingBox(builder, source.getBoundingBox());
            }
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"rows\":[");
        List<CompactDebugRow> rows = debugInfo.getRows();
        for (int index = 0; index < rows.size(); index++) {
            CompactDebugRow row = rows.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"sectionId\":\"").append(escape(row.getSectionId())).append("\",");
            builder.append("\"itemId\":\"").append(escape(row.getItemId())).append("\",");
            builder.append("\"rowIndex\":").append(row.getRowIndex()).append(",");
            appendBoundingBox(builder, row.getBoundingBox());
            builder.append(",\"anchorType\":\"").append(escape(row.getAnchorType())).append("\",");
            appendStringArray(builder, "anchorIds", row.getAnchorIds());
            builder.append(",\"collapsed\":").append(row.isCollapsed());
            builder.append("}");
        }
        builder.append("],");
        builder.append("\"dropSummary\":{");
        CompactDropSummary dropSummary = debugInfo.getDropSummary();
        if (dropSummary == null) {
            builder.append("\"texts\":[],\"controls\":[]");
            return;
        }
        appendDropCounts(builder, "texts", dropSummary.getTexts());
        builder.append(",");
        appendDropCounts(builder, "controls", dropSummary.getControls());
        builder.append("}");
    }

    private void appendDropCounts(StringBuilder builder, String fieldName, List<CompactDropCount> counts) {
        builder.append("\"").append(fieldName).append("\":[");
        for (int index = 0; index < counts.size(); index++) {
            CompactDropCount count = counts.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"reason\":\"").append(escape(count.getReason())).append("\",");
            builder.append("\"count\":").append(count.getCount());
            builder.append("}");
        }
        builder.append("]");
    }

    private void appendBoundingBox(StringBuilder builder, BoundingBox boundingBox) {
        builder.append("\"bbox\":[")
                .append(boundingBox.getLeft()).append(",")
                .append(boundingBox.getTop()).append(",")
                .append(boundingBox.getRight()).append(",")
                .append(boundingBox.getBottom()).append("]");
    }

    private void appendStringArray(StringBuilder builder, String fieldName, List<String> values) {
        builder.append("\"").append(fieldName).append("\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escape(values.get(index))).append("\"");
        }
        builder.append("]");
    }

    private String formatFloat(float value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
