#include "icraw/core/agent_loop.hpp"
#include "agent_runtime_state.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/tools/tool_registry.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"
#include "icraw/core/token_utils.hpp"
#include <algorithm>
#include <random>
#include <set>
#include <sstream>
#include <iomanip>
#include <ctime>
#include <chrono>
#include <thread>
#include <cctype>
#include <cstdio>
#include <exception>
#include <limits>
#include <unordered_map>

namespace icraw {

namespace {

#include "agent_runtime_utils.hpp"

// The refactor keeps AgentLoop as the turn orchestrator and moves policy-heavy
// logic into internal slices. These files are included inside this anonymous
// namespace so helper symbols stay private to the core runtime instead of
// becoming part of the public SDK or linker-visible surface.
//
// Keep this order: later slices depend on state helpers declared by earlier
// slices (route -> context -> observation -> navigation -> candidates -> request).
#include "agent_route_planner.hpp"

#include "agent_context_manager.hpp"

#include "agent_observation.hpp"

#include "agent_navigation_executor.hpp"

#include "agent_candidate_matcher.hpp"

#include "agent_request_builder.hpp"

#include "agent_tool_orchestrator.hpp"

bool try_run_deterministic_navigation_step(ChatCompletionRequest& request,
                                           std::vector<Message>& new_messages,
                                           ExecutionState& execution_state,
                                           int iteration,
                                           const std::string& mode,
                                           const ToolExecutionFn& execute_tools,
                                           const AgentEventCallback& callback = AgentEventCallback{}) {
    if (execution_state.mode != "planned_fast_execute"
            || !execution_state.route_ready
            || execution_state.goal_reached) {
        return false;
    }

    // Happy path for structured skills: observe the current page, match the
    // pending NavigationStep, and tap locally when there is exactly one safe
    // high-confidence candidate. Ambiguous/no-progress cases deliberately fall
    // through to the LLM path in the caller.
    prune_context_messages_for_state(request.messages, execution_state);
    const auto tool_name_by_id = build_tool_name_by_id(request.messages);
    const int latest_observation_index = find_latest_tool_message_index(
            request.messages, tool_name_by_id, "android_view_context_tool");
    const int latest_gesture_index = find_latest_tool_message_index(
            request.messages, tool_name_by_id, "android_gesture_tool");
    const bool latest_observation_after_latest_gesture =
            latest_observation_index >= 0 && latest_observation_index > latest_gesture_index;
    const bool stale_pending_step_observation =
            latest_observation_needs_pending_target_refresh(
                    execution_state, latest_observation_after_latest_gesture);
    const bool need_observation = latest_observation_index < 0
            || latest_gesture_index > latest_observation_index
            || execution_state.awaiting_step_confirmation_index >= 0
            || stale_pending_step_observation;

    bool deterministic_navigation_executed = false;
    if (execution_state.navigation_checkpoint.stagnant_rounds >= 2) {
        execution_state.latest_escalation.reason = "no_progress";
        execution_state.latest_escalation.detail =
                "stagnant_rounds=" + std::to_string(execution_state.navigation_checkpoint.stagnant_rounds);
        ICRAW_LOG_INFO(
                "[AgentLoop][navigation_escalation] mode={} reason={} detail={}",
                mode,
                execution_state.latest_escalation.reason,
                execution_state.latest_escalation.detail);
    } else if (need_observation) {
        // Observation is refreshed when no page snapshot exists yet, a gesture
        // happened after the last snapshot, a tap is awaiting confirmation, or
        // the pending target hint changed.
        if (stale_pending_step_observation) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][navigation_observe_refresh] mode={} reason=pending_target_changed last_hint={} pending_step={}",
                    mode,
                    truncate_runtime_text(execution_state.last_observation_target_hint, 100),
                    summarize_pending_step_json(execution_state.pending_step));
        }
        maybe_wait_before_confirmation_retry(execution_state);
        ToolCall observation_call = build_navigation_observation_call(execution_state);
        Message observation_assistant = build_assistant_tool_call_message(observation_call);
        new_messages.push_back(observation_assistant);
        request.messages.push_back(observation_assistant);
        emit_tool_use_event_if_needed(callback, observation_call);

