package com.hh.agent;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.android.AndroidToolManager;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScreenSnapshotProbeActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView resultView;
    private LinearLayout debugPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("屏幕快照探针");
        setContentView(createContentView());
        mainHandler.postDelayed(this::runProbe, 500L);
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F4F1E8"));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, dp(24));

        TextView pageTitle = new TextView(this);
        pageTitle.setText("运营工作台");
        pageTitle.setTextSize(24f);
        pageTitle.setTextColor(Color.parseColor("#1E2A24"));
        pageTitle.setTypeface(pageTitle.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(pageTitle, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("将报销、出差、考勤和待办集中展示在一个页面中。");
        subtitle.setTextSize(14f);
        subtitle.setTextColor(Color.parseColor("#5B625C"));
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        container.addView(subtitle, subtitleParams);

        TextView searchBox = chip("搜索报销单、审批单或会议室", "#FFF8E7", "#CABF9B");
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.topMargin = dp(16);
        container.addView(searchBox, searchParams);

        container.addView(createBannerCard(), cardParams(dp(16)));
        container.addView(createQuickActionsRow(), cardParams(dp(16)));
        container.addView(createTodoCard(), cardParams(dp(16)));
        container.addView(createMetricsCard(), cardParams(dp(16)));

        container.addView(sectionHeader("融合调试面板", "观察原生节点、OCR 与 UI 检测如何共同影响最终动作排序。"), cardParams(dp(20)));

        debugPanel = new LinearLayout(this);
        debugPanel.setOrientation(LinearLayout.VERTICAL);
        container.addView(debugPanel, matchWrap());
        renderLoadingState("正在运行屏幕快照探针...");

        container.addView(sectionHeader("原始快照报告", "保留纯文本输出，方便复制和核对 JSON。"), cardParams(dp(20)));

        resultView = new TextView(this);
        resultView.setVisibility(View.GONE);
        resultView.setTextIsSelectable(true);
        resultView.setTextSize(13f);
        resultView.setTextColor(Color.parseColor("#162018"));
        resultView.setBackground(buildRoundedBackground("#FFFFFF", "#C7D2C4"));
        resultView.setPadding(dp(16), dp(16), dp(16), dp(16));
        container.addView(resultView, cardParams(dp(12)));

        scrollView.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private View createBannerCard() {
        LinearLayout card = verticalCard("#DCE8D2", "#9EBA8F");
        TextView eyebrow = new TextView(this);
        eyebrow.setText("今日");
        eyebrow.setTextSize(12f);
        eyebrow.setTextColor(Color.parseColor("#4F6A4B"));
        card.addView(eyebrow, matchWrap());

        TextView title = new TextView(this);
        title.setText("费用审批中心");
        title.setTextSize(22f);
        title.setTextColor(Color.parseColor("#17331D"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        card.addView(title, titleParams);

        TextView desc = new TextView(this);
        desc.setText("3 笔报销待审批，1 个紧急出差申请，2 张发票待确认。");
        desc.setTextSize(14f);
        desc.setTextColor(Color.parseColor("#34513D"));
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(8);
        card.addView(desc, descParams);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipsParams = matchWrap();
        chipsParams.topMargin = dp(12);
        card.addView(chips, chipsParams);
        chips.addView(chip("紧急", "#FFF2C8", "#D6C06D"), wrapWrap());
        LinearLayout.LayoutParams secondChipParams = wrapWrap();
        secondChipParams.leftMargin = dp(8);
        chips.addView(chip("3 个审批", "#EEF7EA", "#9EBA8F"), secondChipParams);
        return card;
    }

    private View createQuickActionsRow() {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scrollView.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(actionCard("差旅", "预订机票和酒店", "#F9E7D1", "#D49F62"), wrapWrap());
        row.addView(actionCard("考勤", "09:30 前打卡", "#E0EEF6", "#77A4C4"), leftMargin(dp(12)));
        row.addView(actionCard("会议室", "当前可预订 2 间", "#ECE4F7", "#A187CC"), leftMargin(dp(12)));
        row.addView(actionCard("合同", "7 份草稿待审", "#E8F0D8", "#90A95B"), leftMargin(dp(12)));
        return scrollView;
    }

    private View createTodoCard() {
        LinearLayout card = verticalCard("#FFFFFF", "#D6D2C6");
        TextView title = new TextView(this);
        title.setText("待处理事项");
        title.setTextSize(18f);
        title.setTextColor(Color.parseColor("#1D241F"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(title, matchWrap());

        card.addView(todoRow("深圳峰会差旅报销", "等待财务审批"), cardParams(dp(12)));
        card.addView(todoRow("Q2 供应商付款申请", "等待法务确认"), cardParams(dp(10)));
        card.addView(todoRow("周五考勤异常", "需要主管签字"), cardParams(dp(10)));
        return card;
    }

    private View createMetricsCard() {
        LinearLayout card = verticalCard("#1F3A2D", "#1F3A2D");
        TextView title = new TextView(this);
        title.setText("本周");
        title.setTextSize(18f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(title, matchWrap());

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsParams = matchWrap();
        metricsParams.topMargin = dp(12);
        card.addView(metrics, metricsParams);
        metrics.addView(metricBlock("12", "已处理"), weightedWrap());
        metrics.addView(metricBlock("4", "紧急"), weightedWrap());
        metrics.addView(metricBlock("98%", "准时率"), weightedWrap());
        return card;
    }
    private View todoRow(String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(buildRoundedBackground("#F7F4ED", "#E2DCCF"));
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(15f);
        title.setTextColor(Color.parseColor("#1B201C"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(13f);
        subtitle.setTextColor(Color.parseColor("#66706A"));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(4);
        row.addView(subtitle, params);
        return row;
    }

    private View actionCard(String titleText, String subtitleText, String fill, String stroke) {
        LinearLayout card = verticalCard(fill, stroke);
        card.setLayoutParams(new LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(17f);
        title.setTextColor(Color.parseColor("#1E2A24"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(13f);
        subtitle.setTextColor(Color.parseColor("#55615A"));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(6);
        card.addView(subtitle, params);
        return card;
    }

    private View metricBlock(String value, String label) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(24f);
        valueView.setTextColor(Color.WHITE);
        valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);
        block.addView(valueView, wrapWrap());

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12f);
        labelView.setTextColor(Color.parseColor("#D4E6D9"));
        LinearLayout.LayoutParams params = wrapWrap();
        params.topMargin = dp(4);
        block.addView(labelView, params);
        return block;
    }

    private void runProbe() {
        resultView.setVisibility(View.VISIBLE);
        resultView.setText("正在运行屏幕快照探针...");
        renderLoadingState("正在收集原生树、视觉结果和融合调试数据...");
        Thread worker = new Thread(() -> {
            try {
                AndroidToolManager manager = new AndroidToolManager(this);
                String resultJson = manager.callTool(
                        "android_view_context_tool",
                        new JSONObject().put("targetHint", "定位费用审批中心").toString());
                JSONObject result = new JSONObject(resultJson);
                String report = buildReport(result);
                runOnUiThread(() -> {
                    resultView.setText(report);
                    renderDebugPanel(result);
                });
            } catch (Exception e) {
                String report = "成功=false\n错误=" + e.getMessage();
                runOnUiThread(() -> {
                    resultView.setText(report);
                    renderErrorState(e.getMessage());
                });
            }
        });
        worker.start();
    }

    private void renderLoadingState(String message) {
        debugPanel.removeAllViews();
        appendDebugCard(createInfoCard(
                "等待快照",
                message,
                "#FFF8E7",
                "#D8C084",
                "#6D5826"
        ));
    }

    private void renderErrorState(String message) {
        debugPanel.removeAllViews();
        appendDebugCard(createInfoCard(
                "探针执行失败",
                message,
                "#FFF0EE",
                "#E3AAA2",
                "#7C3028"
        ));
    }

    private void renderDebugPanel(JSONObject result) {
        debugPanel.removeAllViews();
        JSONObject hybrid = result.optJSONObject("hybridObservation");
        JSONObject compact = result.optJSONObject("screenVisionCompact");
        String nativeXml = result.optString("nativeViewXml", "");
        appendDebugCard(createOverviewCard(result, hybrid, compact, nativeXml));

        if (hybrid == null) {
            appendDebugCard(createInfoCard(
                    "没有融合观测",
                    "当前结果未包含 hybridObservation，本次无法展示融合链路。",
                    "#FFF9F3",
                    "#D9C6AE",
                    "#6B5C4B"
            ));
            return;
        }

        appendDebugCard(createSelectionBiasCard(hybrid));
        JSONObject debug = hybrid.optJSONObject("debug");
        appendDebugCard(createMatchPairsCard(debug != null ? debug.optJSONArray("matchPairs") : null));
        appendDebugCard(createActionableNodesCard(hybrid.optJSONArray("actionableNodes")));
        appendDebugCard(createCandidatesCard(
                "仅原生候选",
                "高价值原生节点未匹配到视觉信号。",
                debug != null ? debug.optJSONArray("nativeOnlyCandidates") : null,
                "native"
        ));
        appendDebugCard(createCandidatesCard(
                "仅视觉候选",
                "OCR 或 UI 检测得到的候选未找到原生锚点。",
                debug != null ? debug.optJSONArray("visionOnlyCandidates") : null,
                "vision"
        ));
        appendDebugCard(createRegionsCard("视觉分区", "按重要度排序的视觉分区及其回映的原生节点。", hybrid.optJSONArray("sections"), true));
        appendDebugCard(createRegionsCard("视觉列表项", "列表切分结果及对应的原生锚点。", hybrid.optJSONArray("listItems"), false));
        appendDebugCard(createConflictsCard(hybrid.optJSONArray("conflicts")));
        appendDebugCard(createRawSignalCard(compact, nativeXml, debug));
    }

    private View createOverviewCard(JSONObject result,
                                    JSONObject hybrid,
                                    JSONObject compact,
                                    String nativeXml) {
        LinearLayout card = debugCard("融合快照", "汇总本次来源选择、融合质量和可用证据。");
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(chipRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chipRow.addView(metricChip("来源", result.optString("source", "<无>"), "#E8F0D8", "#90A95B", "#2D4023"), wrapWrap());
        chipRow.addView(metricChip("模式", hybrid != null ? hybrid.optString("mode", "<无>") : "<无>", "#E0EEF6", "#77A4C4", "#22465D"), leftMargin(dp(8)));
        chipRow.addView(metricChip("观测", result.optString("observationMode", "<无>"), "#F8E8D8", "#D29C61", "#5C3B18"), leftMargin(dp(8)));
        chipRow.addView(metricChip("快照", result.optString("snapshotId", "<无>"), "#EEE8FB", "#A28CD6", "#49346D"), leftMargin(dp(8)));
        card.addView(scroll, cardParams(dp(12)));

        appendValueLine(card, "摘要", hybrid != null ? hybrid.optString("summary", "<无>") : "<无>");
        appendValueLine(card, "页面", result.optString("activityClassName", "<无>"));
        appendValueLine(card, "截图尺寸", result.optInt("screenSnapshotWidth", -1) + " x " + result.optInt("screenSnapshotHeight", -1));

        JSONObject quality = hybrid != null ? hybrid.optJSONObject("quality") : null;
        HorizontalScrollView qualityScroll = new HorizontalScrollView(this);
        qualityScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout qualityRow = new LinearLayout(this);
        qualityRow.setOrientation(LinearLayout.HORIZONTAL);
        qualityScroll.addView(qualityRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        qualityRow.addView(metricChip("融合配对", quality != null ? String.valueOf(quality.optInt("fusedMatchCount", 0)) : "0", "#DCEED7", "#8DB17C", "#24441E"), wrapWrap());
        qualityRow.addView(metricChip("可执行", hybrid != null ? String.valueOf(lengthOf(hybrid.optJSONArray("actionableNodes"))) : "0", "#FFF3D7", "#D6B55C", "#6B4B00"), leftMargin(dp(8)));
        qualityRow.addView(metricChip("冲突", hybrid != null ? String.valueOf(lengthOf(hybrid.optJSONArray("conflicts"))) : "0", "#FBE4E0", "#D69B93", "#6D2E25"), leftMargin(dp(8)));
        qualityRow.addView(metricChip("视觉控件", quality != null ? String.valueOf(quality.optInt("visionControlCount", 0)) : String.valueOf(compact != null ? lengthOf(compact.optJSONArray("controls")) : 0), "#E4ECFA", "#8FA9D8", "#2A426B"), leftMargin(dp(8)));
        qualityRow.addView(metricChip("原生树", nativeXml.isEmpty() ? "0" : String.valueOf(nativeXml.length()), "#F1EEE7", "#B6AA92", "#5E5548"), leftMargin(dp(8)));
        card.addView(qualityScroll, cardParams(dp(12)));
        return card;
    }

    private View createSelectionBiasCard(JSONObject hybrid) {
        LinearLayout card = debugCard("目标选择偏向", "解释解析器如何利用融合结果选择最终目标。");
        appendValueLine(card, "执行提示", hybrid.optString("executionHint", "优先使用融合节点，其次使用原生节点。"));
        appendValueLine(card, "解析策略", "解析器会优先加权 fused，其次是 native，vision_only 只作为较弱回退。");
        return card;
    }
    private View createMatchPairsCard(JSONArray matchPairs) {
        LinearLayout card = debugCard("融合配对", "展示经过 bbox 与文本打分后成功配对的原生节点和视觉信号。");
        if (matchPairs == null || matchPairs.length() == 0) {
            appendEmptyState(card, "本次没有生成原生/视觉配对。");
            return card;
        }
        for (int i = 0; i < matchPairs.length(); i++) {
            JSONObject pair = matchPairs.optJSONObject(i);
            if (pair == null) {
                continue;
            }
            LinearLayout item = detailItem("#F5F8F0", "#C8D7BF");
            item.addView(headerRow(
                    sourceBadge("fused"),
                    strongText(firstNonEmpty(pair.optString("nativeText", null), pair.optString("visionLabel", null), "<未命名配对>"))
            ), matchWrap());
            appendInlineText(item, "原生", "#" + pair.optInt("nativeNodeIndex", -1) + " · " + safeText(pair.optString("nativeClassName", null)) + " · " + safeText(pair.optString("nativeResourceId", null)));
            appendInlineText(item, "视觉", safeText(pair.optString("visionKind", null)) + " / " + safeText(pair.optString("visionType", null)) + " / " + safeText(pair.optString("visionLabel", null)));
            appendInlineText(item, "分数", "匹配=" + formatDouble(pair.optDouble("matchScore", 0d)) + " | 原生=" + formatDouble(pair.optDouble("nativeScore", 0d)) + " | 视觉=" + formatDouble(pair.optDouble("visionScore", 0d)));
            appendInlineText(item, "边界", safeText(pair.optString("bounds", null)));
            card.addView(item, cardParams(i == 0 ? dp(12) : dp(10)));
        }
        return card;
    }

    private View createActionableNodesCard(JSONArray actionableNodes) {
        LinearLayout card = debugCard("可执行节点", "最终提供给模型和解析器优先消费的候选。");
        if (actionableNodes == null || actionableNodes.length() == 0) {
            appendEmptyState(card, "没有生成可执行节点。");
            return card;
        }
        for (int i = 0; i < actionableNodes.length(); i++) {
            JSONObject node = actionableNodes.optJSONObject(i);
            if (node == null) {
                continue;
            }
            LinearLayout item = detailItem("#FBF9F4", "#DDD3C2");
            item.addView(headerRow(
                    sourceBadge(node.optString("source", "unknown")),
                    strongText(firstNonEmpty(node.optString("text", null), node.optString("visionLabel", null), node.optString("resourceId", null), "<未命名候选>"))
            ), matchWrap());
            appendInlineText(item, "得分", formatDouble(node.optDouble("score", 0d)) + " · " + safeText(node.optString("actionability", null)));
            appendInlineText(item, "类型", safeText(node.optString("className", null)) + " | 视觉=" + safeText(node.optString("visionType", null)));
            appendInlineText(item, "资源ID", safeText(node.optString("resourceId", null)));
            appendInlineText(item, "边界", safeText(node.optString("bounds", null)));
            card.addView(item, cardParams(i == 0 ? dp(12) : dp(10)));
        }
        return card;
    }

    private View createCandidatesCard(String title,
                                      String subtitle,
                                      JSONArray candidates,
                                      String kind) {
        LinearLayout card = debugCard(title, subtitle);
        if (candidates == null || candidates.length() == 0) {
            appendEmptyState(card, "当前快照没有这一类候选。");
            return card;
        }
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) {
                continue;
            }
            LinearLayout item = detailItem("#F7F6F2", "#D8D0C2");
            item.addView(headerRow(
                    sourceBadge("native".equals(kind) ? "native" : "vision_only"),
                    strongText(firstNonEmpty(candidate.optString("text", null), candidate.optString("visionType", null), candidate.optString("className", null), candidate.optString("resourceId", null), "<未命名候选>"))
            ), matchWrap());
            appendInlineText(item, "得分", formatDouble(candidate.optDouble("score", 0d)) + " · " + safeText(candidate.optString("actionability", null)));
            if ("native".equals(kind)) {
                appendInlineText(item, "原生", "#" + candidate.optInt("nativeNodeIndex", -1) + " · " + safeText(candidate.optString("className", null)) + " · " + safeText(candidate.optString("resourceId", null)));
            } else {
                appendInlineText(item, "视觉", safeText(candidate.optString("visionType", null)) + " | 角色=" + safeText(candidate.optString("visionRole", null)));
            }
            appendInlineText(item, "边界", safeText(candidate.optString("bounds", null)));
            card.addView(item, cardParams(i == 0 ? dp(12) : dp(10)));
        }
        return card;
    }

    private View createRegionsCard(String title,
                                   String subtitle,
                                   JSONArray array,
                                   boolean section) {
        LinearLayout card = debugCard(title, subtitle);
        if (array == null || array.length() == 0) {
            appendEmptyState(card, "当前没有区域映射数据。");
            return card;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject itemJson = array.optJSONObject(i);
            if (itemJson == null) {
                continue;
            }
            LinearLayout item = detailItem("#F2F7FA", "#C1D2DC");
            item.addView(headerRow(
                    sourceBadge(section ? "section" : "item"),
                    strongText(firstNonEmpty(itemJson.optString("summaryText", null), itemJson.optString("type", null), itemJson.optString("id", null), "<未命名区域>"))
            ), matchWrap());
            appendInlineText(item, "重要度", formatDouble(itemJson.optDouble("importance", 0d)) + " | 匹配原生=" + itemJson.optInt("matchedNativeNodeCount", 0));
            if (section) {
                appendInlineText(item, "分区", safeText(itemJson.optString("type", null)) + " | 折叠项=" + itemJson.optInt("collapsedItemCount", 0));
            } else {
                appendInlineText(item, "列表项", safeText(itemJson.optString("type", null)) + " | 文本=" + itemJson.optInt("textCount", 0) + " | 控件=" + itemJson.optInt("controlCount", 0));
            }
            appendInlineText(item, "原生ID", joinArray(itemJson.optJSONArray("matchedNativeNodeIds"), 6));
            appendInlineText(item, "边界", safeText(itemJson.optString("bounds", null)));
            card.addView(item, cardParams(i == 0 ? dp(12) : dp(10)));
        }
        return card;
    }

    private View createConflictsCard(JSONArray conflicts) {
        LinearLayout card = debugCard("冲突提示", "融合后仍值得关注的分歧或风险。");
        if (conflicts == null || conflicts.length() == 0) {
            appendEmptyState(card, "当前快照没有冲突项。");
            return card;
        }
        for (int i = 0; i < conflicts.length(); i++) {
            JSONObject conflict = conflicts.optJSONObject(i);
            if (conflict == null) {
                continue;
            }
            LinearLayout item = detailItem("#FFF6F2", "#E1B5AC");
            item.addView(headerRow(
                    severityBadge(conflict.optString("severity", "info")),
                    strongText(firstNonEmpty(conflict.optString("code", null), "<未知冲突>"))
            ), matchWrap());
            appendInlineText(item, "说明", safeText(conflict.optString("message", null)));
            appendInlineText(item, "边界", safeText(conflict.optString("bounds", null)));
            if (conflict.has("nativeNodeIndex") && !conflict.isNull("nativeNodeIndex")) {
                appendInlineText(item, "原生", "#" + conflict.optInt("nativeNodeIndex", -1));
            }
            card.addView(item, cardParams(i == 0 ? dp(12) : dp(10)));
        }
        return card;
    }

    private View createRawSignalCard(JSONObject compact,
                                     String nativeXml,
                                     JSONObject debug) {
        LinearLayout card = debugCard("原始信号摘要", "展示视觉 compact 统计与原生树概览，便于对照检查。");
        if (compact != null) {
            appendValueLine(card, "视觉摘要", compact.optString("summary", "<无>"));
            appendValueLine(card, "视觉计数", "分区=" + lengthOf(compact.optJSONArray("sections"))
                    + " | 列表项=" + lengthOf(compact.optJSONArray("items"))
                    + " | 文本=" + lengthOf(compact.optJSONArray("texts"))
                    + " | 控件=" + lengthOf(compact.optJSONArray("controls")));
        } else {
            appendValueLine(card, "视觉摘要", "<无>");
        }
        appendValueLine(card, "原生树", nativeXml.isEmpty() ? "缺失" : "长度=" + nativeXml.length());
        appendValueLine(card, "原生文本", debug != null ? joinArray(debug.optJSONArray("topNativeTexts"), 8) : "<无>");
        return card;
    }
    private View createInfoCard(String title,
                                String message,
                                String fill,
                                String stroke,
                                String textColor) {
        LinearLayout card = verticalCard(fill, stroke);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(18f);
        titleView.setTextColor(Color.parseColor(textColor));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(titleView, matchWrap());

        TextView body = new TextView(this);
        body.setText(message);
        body.setTextSize(13f);
        body.setTextColor(Color.parseColor(textColor));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        card.addView(body, params);
        return card;
    }

    private LinearLayout debugCard(String title, String subtitle) {
        LinearLayout card = verticalCard("#FFFFFF", "#C9D1C2");
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(18f);
        titleView.setTextColor(Color.parseColor("#18231A"));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(titleView, matchWrap());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13f);
        subtitleView.setTextColor(Color.parseColor("#5B665D"));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(6);
        card.addView(subtitleView, params);
        return card;
    }

    private LinearLayout detailItem(String fill, String stroke) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(buildRoundedBackground(fill, stroke));
        item.setPadding(dp(14), dp(14), dp(14), dp(14));
        return item;
    }

    private View sectionHeader(String title, String subtitle) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(20f);
        titleView.setTextColor(Color.parseColor("#1A241B"));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        wrapper.addView(titleView, matchWrap());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13f);
        subtitleView.setTextColor(Color.parseColor("#6A726C"));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(4);
        wrapper.addView(subtitleView, params);
        return wrapper;
    }

    private LinearLayout headerRow(View leading, TextView title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(leading, wrapWrap());
        LinearLayout.LayoutParams titleParams = wrapWrap();
        titleParams.leftMargin = dp(10);
        row.addView(title, titleParams);
        return row;
    }

    private TextView strongText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15f);
        view.setTextColor(Color.parseColor("#1C251F"));
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private void appendValueLine(LinearLayout card, String label, String value) {
        appendInlineText(card, label, value);
    }

    private void appendInlineText(LinearLayout card, String label, String value) {
        TextView line = new TextView(this);
        line.setText(label + "：" + safeText(value));
        line.setTextSize(13f);
        line.setTextColor(Color.parseColor("#37433B"));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        card.addView(line, params);
    }

    private void appendEmptyState(LinearLayout card, String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextSize(13f);
        empty.setTextColor(Color.parseColor("#6B756E"));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        card.addView(empty, params);
    }

    private void appendDebugCard(View view) {
        debugPanel.addView(view, cardParams(debugPanel.getChildCount() == 0 ? 0 : dp(12)));
    }

    private TextView sourceBadge(String source) {
        if ("fused".equals(source)) {
            return badge("融合", "#DCEED7", "#8DB17C", "#2E4A27");
        }
        if ("native".equals(source)) {
            return badge("原生", "#FFF2D9", "#D8B366", "#6D4E0D");
        }
        if ("vision_only".equals(source)) {
            return badge("视觉", "#E2ECFA", "#8FA9D8", "#28446E");
        }
        if ("section".equals(source)) {
            return badge("分区", "#EAF3E2", "#A8C28B", "#345520");
        }
        if ("item".equals(source)) {
            return badge("列表", "#F3EAF8", "#B39ACC", "#4F356B");
        }
        return badge(source.toUpperCase(), "#EFECE4", "#BDB4A2", "#5C5448");
    }

    private TextView severityBadge(String severity) {
        if ("warning".equals(severity)) {
            return badge("警告", "#FDE8E3", "#D69B93", "#7A3128");
        }
        return badge("信息", "#EAF0F6", "#9CB5CF", "#314A64");
    }

    private TextView metricChip(String label,
                                String value,
                                String fill,
                                String stroke,
                                String textColor) {
        return badge(label + " · " + safeText(value), fill, stroke, textColor);
    }

    private TextView badge(String text,
                           String fill,
                           String stroke,
                           String textColor) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextSize(12f);
        badge.setTextColor(Color.parseColor(textColor));
        badge.setBackground(buildRoundedBackground(fill, stroke));
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        return badge;
    }

    private TextView chip(String text, String fill, String stroke) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13f);
        chip.setTextColor(Color.parseColor("#2B332F"));
        chip.setBackground(buildRoundedBackground(fill, stroke));
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        return chip;
    }

    private LinearLayout verticalCard(String fill, String stroke) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(buildRoundedBackground(fill, stroke));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        return card;
    }

    private GradientDrawable buildRoundedBackground(String fill, String stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.parseColor(stroke));
        return drawable;
    }
    private String buildReport(JSONObject result) throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("成功=").append(result.optBoolean("success", false)).append('\n');
        report.append("预期来源=")
                .append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "screen_snapshot" : "native_xml")
                .append('\n');
        report.append("实际来源=").append(result.optString("source", "<无>")).append('\n');
        report.append("来源判定=").append(result.optString("selectionStatus", "<无>")).append('\n');
        report.append("页面类名=").append(result.optString("activityClassName", "<无>")).append('\n');
        report.append("观测模式=").append(result.optString("observationMode", "<无>")).append('\n');
        report.append("快照ID=").append(result.optString("snapshotId", "<无>")).append('\n');
        report.append("截图引用=").append(result.optString("screenSnapshot", "<无>")).append('\n');
        report.append("截图宽度=").append(result.optInt("screenSnapshotWidth", -1)).append('\n');
        report.append("截图高度=").append(result.optInt("screenSnapshotHeight", -1)).append('\n');
        if (result.has("hybridObservation") && !result.isNull("hybridObservation")) {
            JSONObject hybrid = result.getJSONObject("hybridObservation");
            report.append("融合模式=").append(hybrid.optString("mode", "<无>")).append('\n');
            report.append("融合摘要=").append(hybrid.optString("summary", "<无>")).append('\n');
            report.append("可执行节点数=").append(lengthOf(hybrid.optJSONArray("actionableNodes"))).append('\n');
            report.append("分区数=").append(lengthOf(hybrid.optJSONArray("sections"))).append('\n');
            report.append("列表项数=").append(lengthOf(hybrid.optJSONArray("listItems"))).append('\n');
            report.append("冲突数=").append(lengthOf(hybrid.optJSONArray("conflicts"))).append('\n');
            JSONObject quality = hybrid.optJSONObject("quality");
            if (quality != null) {
                report.append("融合配对数=").append(quality.optInt("fusedMatchCount", -1)).append('\n');
            }
            JSONArray actionableNodes = hybrid.optJSONArray("actionableNodes");
            if (actionableNodes != null && actionableNodes.length() > 0) {
                JSONObject firstAction = actionableNodes.optJSONObject(0);
                if (firstAction != null) {
                    report.append("第一候选来源=").append(firstAction.optString("source", "<无>")).append('\n');
                    report.append("第一候选文本=").append(firstAction.optString("text", "<无>")).append('\n');
                    report.append("第一候选视觉类型=").append(firstAction.optString("visionType", "<无>")).append('\n');
                }
            }
        }
        if (result.has("screenVisionCompact") && !result.isNull("screenVisionCompact")) {
            JSONObject compact = result.getJSONObject("screenVisionCompact");
            report.append("视觉摘要=").append(compact.optString("summary", "<无>")).append('\n');
            report.append("视觉分区数=").append(lengthOf(compact.optJSONArray("sections"))).append('\n');
            report.append("视觉列表项数=").append(lengthOf(compact.optJSONArray("items"))).append('\n');
            report.append("视觉文本数=").append(lengthOf(compact.optJSONArray("texts"))).append('\n');
            report.append("视觉控件数=").append(lengthOf(compact.optJSONArray("controls"))).append('\n');
        }
        if (result.has("nativeViewXml") && !result.isNull("nativeViewXml")) {
            String nativeXml = result.optString("nativeViewXml", "");
            report.append("原生树长度=").append(nativeXml.length()).append('\n');
        }
        return report.toString();
    }

    private String joinArray(JSONArray array, int maxItems) {
        if (array == null || array.length() == 0) {
            return "<无>";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(maxItems, array.length());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Object value = array.opt(i);
            builder.append(value == null ? "<空>" : String.valueOf(value));
        }
        if (array.length() > limit) {
            builder.append(" … +").append(array.length() - limit);
        }
        return builder.toString();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeText(String value) {
        String text = firstNonEmpty(value);
        return text != null ? text : "<无>";
    }

    private String formatDouble(double value) {
        return String.valueOf(Math.round(value * 1000d) / 1000d);
    }

    private int lengthOf(JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedWrap() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams cardParams(int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = topMargin;
        return params;
    }

    private LinearLayout.LayoutParams leftMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
