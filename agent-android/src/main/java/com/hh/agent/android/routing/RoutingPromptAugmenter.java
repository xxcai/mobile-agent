package com.hh.agent.android.routing;

public final class RoutingPromptAugmenter {

    public static final String ROUTING_CONTEXT_START = "<routing_context>";
    public static final String ROUTING_CONTEXT_END = "</routing_context>";
    private static final String ORIGINAL_REQUEST_LABEL = "Original user request:";

    private RoutingPromptAugmenter() {
    }

    public static String augment(String userRequest, BusinessPathFeasibilityDecision decision) {
        if (userRequest == null) {
            return "";
        }
        if (decision == null) {
            return userRequest;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(ROUTING_CONTEXT_START).append('\n');
        builder.append("business_path_feasible=").append(decision.isBusinessPathFeasible()).append('\n');
        builder.append("entry_function=").append(decision.getEntryFunction()).append('\n');
        builder.append("fallback_mode=").append(decision.getFallbackMode().getWireValue()).append('\n');
        builder.append("reason=").append(decision.getReason()).append('\n');
        builder.append("routing_policy=").append(buildPolicyLine(decision)).append('\n');
        builder.append(ROUTING_CONTEXT_END).append('\n');
        builder.append(ORIGINAL_REQUEST_LABEL).append('\n');
        builder.append(userRequest);
        return builder.toString();
    }

    public static String strip(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        int start = content.indexOf(ROUTING_CONTEXT_START);
        int end = content.indexOf(ROUTING_CONTEXT_END);
        if (start < 0 || end < start) {
            return content;
        }
        int bodyStart = end + ROUTING_CONTEXT_END.length();
        String remaining = content.substring(bodyStart).trim();
        if (remaining.startsWith(ORIGINAL_REQUEST_LABEL)) {
            remaining = remaining.substring(ORIGINAL_REQUEST_LABEL.length()).trim();
        }
        return remaining;
    }

    private static String buildPolicyLine(BusinessPathFeasibilityDecision decision) {
        if (!decision.isBusinessPathFeasible()) {
            return "Do not try call_android_tool first. Start with android_view_context_tool, then android_gesture_tool if needed.";
        }
        if (decision.getFallbackMode() == FeasibilityFallbackMode.UI_FALLBACK_ON_STRUCTURED_FAILURE) {
            return "Try call_android_tool with entry_function first. Only switch to android_view_context_tool after business_target_not_accessible or business_capability_not_supported.";
        }
        return "Try call_android_tool with entry_function first. Do not switch to UI fallback unless the route policy changes.";
    }
}