        std::vector<ToolCall> observation_calls{observation_call};
        auto tool_phase_start = std::chrono::steady_clock::now();
        auto observation_calls_for_execution = enrich_tool_calls_for_execution(
                observation_calls, execution_state, request.messages);
        auto tool_results = execute_tools(observation_calls_for_execution);
        auto tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - tool_phase_start).count();
        ICRAW_LOG_INFO(
                "[AgentLoop][tool_phase_duration_ms] mode={} iteration={} phase=navigation_observe tool_call_count={} duration_ms={}",
                mode,
                iteration,
                observation_calls_for_execution.size(),
                tool_phase_duration_ms);

        for (size_t i = 0; i < tool_results.size(); ++i) {
            append_tool_result_and_update_state(
                    new_messages,
                    request,
                    execution_state,
                    tool_results[i],
                    &observation_calls_for_execution[i],
                    observation_calls_for_execution[i].name,
                    callback);
        }
        deterministic_navigation_executed = !tool_results.empty();
    } else {
        // We already have a fresh observation for the current step, so try a
        // zero-LLM fast execute before asking the model again.
        const auto latest_observation = find_latest_tool_result_content(
                request.messages, "android_view_context_tool");
        if (latest_observation.has_value()) {
            const auto fast_attempt = maybe_run_fast_execute_from_observation(
                    request,
                    new_messages,
                    execution_state,
                    latest_observation.value(),
                    iteration,
                    mode,
                    execute_tools,
                    callback,
                    "navigation_fast_execute");
            if (fast_attempt.attempted) {
                deterministic_navigation_executed = fast_attempt.executed;
            } else {
                execution_state.latest_escalation.reason = "ambiguous_or_low_confidence";
                execution_state.latest_escalation.detail =
                        "pending_step=" + execution_state.pending_step.dump();
                ICRAW_LOG_INFO(
                        "[AgentLoop][navigation_escalation] mode={} reason={} detail={}",
                        mode,
                        execution_state.latest_escalation.reason,
                        truncate_runtime_text(execution_state.latest_escalation.detail, 160));
            }
        }
    }

    reset_context_for_readout_if_needed(request, execution_state, mode);

    return deterministic_navigation_executed;
}

bool emit_readout_stream_error_fallback(ChatCompletionRequest& request,
                                        std::vector<Message>& new_messages,
                                        const ExecutionState& execution_state,
                                        const AgentEventCallback& callback,
                                        const std::string& error_message) {
    if (!execution_state.goal_reached || execution_state.phase != "readout") {
        return false;
    }

    const std::string fallback = build_local_readout_fallback_text(execution_state);
    if (fallback.empty()) {
        return false;
    }

    Message assistant_msg;
    assistant_msg.role = "assistant";
    assistant_msg.content = build_response_blocks(fallback, "");
    new_messages.push_back(assistant_msg);
    request.messages.push_back(std::move(assistant_msg));

    if (callback) {
        AgentEvent text_event;
        text_event.type = "text_delta";
        text_event.data["delta"] = fallback;
        callback(text_event);

        AgentEvent end_event;
        end_event.type = "message_end";
        end_event.data["finish_reason"] = "stop";
        callback(end_event);
    }

    ICRAW_LOG_WARN(
            "[AgentLoop][readout_stream_error_local_fallback] chars={} current_page={} error={}",
            fallback.size(),
            execution_state.current_page,
            truncate_runtime_text(error_message, 160));
    return true;
}

AgentLoop::AgentLoop(std::shared_ptr<MemoryManager> memory_manager,
                     std::shared_ptr<SkillLoader> skill_loader,
                     std::shared_ptr<ToolRegistry> tool_registry,
                     std::shared_ptr<LLMProvider> llm_provider,
                     const AgentConfig& agent_config)
    : memory_manager_(std::move(memory_manager))
    , skill_loader_(std::move(skill_loader))
    , tool_registry_(std::move(tool_registry))
    , llm_provider_(std::move(llm_provider))
    , agent_config_(agent_config)
    , max_iterations_(agent_config.max_iterations) {
}

