package com.hh.agent.android.routing;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DeterministicFeasibilityRouter {

    private static final Pattern STABLE_SEND_TARGET_PATTERN =
            Pattern.compile("(给|告诉)\\s*([\\p{L}\\p{N}_-]{1,20})\\s*(发|发送|说)");

    private DeterministicFeasibilityRouter() {
    }

    static BusinessPathFeasibilityDecision route(String userRequest,
                                                 Map<String, ToolExecutor> tools) {
        String normalized = normalize(userRequest);
        if (normalized.isEmpty()) {
            return BusinessPathFeasibilityDecision.directUi("empty_request_defaults_to_ui_path");
        }

        if (containsAmbiguousCurrentContext(normalized)) {
            return BusinessPathFeasibilityDecision.directUi("ambiguous_current_page_target_defaults_to_ui_path");
        }

        if (looksLikeUiElementTask(normalized)) {
            return BusinessPathFeasibilityDecision.directUi("page_element_task_prefers_view_context");
        }

        if (tools == null || tools.isEmpty()) {
            return BusinessPathFeasibilityDecision.directUi("no_registered_business_tools");
        }

        if (matchesSendMessage(normalized, tools)) {
            return BusinessPathFeasibilityDecision.businessFirst(
                    "send_im_message",
                    FeasibilityFallbackMode.UI_FALLBACK_ON_STRUCTURED_FAILURE,
                    "stable_business_entity_matches_send_im_message");
        }

        if (matchesSearchContacts(normalized, tools)) {
            return BusinessPathFeasibilityDecision.businessFirst(
                    "search_contacts",
                    FeasibilityFallbackMode.NO_UI_FALLBACK,
                    "contact_lookup_maps_to_search_contacts");
        }

        if (matchesReadClipboard(normalized, tools)) {
            return BusinessPathFeasibilityDecision.businessFirst(
                    "read_clipboard",
                    FeasibilityFallbackMode.NO_UI_FALLBACK,
                    "clipboard_intent_maps_to_read_clipboard");
        }

        if (matchesDisplayNotification(normalized, tools)) {
            return BusinessPathFeasibilityDecision.businessFirst(
                    "display_notification",
                    FeasibilityFallbackMode.NO_UI_FALLBACK,
                    "notification_intent_maps_to_display_notification");
        }

        return BusinessPathFeasibilityDecision.directUi("no_stable_business_path_detected");
    }

    private static boolean matchesSendMessage(String request, Map<String, ToolExecutor> tools) {
        if (!hasTool(tools, "send_im_message")) {
            return false;
        }
        if (!containsAny(request, "发消息", "发送消息", "发送", "发一条", "发个", "告诉")) {
            return false;
        }
        if (!hasStableSendTarget(request)) {
            return false;
        }
        return metadataSuggestsIntent(tools.get("send_im_message"), request, "消息", "联系人", "会话");
    }

    private static boolean matchesSearchContacts(String request, Map<String, ToolExecutor> tools) {
        if (!hasTool(tools, "search_contacts")) {
            return false;
        }
        if (!containsAny(request, "查找", "搜索", "找一下", "联系人")) {
            return false;
        }
        return metadataSuggestsIntent(tools.get("search_contacts"), request, "联系人", "查找", "搜索");
    }

    private static boolean matchesReadClipboard(String request, Map<String, ToolExecutor> tools) {
        if (!hasTool(tools, "read_clipboard")) {
            return false;
        }
        if (!containsAny(request, "剪贴板", "复制的内容", "粘贴板")) {
            return false;
        }
        return metadataSuggestsIntent(tools.get("read_clipboard"), request, "剪贴板", "复制");
    }

    private static boolean matchesDisplayNotification(String request, Map<String, ToolExecutor> tools) {
        if (!hasTool(tools, "display_notification")) {
            return false;
        }
        if (!containsAny(request, "通知", "提醒")) {
            return false;
        }
        return metadataSuggestsIntent(tools.get("display_notification"), request, "通知", "提醒");
    }

    private static boolean hasTool(Map<String, ToolExecutor> tools, String name) {
        return tools.containsKey(name) && tools.get(name) != null;
    }

    private static boolean containsAmbiguousCurrentContext(String request) {
        return containsAny(request,
                "当前聊天",
                "这个聊天",
                "当前页面",
                "这个页面",
                "当前会话",
                "这里");
    }

    private static boolean looksLikeUiElementTask(String request) {
        if (containsAny(request, "点击", "点一下", "点开", "长按", "滑动", "上滑", "下滑")) {
            return true;
        }
        if (containsAny(request,
                "按钮",
                "卡片",
                "红点",
                "输入框",
                "列表",
                "界面",
                "图标",
                "右上角",
                "左上角",
                "第二个",
                "第三个")) {
            return true;
        }
        return request.contains("第") && request.contains("个");
    }

    private static boolean hasStableSendTarget(String request) {
        Matcher matcher = STABLE_SEND_TARGET_PATTERN.matcher(request);
        if (!matcher.find()) {
            return false;
        }
        String candidate = matcher.group(2);
        if (candidate == null) {
            return false;
        }
        String normalized = normalize(candidate);
        return !normalized.isEmpty() && !containsAmbiguousCurrentContext(normalized);
    }

    private static boolean metadataSuggestsIntent(ToolExecutor executor,
                                                  String request,
                                                  String... keywords) {
        if (executor == null) {
            return false;
        }
        ToolDefinition definition = executor.getDefinition();
        if (definition == null) {
            return false;
        }

        StringBuilder corpus = new StringBuilder();
        corpus.append(normalize(definition.getTitle())).append(' ');
        corpus.append(normalize(definition.getDescription())).append(' ');
        for (String example : definition.getIntentExamples()) {
            corpus.append(normalize(example)).append(' ');
        }

        String normalizedCorpus = corpus.toString();
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (!normalizedKeyword.isEmpty()
                    && normalizedCorpus.contains(normalizedKeyword)
                    && request.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String request, String... tokens) {
        for (String token : tokens) {
            if (request.contains(normalize(token))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
