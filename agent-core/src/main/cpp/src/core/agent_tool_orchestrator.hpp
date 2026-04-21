#pragma once

// Internal AgentLoop implementation chunk. Included only by agent_loop.cpp.
// It owns the glue between model-produced tool calls, local tool execution,
// ExecutionState updates, stream callbacks, and post-observation fast execute.

using ToolExecutionFn = std::function<std::vector<ContentBlock>(const std::vector<ToolCall>&)>;

struct FastExecuteAttempt {
    bool attempted = false;
    bool executed = false;
};

void emit_tool_use_event_if_needed(const AgentEventCallback& callback, const ToolCall& tool_call) {
    if (!callback) {
        return;
    }
    AgentEvent tool_use_event;
    tool_use_event.type = "tool_use";
    tool_use_event.data["id"] = tool_call.id;
    tool_use_event.data["name"] = tool_call.name;
    tool_use_event.data["input"] = tool_call.arguments;
    callback(tool_use_event);
}

void emit_tool_result_event_if_needed(const AgentEventCallback& callback, const ContentBlock& result) {
    if (!callback) {
        return;
    }
    AgentEvent tool_result_event;
    tool_result_event.type = "tool_result";
    tool_result_event.data["tool_use_id"] = result.tool_use_id;
    tool_result_event.data["content"] = result.content;
    callback(tool_result_event);
}

Message build_tool_result_message(const ContentBlock& result) {
    Message tool_msg;
    tool_msg.role = "tool";
    tool_msg.tool_call_id = result.tool_use_id;
    tool_msg.content.push_back(ContentBlock::make_tool_result(result.tool_use_id, result.content));
    return tool_msg;
}

void log_stream_tool_result_summary_if_needed(const AgentEventCallback& callback, const ContentBlock& result) {
    if (!callback) {
        return;
    }

    try {
        auto result_json = nlohmann::json::parse(result.content);
        if (result_json.value("success", false)) {
            ICRAW_LOG_INFO("[AgentLoop][tool_call_execute_complete] tool_id={} result_success=true bytes_written={}",
                    result.tool_use_id, result_json.value("bytes_written", 0));
        } else {
            ICRAW_LOG_WARN("[AgentLoop][tool_call_execute_failed] tool_id={} error={}",
                    result.tool_use_id, result_json.value("error", "unknown error"));
        }
    } catch (...) {
        ICRAW_LOG_DEBUG("[AgentLoop][tool_call_debug] stage=result tool_id={} result_length={}",
                result.tool_use_id, result.content.size());
    }
}

void append_tool_result_and_update_state(std::vector<Message>& new_messages,
                                         ChatCompletionRequest& request,
                                         ExecutionState& execution_state,
                                         const ContentBlock& result,
                                         const ToolCall* executed_tool_call,
                                         const std::string& tool_name,
                                         const AgentEventCallback& callback) {
    Message tool_msg = build_tool_result_message(result);
    new_messages.push_back(tool_msg);
    request.messages.push_back(std::move(tool_msg));
    update_execution_state_with_tool_result(
            execution_state, executed_tool_call, tool_name, result.content);
    emit_tool_result_event_if_needed(callback, result);
}

void reset_context_for_readout_if_needed(ChatCompletionRequest& request,
                                         ExecutionState& execution_state,
                                         const std::string& mode) {
    if (!execution_state.goal_reached || execution_state.context_reset) {
        return;
    }

    reset_messages_for_readout(request.messages, execution_state);
    execution_state.context_reset = true;
    ICRAW_LOG_INFO(
            "[AgentLoop][goal_reached_context_reset] mode={} phase={} current_page={}",
            mode,
            execution_state.phase,
            execution_state.current_page);
}

