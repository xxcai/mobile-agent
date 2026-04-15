#include "icraw/core/agent_loop.hpp"
#include "icraw/tools/tool_registry.hpp"
#include "icraw/core/token_utils.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"

#include <chrono>

namespace icraw {
namespace {

std::string truncate_tool_runner_text(const std::string& text, size_t max_chars) {
    if (text.size() <= max_chars) {
        return text;
    }
    if (max_chars <= 3) {
        return text.substr(0, max_chars);
    }
    return text.substr(0, max_chars - 3) + "...";
}

} // namespace
std::vector<ContentBlock> AgentLoop::handle_tool_calls(const std::vector<ToolCall>& tool_calls) {
    std::vector<ContentBlock> results;

    for (const auto& tc : tool_calls) {
        // Debug: Log raw tool call details before execution
        ICRAW_LOG_DEBUG("[AgentLoop][tool_call_debug] stage=execute tool_name={} tool_id={} arguments_type={} arguments_preview={}",
            tc.name, tc.id,
            tc.arguments.is_string() ? "string" : (tc.arguments.is_object() ? "object" : (tc.arguments.is_null() ? "null" : "other")),
            log_utils::truncate_for_debug(tc.arguments.dump()));

        const auto tool_start_time = std::chrono::steady_clock::now();
        std::string result = tool_registry_->execute_tool(tc.name, tc.arguments);
        const auto tool_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - tool_start_time).count();

        if (tc.name == "android_view_context_tool") {
            const std::string detail_mode = tc.arguments.is_object()
                    ? tc.arguments.value("__detailMode", "")
                    : "";
            const std::string target_hint = tc.arguments.is_object()
                    ? tc.arguments.value("targetHint", "")
                    : "";
            ICRAW_LOG_INFO(
                    "[AgentLoop][view_context_duration_ms] tool_id={} duration_ms={} detail_mode={} target_hint={}",
                    tc.id,
                    tool_duration_ms,
                    detail_mode.empty() ? "default" : detail_mode,
                    truncate_tool_runner_text(target_hint, 80));
        } else if (tc.name == "android_gesture_tool") {
            const std::string action = tc.arguments.is_object()
                    ? tc.arguments.value("action", "")
                    : "";
            ICRAW_LOG_INFO(
                    "[AgentLoop][gesture_duration_ms] tool_id={} duration_ms={} action={}",
                    tc.id,
                    tool_duration_ms,
                    action);
        } else {
            ICRAW_LOG_INFO(
                    "[AgentLoop][tool_execution_duration_ms] tool_name={} tool_id={} duration_ms={}",
                    tc.name,
                    tc.id,
                    tool_duration_ms);
        }

        std::string pruned_result = result;
        if (tc.name != "android_view_context_tool" && result.size() > 40000) {
            pruned_result = prune_tool_result(result, 40000);
        }
        if (pruned_result.size() != result.size()) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][tool_result_pruned] tool_name={} tool_id={} pruned=true original_length={} pruned_length={}",
                    tc.name, tc.id, result.size(), pruned_result.size());
        }

        results.push_back(ContentBlock::make_tool_result(tc.id, pruned_result));
    }

    return results;
}


} // namespace icraw