// Non-streaming turn runner. The method intentionally owns only orchestration:
// seed the request, resolve a route, let deterministic navigation try first,
// fall back to the LLM when confidence is low, then execute tools and update
// ExecutionState. The streaming variant below mirrors this state machine and
// only adds AgentEvent callbacks around tool/LLM output.
std::vector<Message> AgentLoop::process_message(const std::string& message,
                                                  const std::vector<Message>& history,
                                                  const std::string& system_prompt,
                                                  const std::vector<SkillMetadata>& selected_skills) {
    std::vector<Message> new_messages;
    stop_requested_ = false;

    // Stage 1: seed the provider request with stable turn inputs. Later phases
    // may build a slimmer effective_request, but request remains the canonical
    // transcript used for tool result replay and ExecutionState updates.
    ChatCompletionRequest request =
            build_turn_request(agent_config_, false, system_prompt, history, message);

    // Stage 2: resolve route/navigation/readout intent from the user message
    // and selected skills. Known skills can resolve locally from execution_hints;
    // unknown or insufficient hints may use a lightweight route LLM request.
    auto tool_schemas = tool_registry_->get_tool_schemas();
    ExecutionState execution_state = initialize_execution_state(message, selected_skills);
    maybe_resolve_route_with_llm(execution_state, agent_config_, llm_provider_.get(), message, selected_skills);
    rebuild_tools_for_phase(tool_schemas, execution_state, request);
    log_route_resolved_if_needed(execution_state, "non_stream");

    // Stage 3: run the turn loop. Each pass first gives the local navigation
    // state machine a chance to advance without spending an LLM round.
    int iteration = 0;
    auto loop_start_time = std::chrono::steady_clock::now();
    ICRAW_LOG_INFO("[AgentLoop][loop_start] mode=non_stream max_iterations={}", max_iterations_);
    const ToolExecutionFn execute_tools = [this](const std::vector<ToolCall>& tool_calls) {
        return handle_tool_calls(tool_calls);
    };
    while (iteration < max_iterations_ && !stop_requested_) {
        if (try_run_deterministic_navigation_step(
                    request, new_messages, execution_state, iteration, "non_stream", execute_tools)) {
            continue;
        }

        auto iter_start_time = std::chrono::steady_clock::now();
        iteration++;
        ICRAW_LOG_DEBUG("[AgentLoop][iteration_start] mode=non_stream iteration={}", iteration);

        prune_context_messages_for_state(request.messages, execution_state);

        // Stage 4: fallback/normal LLM step. The effective request is rebuilt
        // for the current phase, so route/navigation/readout can use different
        // tool sets, token budgets, and context compaction rules.
        ChatCompletionRequest effective_request = build_effective_request_for_iteration(
                request,
                message,
                tool_schemas,
                selected_skills,
                execution_state,
                agent_config_,
                iteration,
                "non_stream");

        // Call LLM
        auto response = llm_provider_->chat_completion(effective_request);
        execution_state.latest_escalation = NavigationEscalation{};

        // Build assistant message
        Message assistant_msg;
        assistant_msg.role = "assistant";
        assistant_msg.content = build_response_blocks(response.content, response.reasoning_content);

        auto filtered_tool_calls = filter_tool_calls_for_request(
                response.tool_calls, effective_request, execution_state);

        append_tool_calls_to_assistant_message(assistant_msg, filtered_tool_calls);

        new_messages.push_back(assistant_msg);
        request.messages.push_back(assistant_msg);

        // Log iteration timing before decision (ensures log even when breaking)
        auto iter_end_time = std::chrono::steady_clock::now();
        auto iter_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(iter_end_time - iter_start_time).count();
        ICRAW_LOG_INFO("[AgentLoop][iteration_complete] mode=non_stream iteration={} duration_ms={}",
                iteration, iter_duration_ms);

        // Check if we're done
        if (filtered_tool_calls.empty()) {
            const bool rejected_readout_tool_only =
                    execution_state.goal_reached
                    && execution_state.phase == "readout"
                    && response.finish_reason == "tool_calls"
                    && response.content.empty();
            if (rejected_readout_tool_only) {
                if (execution_state.readout_retry_count < 1) {
                    execution_state.readout_retry_count++;
                    request.messages.emplace_back("user", build_readout_retry_instruction(execution_state));
                    ICRAW_LOG_INFO(
                            "[AgentLoop][readout_retry_scheduled] mode=non_stream retry_count={} current_page={}",
                            execution_state.readout_retry_count,
                            execution_state.current_page);
                    continue;
                }

                const std::string fallback = build_local_readout_fallback_text(execution_state);
                if (!fallback.empty()) {
                    assistant_msg.content = build_response_blocks(fallback, "");
                    new_messages.back() = assistant_msg;
                    request.messages.back() = assistant_msg;
                    ICRAW_LOG_INFO(
                            "[AgentLoop][readout_local_fallback] mode=non_stream chars={} current_page={}",
                            fallback.size(),
                            execution_state.current_page);
                }
            }
            break;
        }
        if (response.finish_reason == "end_turn") {
            break;
        }

        execute_tool_calls_and_maybe_fast_execute(
                request,
                new_messages,
                execution_state,
                filtered_tool_calls,
                iteration,
                "non_stream",
                execute_tools);
    }

    auto loop_end_time = std::chrono::steady_clock::now();
    auto loop_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(loop_end_time - loop_start_time).count();
    ICRAW_LOG_INFO("[AgentLoop][loop_complete] mode=non_stream duration_ms={} iteration_count={}",
            loop_duration_ms, iteration);

    return new_messages;
}