FastExecuteAttempt maybe_run_fast_execute_from_observation(ChatCompletionRequest& request,
                                                           std::vector<Message>& new_messages,
                                                           ExecutionState& execution_state,
                                                           const std::string& observation_content,
                                                           int iteration,
                                                           const std::string& mode,
                                                           const ToolExecutionFn& execute_tools,
                                                           const AgentEventCallback& callback,
                                                           const std::string& phase_name = "fast_execute") {
    FastExecuteAttempt attempt;
    auto fast_tool_call = maybe_build_fast_execute_tool_call(execution_state, observation_content);
    if (!fast_tool_call.has_value()) {
        return attempt;
    }

    attempt.attempted = true;
    Message fast_assistant = build_assistant_tool_call_message(*fast_tool_call);
    new_messages.push_back(fast_assistant);
    request.messages.push_back(fast_assistant);
    emit_tool_use_event_if_needed(callback, *fast_tool_call);

    std::vector<ToolCall> fast_calls{*fast_tool_call};
    auto fast_phase_start = std::chrono::steady_clock::now();
    auto fast_results = execute_tools(fast_calls);
    auto fast_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - fast_phase_start).count();
    ICRAW_LOG_INFO(
            "[AgentLoop][tool_phase_duration_ms] mode={} iteration={} phase={} tool_call_count={} duration_ms={}",
            mode,
            iteration,
            phase_name,
            fast_calls.size(),
            fast_phase_duration_ms);

    for (const auto& fast_result : fast_results) {
        log_stream_tool_result_summary_if_needed(callback, fast_result);
        append_tool_result_and_update_state(
                new_messages,
                request,
                execution_state,
                fast_result,
                &(*fast_tool_call),
                fast_tool_call->name,
                callback);
    }
    attempt.executed = !fast_results.empty();
    return attempt;
}

size_t execute_tool_calls_and_maybe_fast_execute(ChatCompletionRequest& request,
                                                 std::vector<Message>& new_messages,
                                                 ExecutionState& execution_state,
                                                 const std::vector<ToolCall>& tool_calls,
                                                 int iteration,
                                                 const std::string& mode,
                                                 const ToolExecutionFn& execute_tools,
                                                 const AgentEventCallback& callback = AgentEventCallback{}) {
    if (callback) {
        ICRAW_LOG_INFO("[AgentLoop][tool_call_execute_start] tool_call_count={}", tool_calls.size());
    }

    auto tool_phase_start = std::chrono::steady_clock::now();
    auto tool_calls_for_execution = enrich_tool_calls_for_execution(
            tool_calls, execution_state, request.messages);
    auto tool_results = execute_tools(tool_calls_for_execution);
    auto tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - tool_phase_start).count();
    ICRAW_LOG_INFO(
            "[AgentLoop][tool_phase_duration_ms] mode={} iteration={} phase=primary tool_call_count={} duration_ms={}",
            mode,
            iteration,
            tool_calls_for_execution.size(),
            tool_phase_duration_ms);

    for (size_t i = 0; i < tool_results.size(); ++i) {
        const std::string tool_name = i < tool_calls_for_execution.size()
                ? tool_calls_for_execution[i].name
                : "tool";
        const ToolCall* executed_tool_call = i < tool_calls_for_execution.size()
                ? &tool_calls_for_execution[i]
                : nullptr;
        log_stream_tool_result_summary_if_needed(callback, tool_results[i]);
        append_tool_result_and_update_state(
                new_messages,
                request,
                execution_state,
                tool_results[i],
                executed_tool_call,
                tool_name,
                callback);
    }

    reset_context_for_readout_if_needed(request, execution_state, mode);

    if (!tool_results.empty() && !tool_calls_for_execution.empty()
            && tool_calls_for_execution.back().name == "android_view_context_tool") {
        // LLM may request an observation first; when that observation gives a
        // unique candidate, execute the next tap locally instead of spending
        // another model round.
        maybe_run_fast_execute_from_observation(
                request,
                new_messages,
                execution_state,
                tool_results.back().content,
                iteration,
                mode,
                execute_tools,
                callback);
    }

    return tool_results.size();
}