// Streaming turn runner. It follows the same Route -> Navigate -> LLM/Tool ->
// Readout state machine as process_message(), while emitting tool_use and
// tool_result events so the UI can display incremental progress.
std::vector<Message> AgentLoop::process_message_stream(const std::string& message,
                                                        const std::vector<Message>& history,
                                                        const std::string& system_prompt,
                                                        AgentEventCallback callback,
                                                        const std::vector<SkillMetadata>& selected_skills) {
    std::vector<Message> new_messages;
    stop_requested_ = false;
    std::string loop_exit_reason;

    // Stage 1 mirrors non-stream mode: keep request as the canonical transcript
    // and build phase-specific effective requests only when invoking the model.
    ChatCompletionRequest request =
            build_turn_request(agent_config_, true, system_prompt, history, message);

    // Stage 2 resolves the route before the main loop so navigation can often
    // proceed locally without waiting for a model decision on every step.
    auto tool_schemas = tool_registry_->get_tool_schemas();
    ExecutionState execution_state = initialize_execution_state(message, selected_skills);
    maybe_resolve_route_with_llm(execution_state, agent_config_, llm_provider_.get(), message, selected_skills);
    rebuild_tools_for_phase(tool_schemas, execution_state, request);
    log_route_resolved_if_needed(execution_state, "stream");

    // Stage 3 mirrors non-stream mode, with callbacks emitted around local tool
    // execution so observers see deterministic navigation progress too.
    int iteration = 0;
    auto loop_start_time = std::chrono::steady_clock::now();
    ICRAW_LOG_INFO("[AgentLoop][loop_start] mode=stream max_iterations={}", max_iterations_);
    const ToolExecutionFn execute_tools = [this](const std::vector<ToolCall>& tool_calls) {
        return handle_tool_calls(tool_calls);
    };
    while (iteration < max_iterations_ && !stop_requested_) {
        if (try_run_deterministic_navigation_step(
                    request, new_messages, execution_state, iteration, "stream", execute_tools, callback)) {
            continue;
        }

        iteration++;
        auto iter_start_time = std::chrono::steady_clock::now();
        ICRAW_LOG_DEBUG("[AgentLoop][iteration_start] mode=stream iteration={}", iteration);

        prune_context_messages_for_state(request.messages, execution_state);

        // Stage 4: model fallback or readout. Streaming mode still applies the
        // same phase-specific request profile and context trimming.
        ChatCompletionRequest effective_request = build_effective_request_for_iteration(
                request,
                message,
                tool_schemas,
                selected_skills,
                execution_state,
                agent_config_,
                iteration,
                "stream");

        // Reset state for this iteration
        last_finish_reason_.clear();

        // === Accumulator Pattern ===
        // StreamParser handles tool call accumulation internally
        // We only accumulate text here for real-time display
        std::string accumulated_text;
        std::string accumulated_reasoning;
        std::vector<ToolCall> final_tool_calls;
        bool stream_complete = false;

        ICRAW_LOG_INFO("[AgentLoop][stream_start] iteration={}", iteration);

        // Stream LLM response. If readout already has target-page observation
        // but the provider fails, return a local page-summary fallback instead
        // of leaving the UI with only navigation tool events.
        try {
            llm_provider_->chat_completion_stream(effective_request,
                [&](const ChatCompletionResponse& chunk) {
                    // Accumulate and emit text delta
                    if (!chunk.content.empty()) {
                        accumulated_text += chunk.content;

                        AgentEvent event;
                        event.type = "text_delta";
                        event.data["delta"] = chunk.content;
                        callback(event);
                    }

                    if (!chunk.reasoning_content.empty()) {
                        accumulated_reasoning += chunk.reasoning_content;

                        AgentEvent event;
                        event.type = "reasoning_delta";
                        event.data["delta"] = chunk.reasoning_content;
                        callback(event);
                    }

                    // When stream ends, StreamParser provides complete tool calls
                    if (chunk.is_stream_end) {
                        ICRAW_LOG_DEBUG("[AgentLoop][stream_complete_debug] finish_reason={} tool_call_count={}",
                            chunk.finish_reason, chunk.tool_calls.size());
                        stream_complete = true;
                        final_tool_calls = chunk.tool_calls;  // Already accumulated by StreamParser
                        last_finish_reason_ = chunk.finish_reason;

                        ICRAW_LOG_DEBUG("[AgentLoop][stream_complete_debug] action=emit_message_end");
                        AgentEvent event;
                        event.type = "message_end";
                        event.data["finish_reason"] = chunk.finish_reason;
                        callback(event);
                    }
                });
        } catch (const std::exception& error) {
            if (emit_readout_stream_error_fallback(
                        request, new_messages, execution_state, callback, error.what())) {
                loop_exit_reason = "readout_stream_error_local_fallback";
                break;
            }
            throw;
        }

        if (stop_requested_ && !stream_complete) {
            loop_exit_reason = "cancel";
            ICRAW_LOG_INFO("[AgentLoop][stream_cancelled] iteration={} text_length={} reasoning_length={}",
                    iteration, accumulated_text.length(), accumulated_reasoning.length());

            AgentEvent event;
            event.type = "message_end";
            event.data["finish_reason"] = "cancel";
            callback(event);
            break;
        }

        ICRAW_LOG_INFO("[AgentLoop][stream_complete] iteration={} stream_complete={} text_length={} reasoning_length={} tool_call_count={}",
            iteration, stream_complete, accumulated_text.length(), accumulated_reasoning.length(), final_tool_calls.size());
        execution_state.latest_escalation = NavigationEscalation{};

        // === Deferred Processing ===
        // StreamParser has already accumulated and validated tool calls
        // Build assistant message with accumulated content
        Message assistant_msg;
        assistant_msg.role = "assistant";

        assistant_msg.content = build_response_blocks(accumulated_text, accumulated_reasoning);

        // Process tool calls - StreamParser already accumulated and validated them
        std::vector<ToolCall> valid_tool_calls;
        for (const auto& tc : final_tool_calls) {
            // Validate tool call has both name AND valid arguments
            if (tc.name.empty() || tc.arguments.is_null()) {
                ICRAW_LOG_WARN("[AgentLoop][tool_call_invalid] reason=incomplete_tool_call tool_name={} has_arguments={}",
                    tc.name, !tc.arguments.is_null());
                continue;
            }

            // StreamParser already parsed arguments, just validate
            nlohmann::json parsed_args;

            if (tc.arguments.is_string()) {
                try {
                    std::string args_str = tc.arguments.get<std::string>();
                    parsed_args = nlohmann::json::parse(args_str);
                } catch (...) {
                    ICRAW_LOG_WARN("[AgentLoop][tool_call_invalid] reason=invalid_arguments tool_name={}", tc.name);
                    continue;
                }
            } else if (tc.arguments.is_object()) {
                parsed_args = tc.arguments;
            } else {
                continue;
            }

            ToolCall valid_tc = tc;
            valid_tc.arguments = parsed_args;
            valid_tool_calls.push_back(valid_tc);

            ICRAW_LOG_DEBUG("[AgentLoop][tool_call_debug] stage=validated tool_name={} tool_id={}",
                    valid_tc.name, valid_tc.id);
        }

        valid_tool_calls = filter_tool_calls_for_request(valid_tool_calls, effective_request, execution_state);

        append_tool_calls_to_assistant_message(assistant_msg, valid_tool_calls, callback);

        ICRAW_LOG_DEBUG("[AgentLoop][assistant_message_debug] content_block_count={} tool_call_count={} text_length={}",
            assistant_msg.content.size(), assistant_msg.tool_calls.size(), accumulated_text.size());

        new_messages.push_back(assistant_msg);
        request.messages.push_back(assistant_msg);

        // === Deduplication ===
        // Remove duplicate tool calls (same name + arguments)
        std::vector<ToolCall> deduplicated_tool_calls;
        std::set<std::pair<std::string, std::string>> seen_tool_signatures;

        for (const auto& tc : valid_tool_calls) {
            // Create signature from tool name and normalized arguments
            std::string args_str = tc.arguments.is_string()
                ? tc.arguments.get<std::string>()
                : tc.arguments.dump();

            // Normalize JSON by re-parsing and re-dumping to catch equivalent JSON
            try {
                auto args_json = nlohmann::json::parse(args_str);
                args_str = args_json.dump();  // Normalize JSON format
            } catch (...) {
                // If not valid JSON, use as-is
            }

            std::pair<std::string, std::string> signature = {tc.name, args_str};

            if (seen_tool_signatures.find(signature) == seen_tool_signatures.end()) {
                seen_tool_signatures.insert(signature);
                deduplicated_tool_calls.push_back(tc);
            } else {
                ICRAW_LOG_DEBUG("[AgentLoop][tool_call_debug] stage=deduplicated tool_name={} args_length={}",
                        tc.name, args_str.size());
            }
        }

        ICRAW_LOG_DEBUG("[AgentLoop][tool_call_debug] stage=deduplication before={} after={} removed={}",
            valid_tool_calls.size(), deduplicated_tool_calls.size(),
            valid_tool_calls.size() - deduplicated_tool_calls.size());

        // Use deduplicated tool calls
        valid_tool_calls = std::move(deduplicated_tool_calls);

        // === Decision Point ===
        // Check if we should continue the loop or exit
        ICRAW_LOG_DEBUG("[AgentLoop][decision_debug] valid_tool_call_count={} finish_reason={}",
            valid_tool_calls.size(), last_finish_reason_);

        // Log iteration timing before decision (ensures log even when breaking)
        auto iter_end_time = std::chrono::steady_clock::now();
        auto iter_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(iter_end_time - iter_start_time).count();
        ICRAW_LOG_INFO("[AgentLoop][iteration_complete] mode=stream iteration={} duration_ms={}",
                iteration, iter_duration_ms);

        if (valid_tool_calls.empty()) {
            const bool rejected_readout_tool_only =
                    execution_state.goal_reached
                    && execution_state.phase == "readout"
                    && last_finish_reason_ == "tool_calls"
                    && accumulated_text.empty();
            if (rejected_readout_tool_only) {
                if (execution_state.readout_retry_count < 1) {
                    execution_state.readout_retry_count++;
                    request.messages.emplace_back("user", build_readout_retry_instruction(execution_state));
                    ICRAW_LOG_INFO(
                            "[AgentLoop][readout_retry_scheduled] mode=stream retry_count={} current_page={}",
                            execution_state.readout_retry_count,
                            execution_state.current_page);
                    continue;
                }

                const std::string fallback = build_local_readout_fallback_text(execution_state);
                if (!fallback.empty()) {
                    accumulated_text = fallback;
                    assistant_msg.content = build_response_blocks(accumulated_text, "");
                    new_messages.back() = assistant_msg;
                    request.messages.back() = assistant_msg;

                    AgentEvent text_event;
                    text_event.type = "text_delta";
                    text_event.data["delta"] = accumulated_text;
                    callback(text_event);

                    AgentEvent end_event;
                    end_event.type = "message_end";
                    end_event.data["finish_reason"] = "stop";
                    callback(end_event);

                    ICRAW_LOG_INFO(
                            "[AgentLoop][readout_local_fallback] mode=stream chars={} current_page={}",
                            accumulated_text.size(),
                            execution_state.current_page);
                    loop_exit_reason = "readout_local_fallback";
                    break;
                }
            }

            // No valid tool calls - check finish_reason to decide
            if (last_finish_reason_ == "stop" || last_finish_reason_ == "end_turn") {
                ICRAW_LOG_DEBUG("[AgentLoop][loop_exit_debug] reason=finish_reason finish_reason={}", last_finish_reason_);
                loop_exit_reason = last_finish_reason_;
                AgentEvent event;
                event.type = "message_end";
                event.data["finish_reason"] = "stop";
                callback(event);
                break;  // Exit loop - LLM is done
            }

            // For text-only responses (no tool calls), also exit the loop
            // The LLM has finished its response
            ICRAW_LOG_DEBUG("[AgentLoop][loop_exit_debug] reason=text_only_response");
            loop_exit_reason = "text_only_response";
            AgentEvent event;
            event.type = "message_end";
            event.data["finish_reason"] = "stop";
            callback(event);
            break;
        }
        // If we have valid tool calls, execute them and loop continues

        const size_t tool_result_count = execute_tool_calls_and_maybe_fast_execute(
                request,
                new_messages,
                execution_state,
                valid_tool_calls,
                iteration,
                "stream",
                execute_tools,
                callback);

        if (stop_requested_) {
            loop_exit_reason = "cancel";
            ICRAW_LOG_INFO("[AgentLoop][tool_phase_cancelled] iteration={} tool_result_count={}",
                    iteration, tool_result_count);

            AgentEvent event;
            event.type = "message_end";
            event.data["finish_reason"] = "cancel";
            callback(event);
            break;
        }
    }

    if (loop_exit_reason.empty()) {
        if (stop_requested_) {
            loop_exit_reason = "cancel";
        } else if (iteration >= max_iterations_) {
            loop_exit_reason = "max_iterations";
            ICRAW_LOG_WARN("[AgentLoop][loop_exit_debug] reason=max_iterations_reached iteration={} max_iterations={}",
                    iteration, max_iterations_);

            AgentEvent event;
            event.type = "message_end";
            event.data["finish_reason"] = "max_iterations";
            callback(event);
        }
    }

    auto loop_end_time = std::chrono::steady_clock::now();
    auto loop_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(loop_end_time - loop_start_time).count();
    ICRAW_LOG_INFO("[AgentLoop][loop_complete] mode=stream duration_ms={} iteration_count={} exit_reason={}",
            loop_duration_ms, iteration, loop_exit_reason);

    ICRAW_LOG_DEBUG("[AgentLoop][loop_complete_debug] iteration={} total_message_count={} exit_reason={}",
        iteration, new_messages.size(), loop_exit_reason);

    return new_messages;
}

void AgentLoop::stop() {
    stop_requested_ = true;
    if (llm_provider_) {
        llm_provider_->cancel_active_request();
    }
}

void AgentLoop::set_config(const AgentConfig& config) {
    agent_config_ = config;
    max_iterations_ = config.max_iterations;
}

} // namespace icraw
