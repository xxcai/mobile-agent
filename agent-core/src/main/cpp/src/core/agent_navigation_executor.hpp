#pragma once

// Internal AgentLoop navigation state helpers. Included only by agent_loop.cpp.

bool gesture_matches_step_intent(const ToolCall& tool_call, const SkillStepHint& step) {
    if (!tool_call.arguments.is_object()) {
        return step.target.empty() && step.aliases.empty();
    }
    if (step.action == "back" || step.action == "swipe" || step.action == "scroll") {
        return true;
    }
    if (!tool_call.arguments.contains("observation")
            || !tool_call.arguments["observation"].is_object()) {
        return step.target.empty() && step.aliases.empty();
    }

    const auto& observation = tool_call.arguments["observation"];
    const std::string descriptor = trim_whitespace(
            first_string_value(observation, {"targetDescriptor", "target", "label"}));
    if (descriptor.empty()) {
        return step.target.empty() && step.aliases.empty();
    }

    auto matches_label = [&](const std::string& label) -> bool {
        if (label.empty()) {
            return false;
        }
        return contains_runtime_match(descriptor, label)
                || contains_runtime_match(label, descriptor);
    };

    if (matches_label(step.target)) {
        return true;
    }
    for (const auto& alias : step.aliases) {
        if (matches_label(alias)) {
            return true;
        }
    }
    return false;
}

bool snapshot_has_readout_completion_signal(const ObservationSnapshot& snapshot,
                                            const SkillStepHint& step) {
    const std::string visible_context = visible_observation_context(snapshot);
    if (!step.target.empty()
            && !is_weak_completion_signal(step.target)
            && contains_runtime_match(visible_context, step.target)) {
        return true;
    }
    for (const auto& alias : step.aliases) {
        if (alias.empty() || is_weak_completion_signal(alias)) {
            continue;
        }
        if (contains_runtime_match(visible_context, alias)) {
            return true;
        }
    }
    return false;
}

bool is_pull_refresh_swipe_tool_call(const ToolCall& tool_call) {
    if (tool_call.name != "android_gesture_tool" || !tool_call.arguments.is_object()) {
        return false;
    }
    const std::string action = normalize_for_runtime_match(
            first_string_value(tool_call.arguments, {"action", "gesture", "type"}));
    if (action != "swipe" && action != "scroll") {
        return false;
    }
    const std::string direction = normalize_for_runtime_match(
            first_string_value(tool_call.arguments, {"direction", "scrollDirection", "swipeDirection"}));
    // In the Android runtime, direction=down scrolls content down by injecting an upward finger swipe.
    // direction=up injects a downward finger drag and can trigger pull-to-refresh at the top of a list.
    return direction == "up" || direction == "pull_down" || direction == "pulldown";
}

bool should_guard_pull_refresh_for_pending_step(const SkillStepHint& pending_step) {
    if (pending_step.action == "swipe" || pending_step.action == "scroll") {
        return false;
    }
    return pending_step.action.empty()
            || pending_step.action == "tap"
            || pending_step.action == "open"
            || pending_step.action == "read"
            || pending_step.action == "readout";
}

bool step_supports_forward_scroll_search(const SkillStepHint& pending_step) {
    if (pending_step.action != "tap" && pending_step.action != "open") {
        return false;
    }
    const std::string region = normalize_for_runtime_match(pending_step.region);
    const std::string anchor = normalize_for_runtime_match(pending_step.anchor_type);
    const std::string role = normalize_for_runtime_match(pending_step.container_role);
    if (region.find("topleft") != std::string::npos
            || region.find("headerleft") != std::string::npos
            || anchor.find("profile") != std::string::npos
            || anchor.find("bottomtab") != std::string::npos
            || role.find("tabbar") != std::string::npos) {
        return false;
    }
    return anchor.find("card") != std::string::npos
            || anchor.find("item") != std::string::npos
            || anchor.find("row") != std::string::npos
            || role.find("grid") != std::string::npos
            || role.find("list") != std::string::npos
            || role.find("feed") != std::string::npos
            || role.find("scroll") != std::string::npos;
}

bool looks_like_readout_target_hint(const std::string& target_hint) {
    static const std::vector<std::string> keywords = {
        u8"\u6587\u6863", u8"\u6587\u4ef6", u8"\u5185\u5bb9",
        u8"\u8be6\u60c5", u8"\u5217\u8868", u8"\u9875\u9762", u8"\u603b\u7ed3",
        u8"\u6982\u8981", u8"\u9605\u8bfb", u8"\u67e5\u770b",
        "content", "detail", "details", "summary", "summarize", "read",
        "document", "documents", "file", "files", "list", "page"
    };
    for (const auto& keyword : keywords) {
        if (contains_runtime_match(target_hint, keyword)) {
            return true;
        }
    }
    return false;
}

bool matches_readout_target_hint(const ObservationSnapshot& snapshot,
                                 const std::string& target_hint) {
    if (target_hint.empty()) {
        return false;
    }
    if (!looks_like_readout_target_hint(target_hint)) {
        return false;
    }
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    return contains_runtime_match(page_context, target_hint);
}

void clear_step_confirmation(ExecutionState& state) {
    state.awaiting_step_confirmation_index = -1;
    state.awaiting_confirmation_target.clear();
    state.awaiting_confirmation_previous_page.clear();
    state.awaiting_confirmation_retry_count = 0;
}

bool snapshot_matches_confirmation_previous_page(const ExecutionState& state,
                                                const ObservationSnapshot& snapshot) {
    if (state.awaiting_confirmation_previous_page.empty()) {
        return snapshot.activity.empty() && snapshot.summary.empty();
    }

    if (!snapshot.activity.empty()
            && (matches_activity_name(snapshot.activity, state.awaiting_confirmation_previous_page)
                || contains_runtime_match(snapshot.activity, state.awaiting_confirmation_previous_page)
                || contains_runtime_match(state.awaiting_confirmation_previous_page, snapshot.activity))) {
        return true;
    }

    if (!snapshot.summary.empty()
            && (contains_runtime_match(snapshot.summary, state.awaiting_confirmation_previous_page)
                || contains_runtime_match(state.awaiting_confirmation_previous_page, snapshot.summary))) {
        return true;
    }

    return false;
}

bool matches_confirmed_arrival_after_action(const ExecutionState& state,
                                            const ObservationSnapshot& snapshot,
                                            const SkillStepHint& next_step) {
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    if (!next_step.page.empty() && contains_runtime_match(page_context, next_step.page)) {
        return true;
    }

    if (!next_step.activity.empty() && matches_activity_name(snapshot.activity, next_step.activity)) {
        if (!snapshot_matches_confirmation_previous_page(state, snapshot)
                || !step_requires_visible_target_for_arrival(next_step)) {
            return true;
        }
        if (snapshot_has_step_target_visible(snapshot, next_step)) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][execution_state_arrival_confirmed_by_visible_target] page={} target={} activity={}",
                    next_step.page,
                    next_step.target,
                    snapshot.activity);
            return true;
        }
    }

    if (snapshot_has_step_target_visible(snapshot, next_step)) {
        const bool observation_changed = state.navigation_checkpoint.stagnant_rounds == 0;
        const bool page_context_changed = !snapshot_matches_confirmation_previous_page(state, snapshot);
        if (observation_changed || page_context_changed) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][execution_state_arrival_confirmed_by_next_target] page={} target={} activity={} observation_changed={} page_context_changed={}",
                    next_step.page,
                    next_step.target,
                    snapshot.activity,
                    observation_changed,
                    page_context_changed);
            return true;
        }
    }

    return next_step.page.empty()
            && next_step.activity.empty()
            && snapshot_has_step_target_visible(snapshot, next_step);
}

bool should_retry_pending_step_confirmation(const ExecutionState& state,
                                           const ObservationSnapshot& snapshot) {
    if (state.awaiting_step_confirmation_index < 0) {
        return false;
    }
    if (state.awaiting_confirmation_retry_count >= kMaxPendingConfirmationRetries) {
        return false;
    }

    const bool snapshot_incomplete = snapshot.activity.empty() && snapshot.summary.empty();
    if (snapshot_incomplete) {
        return true;
    }

    return snapshot_matches_confirmation_previous_page(state, snapshot);
}

void maybe_wait_before_confirmation_retry(const ExecutionState& state) {
    if (state.awaiting_step_confirmation_index < 0) {
        return;
    }

    const bool initial_settle = state.awaiting_confirmation_retry_count <= 0;
    const auto delay = initial_settle
            ? kPendingConfirmationInitialSettleDelay
            : kPendingConfirmationRetryDelay;
    ICRAW_LOG_INFO(
            "[AgentLoop][execution_state_confirmation_wait] step_index={} retry_count={} reason={} delay_ms={}",
            state.awaiting_step_confirmation_index,
            state.awaiting_confirmation_retry_count,
            initial_settle ? "initial_settle" : "retry",
            static_cast<int>(delay.count()));
    std::this_thread::sleep_for(delay);
}

void record_completed_step(ExecutionState& state, const SkillStepHint& step) {
    state.completed_steps.push_back(describe_step(step));
    if (state.completed_steps.size() > EXECUTION_STATE_MAX_TOOL_EVENTS) {
        state.completed_steps.erase(state.completed_steps.begin());
    }
}

int step_action_attempt_count(const ExecutionState& state, int step_index) {
    const auto it = state.step_action_attempt_counts.find(step_index);
    return it == state.step_action_attempt_counts.end() ? 0 : it->second;
}

bool step_attempt_limit_reached(const ExecutionState& state,
                                const SkillStepHint& step,
                                int step_index) {
    return step.max_attempts > 0
            && step_action_attempt_count(state, step_index) >= step.max_attempts;
}

bool step_requires_page_confirmation(const SkillStepHint& step) {
    return step.action == "tap" || step.action == "open" || step.action == "back";
}

bool confirm_pending_step_from_observation(ExecutionState& state,
                                           const ObservationSnapshot& snapshot) {
    if (!state.active_hints || state.active_hints->empty()) {
        clear_step_confirmation(state);
        return false;
    }

    const auto& steps = state.active_hints->steps;
    const int current_index = state.awaiting_step_confirmation_index;
    if (current_index < 0 || current_index >= static_cast<int>(steps.size())) {
        clear_step_confirmation(state);
        return false;
    }

    const auto& current_step = steps[static_cast<size_t>(current_index)];
    const int next_index = current_index + 1;
    bool confirmed = false;

    if (next_index < static_cast<int>(steps.size())) {
        const auto& next_step = steps[static_cast<size_t>(next_index)];
        confirmed = matches_confirmed_arrival_after_action(state, snapshot, next_step);
        if (!confirmed && next_step.readout) {
            if (snapshot_has_readout_completion_signal(snapshot, next_step)) {
                confirmed = true;
                ICRAW_LOG_INFO(
                        "[AgentLoop][execution_state_readout_confirmed_by_visible_signal] target={} activity={}",
                        next_step.target,
                        snapshot.activity);
            }
        }
        if (!confirmed && next_step.readout) {
            confirmed = matches_goal_page(snapshot, state.goal)
                    || (state.route_ready
                        && matches_stop_condition(snapshot, state.intent_route.stop_condition, state.goal));
        }
        if (confirmed) {
            record_completed_step(state, current_step);
            state.pending_step_index = next_index;
            state.pending_step = build_step_json(next_step);
            state.current_page = snapshot.activity.empty() ? snapshot.summary : snapshot.activity;
            state.latest_observation_summary = snapshot.summary;
            state.phase = next_step.readout ? "readout" : "advance";
            state.goal_reached = next_step.readout
                    || ((next_index + 1) == static_cast<int>(steps.size())
                        && (next_step.action.empty()
                            || next_step.action == "read"
                            || next_step.action == "readout"));
            if (state.goal_reached) {
                state.mode = "free_llm";
            }
            clear_step_confirmation(state);
            return true;
        }
        return false;
    }

    confirmed = matches_goal_page(snapshot, state.goal)
            || (state.route_ready
                && matches_stop_condition(snapshot, state.intent_route.stop_condition, state.goal));
    if (!confirmed && current_step.readout) {
        confirmed = matches_step_page(snapshot, current_step);
    }
    if (confirmed) {
        record_completed_step(state, current_step);
        state.pending_step_index = -1;
        state.pending_step = nlohmann::json::object();
        state.current_page = snapshot.activity.empty() ? snapshot.summary : snapshot.activity;
        state.latest_observation_summary = snapshot.summary;
        state.goal_reached = true;
        state.phase = "readout";
        state.mode = "free_llm";
        clear_step_confirmation(state);
        return true;
    }
    return false;
}

void refresh_pending_step_from_observation(ExecutionState& state,
                                           const ObservationSnapshot& snapshot) {
    if (!state.active_hints || state.active_hints->empty()) {
        return;
    }

    const auto& steps = state.active_hints->steps;
    const size_t start_index = state.pending_step_index > 0
            ? static_cast<size_t>(state.pending_step_index)
            : 0;
    for (size_t i = start_index; i < steps.size(); ++i) {
        if (!matches_step_page(snapshot, steps[i])) {
            continue;
        }
        state.pending_step_index = static_cast<int>(i);
        state.pending_step = build_step_json(steps[i]);
        state.current_page = snapshot.activity.empty() ? snapshot.summary : snapshot.activity;
        state.latest_observation_summary = snapshot.summary;
        state.phase = steps[i].readout ? "readout" : (i == 0 ? "discovery" : "advance");
        state.goal_reached = steps[i].readout
                || ((i + 1) == steps.size()
                    && (steps[i].action.empty() || steps[i].action == "read" || steps[i].action == "readout"));
        if (state.goal_reached) {
            state.mode = "free_llm";
        }
        return;
    }
}

void update_execution_state_with_tool_result(ExecutionState& state,
                                             const ToolCall* tool_call,
                                             const std::string& tool_name,
                                             const std::string& content) {
    const std::string summary = summarize_tool_result_for_state(tool_name, content);
    if (tool_call && tool_call->arguments.is_object()) {
        if (tool_name == "android_view_context_tool") {
            const std::string target_hint = first_string_value(
                    tool_call->arguments, {"targetHint", "target", "pageHint"});
            if (!target_hint.empty()) {
                state.last_observation_target_hint = target_hint;
            }
        } else if (tool_name == "android_gesture_tool" && tool_call->arguments.contains("observation")
                && tool_call->arguments["observation"].is_object()) {
            const std::string target = first_string_value(
                    tool_call->arguments["observation"],
                    {"targetDescriptor", "target", "label"});
            if (!target.empty()) {
                state.last_gesture_target = target;
            }
        }
    }

    if (tool_name == "android_view_context_tool") {
        try {
            const auto json = nlohmann::json::parse(content);
            if (is_mock_observation_result(json)) {
                state.latest_observation_summary = truncate_runtime_text(
                        summary, EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
                state.latest_navigation_observation_summary.clear();
                state.latest_canonical_candidate_summary.clear();
                state.latest_readout_observation_summary.clear();
                state.latest_action_result = summary;
                state.phase = "discovery";
                state.mode = "free_llm";
                clear_step_confirmation(state);
                ICRAW_LOG_WARN(
                        "[AgentLoop][execution_state_observation_ignored] reason=mock_fallback source={} selection_status={}",
                        json.value("source", ""), json.value("selectionStatus", ""));
                return;
            }
        } catch (...) {
            // Fall through and let compact snapshot parsing handle malformed payloads.
        }
        const ObservationSnapshot snapshot = parse_observation_snapshot(content);
        state.latest_observation_summary = truncate_runtime_text(
                snapshot.summary.empty() ? summary : snapshot.summary,
                EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
        state.current_page = truncate_runtime_text(
                snapshot.activity.empty() ? snapshot.summary : snapshot.activity,
                EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
        const auto canonical_candidates = build_canonical_candidates(snapshot.actionable_candidates);
        state.latest_canonical_candidate_summary = build_canonical_candidate_summary(
                canonical_candidates, CANONICAL_CANDIDATE_SUMMARY_COUNT);
        state.latest_navigation_observation_summary = build_navigation_observation_summary(
                content, state.latest_canonical_candidate_summary);
        state.latest_readout_observation_summary = build_readout_observation_summary(content);
        const ObservationFingerprint fingerprint = build_observation_fingerprint(snapshot);
        const bool same_fingerprint = !fingerprint.value.empty()
                && fingerprint.value == state.navigation_checkpoint.last_fingerprint;
        if (same_fingerprint) {
            state.navigation_checkpoint.stagnant_rounds++;
        } else {
            state.navigation_checkpoint.stagnant_rounds = 0;
        }
        state.latest_observation_fingerprint = fingerprint;
        state.navigation_checkpoint.last_activity = snapshot.activity;
        state.navigation_checkpoint.last_summary = snapshot.summary;
        state.navigation_checkpoint.last_fingerprint = fingerprint.value;
        state.navigation_checkpoint.current_step_index = state.pending_step_index;
        refresh_navigation_trace_summary(state);
        ICRAW_LOG_INFO(
                "[AgentLoop][observation_fingerprint] value={} changed={} activity={} labels={} conflicts={} stagnant_rounds={}",
                truncate_runtime_text(fingerprint.value, 180),
                !same_fingerprint,
                truncate_runtime_text(fingerprint.activity, 80),
                join_fingerprint_values(fingerprint.actionable_labels),
                join_fingerprint_values(fingerprint.conflict_codes),
                state.navigation_checkpoint.stagnant_rounds);
        ICRAW_LOG_INFO("[AgentLoop][navigation_trace_summary] {}",
                truncate_runtime_text(state.latest_navigation_trace.summary, 320));
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_count] raw_count={} canonical_count={} activity={} summary_chars={}",
                snapshot.actionable_candidates.size(),
                canonical_candidates.size(),
                truncate_runtime_text(snapshot.activity, 80),
                state.latest_canonical_candidate_summary.size());
        if (state.pending_step_index >= 0) {
            const std::string cache_key = build_navigation_attempt_cache_key(
                    state.pending_step_index, fingerprint.value);
            const auto attempt_it = state.navigation_attempt_cache.find(cache_key);
            if (attempt_it != state.navigation_attempt_cache.end()) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][navigation_attempt_cache_hit] step_index={} attempt_count={} target={} candidate={} tap_bounds={}",
                        state.pending_step_index,
                        attempt_it->second.attempt_count,
                        truncate_runtime_text(attempt_it->second.step_target, 80),
                        truncate_runtime_text(attempt_it->second.selected_candidate, 80),
                        truncate_runtime_text(attempt_it->second.tap_bounds, 80));
            }
        }

        if (state.awaiting_step_confirmation_index >= 0) {
            const int awaiting_index = state.awaiting_step_confirmation_index;
            if (confirm_pending_step_from_observation(state, snapshot)) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][execution_state_step_confirmed] step_index={} current_page={} phase={}",
                        awaiting_index, state.current_page, state.phase);
                refresh_navigation_trace_summary(state);
            } else {
                if (state.active_hints && awaiting_index >= 0
                        && awaiting_index < static_cast<int>(state.active_hints->steps.size())) {
                    const auto& awaiting_step =
                            state.active_hints->steps[static_cast<size_t>(awaiting_index)];
                    state.pending_step_index = awaiting_index;
                    state.pending_step = build_step_json(awaiting_step);
                }
                if (should_retry_pending_step_confirmation(state, snapshot)) {
                    state.awaiting_confirmation_retry_count++;
                    state.goal_reached = false;
                    state.phase = "advance";
                    state.mode = (state.active_hints && !state.active_hints->empty())
                            ? "planned_fast_execute"
                            : "free_llm";
                    ICRAW_LOG_INFO(
                            "[AgentLoop][execution_state_confirmation_retry_scheduled] step_index={} retry_count={} activity={} previous_page={} summary={}",
                            awaiting_index,
                            state.awaiting_confirmation_retry_count,
                            snapshot.activity,
                            truncate_runtime_text(state.awaiting_confirmation_previous_page, 80),
                            truncate_runtime_text(snapshot.summary, 120));
                    return;
                }
                if (same_fingerprint || state.navigation_checkpoint.stagnant_rounds > 0) {
                    const std::string cache_key = build_navigation_attempt_cache_key(
                            awaiting_index, state.navigation_checkpoint.last_fingerprint);
                    const auto attempt_it = state.navigation_attempt_cache.find(cache_key);
                    const int attempt_count = attempt_it == state.navigation_attempt_cache.end()
                            ? 0
                            : attempt_it->second.attempt_count;
                    state.latest_escalation.reason = "ambiguous_or_no_progress";
                    std::ostringstream detail;
                    detail << "fingerprint="
                           << truncate_runtime_text(state.navigation_checkpoint.last_fingerprint, 160);
                    if (attempt_count > 0) {
                        detail << "; attempts=" << attempt_count;
                    }
                    if (attempt_it != state.navigation_attempt_cache.end()
                            && !attempt_it->second.selected_candidate.empty()) {
                        detail << "; candidate="
                               << truncate_runtime_text(attempt_it->second.selected_candidate, 80);
                    }
                    state.latest_escalation.detail = detail.str();
                    state.navigation_checkpoint.stagnant_rounds = std::max(state.navigation_checkpoint.stagnant_rounds, 2);
                    ICRAW_LOG_INFO(
                            "[AgentLoop][navigation_no_progress_detected] reason={} step_index={} attempt_count={} fingerprint={} candidate={} tap_bounds={}",
                            state.latest_escalation.reason,
                            awaiting_index,
                            attempt_count,
                            truncate_runtime_text(state.navigation_checkpoint.last_fingerprint, 160),
                            attempt_it == state.navigation_attempt_cache.end()
                                    ? ""
                                    : truncate_runtime_text(attempt_it->second.selected_candidate, 80),
                            attempt_it == state.navigation_attempt_cache.end()
                                    ? ""
                                    : truncate_runtime_text(attempt_it->second.tap_bounds, 80));
                }
                clear_step_confirmation(state);
                state.goal_reached = false;
                state.phase = "discovery";
                state.mode = (state.active_hints && !state.active_hints->empty()) ? "planned_fast_execute" : "free_llm";
                ICRAW_LOG_WARN(
                        "[AgentLoop][execution_state_confirmation_failed] step_index={} activity={} summary={}",
                        awaiting_index,
                        snapshot.activity,
                        truncate_runtime_text(snapshot.summary, 120));
                return;
            }
        } else {
            refresh_pending_step_from_observation(state, snapshot);
        }
        if (!state.goal_reached && !state.active_hints && matches_goal_page(snapshot, state.goal)) {
            state.goal_reached = true;
            state.phase = "readout";
        }
        if (!state.goal_reached && !state.active_hints
                && matches_readout_target_hint(snapshot, state.last_observation_target_hint)) {
            state.goal_reached = true;
            state.phase = "readout";
            state.mode = "free_llm";
        }
        if (!state.goal_reached && state.route_ready
                && matches_stop_condition(snapshot, state.intent_route.stop_condition, state.goal)) {
            state.goal_reached = true;
            state.phase = state.intent_route.stop_condition.requires_readout ? "readout" : "advance";
            state.mode = "free_llm";
            ICRAW_LOG_INFO(
                    "[AgentLoop][stop_condition_matched] phase={} task_type={} page={} stagnant_rounds={}",
                    state.phase,
                    state.intent_route.task_type,
                    state.current_page,
                    state.navigation_checkpoint.stagnant_rounds);
        }
        if (!state.goal_reached && state.phase == "discovery") {
            state.phase = "advance";
        }
        if (state.goal_reached) {
            state.readout_context.current_page = state.current_page;
        }
        return;
    }

    state.latest_action_result = summary;
    refresh_navigation_trace_summary(state);
    try {
        const auto json = nlohmann::json::parse(content);
        const bool success = json.is_object() && json.value("success", false);
        if (success && tool_name == "android_gesture_tool" && state.active_hints
                && state.pending_step_index >= 0
                && state.pending_step_index < static_cast<int>(state.active_hints->steps.size())) {
            const auto& current_step =
                    state.active_hints->steps[static_cast<size_t>(state.pending_step_index)];
            if (!state.navigation_checkpoint.last_fingerprint.empty()) {
                const std::string cache_key = build_navigation_attempt_cache_key(
                        state.pending_step_index, state.navigation_checkpoint.last_fingerprint);
                if (!cache_key.empty()) {
                    auto& entry = state.navigation_attempt_cache[cache_key];
                    entry.step_target = current_step.target;
                    if (tool_call && tool_call->arguments.is_object()
                            && tool_call->arguments.contains("observation")
                            && tool_call->arguments["observation"].is_object()) {
                        const auto& observation = tool_call->arguments["observation"];
                        entry.selected_candidate = first_string_value(
                                observation, {"targetDescriptor", "target", "label"});
                        entry.tap_bounds = first_string_value(observation, {"referencedBounds"});
                    }
                    entry.attempt_count++;
                }
            }
            if (tool_call && !gesture_matches_step_intent(*tool_call, current_step)) {
                ICRAW_LOG_WARN(
                        "[AgentLoop][execution_state_step_not_advanced] reason=gesture_target_mismatch step_target={} last_target={}",
                        current_step.target, state.last_gesture_target);
                return;
            }
            state.step_action_attempt_counts[state.pending_step_index]++;
            ICRAW_LOG_INFO(
                    "[AgentLoop][step_action_attempt_recorded] step_index={} target={} attempt_count={} max_attempts={}",
                    state.pending_step_index,
                    current_step.target,
                    state.step_action_attempt_counts[state.pending_step_index],
                    current_step.max_attempts);
            if (step_requires_page_confirmation(current_step)) {
                state.awaiting_step_confirmation_index = state.pending_step_index;
                state.awaiting_confirmation_target = current_step.target.empty()
                        ? state.last_gesture_target
                        : current_step.target;
                state.awaiting_confirmation_previous_page = !state.current_page.empty()
                        ? state.current_page
                        : (!state.navigation_checkpoint.last_activity.empty()
                            ? state.navigation_checkpoint.last_activity
                            : state.navigation_checkpoint.last_summary);
                state.awaiting_confirmation_retry_count = 0;
                state.phase = "advance";
                ICRAW_LOG_INFO(
                        "[AgentLoop][execution_state_step_pending_confirmation] step_index={} target={} previous_page={}",
                        state.awaiting_step_confirmation_index,
                        state.awaiting_confirmation_target,
                        truncate_runtime_text(state.awaiting_confirmation_previous_page, 80));
                return;
            }
            record_completed_step(state, current_step);
            const int next_index = state.pending_step_index + 1;
            if (next_index < static_cast<int>(state.active_hints->steps.size())) {
                state.pending_step_index = next_index;
                state.pending_step = build_step_json(
                        state.active_hints->steps[static_cast<size_t>(next_index)]);
            } else {
                state.pending_step_index = -1;
                state.pending_step = nlohmann::json::object();
            }
            state.phase = "advance";
            refresh_navigation_trace_summary(state);
        }
    } catch (...) {
        // Ignore malformed payloads and keep compact text summary only.
    }
}

std::vector<std::string> pending_step_observation_terms(const ExecutionState& state) {
    std::vector<std::string> terms;
    if (state.pending_step.is_object()) {
        const std::string target = first_string_value(
                state.pending_step, {"target", "targetHint", "label"});
        if (!target.empty()) {
            terms.push_back(target);
        }
        const auto aliases = string_array_values(
                state.pending_step, {"aliases", "alias", "targetAliases"});
        for (const auto& alias : aliases) {
            if (!alias.empty()) {
                terms.push_back(alias);
            }
        }
    }
    return terms;
}

bool observation_target_matches_pending_step(const ExecutionState& state) {
    const std::string last_hint = trim_whitespace(state.last_observation_target_hint);
    const auto terms = pending_step_observation_terms(state);
    if (terms.empty()) {
        return true;
    }
    for (const auto& term : terms) {
        if (term.empty()) {
            continue;
        }
        if (contains_runtime_match(last_hint, term)
                || contains_runtime_match(term, last_hint)) {
            return true;
        }
    }

    const std::string latest_observation_context =
            state.latest_canonical_candidate_summary + " "
            + state.latest_navigation_observation_summary + " "
            + state.latest_observation_summary;
    if (!latest_observation_context.empty()) {
        for (const auto& term : terms) {
            if (term.empty()) {
                continue;
            }
            if (contains_runtime_match(latest_observation_context, term)) {
                return true;
            }
        }
    }
    return false;
}

bool latest_observation_needs_pending_target_refresh(const ExecutionState& state,
                                                     bool latest_observation_after_latest_gesture) {
    if (!latest_observation_after_latest_gesture
            || state.goal_reached
            || state.phase == "readout"
            || state.awaiting_step_confirmation_index >= 0) {
        return false;
    }
    if (!state.pending_step.is_object() || state.pending_step.empty()) {
        return false;
    }
    return !observation_target_matches_pending_step(state);
}

std::string determine_observation_detail_mode(const ExecutionState& state,
                                              const std::vector<Message>& messages) {
    if (state.phase == "readout" || state.goal_reached) {
        return "readout";
    }
    if (state.active_hints && state.awaiting_step_confirmation_index >= 0) {
        const int next_index = state.awaiting_step_confirmation_index + 1;
        if (next_index >= 0 && next_index < static_cast<int>(state.active_hints->steps.size())) {
            const auto& next_step = state.active_hints->steps[static_cast<size_t>(next_index)];
            if (next_step.readout) {
                return "readout";
            }
        }
        const int current_index = state.awaiting_step_confirmation_index;
        if (current_index >= 0 && current_index < static_cast<int>(state.active_hints->steps.size())) {
            const auto& current_step = state.active_hints->steps[static_cast<size_t>(current_index)];
            if (current_step.readout) {
                return "readout";
            }
        }
    }
    if (state.active_hints && state.pending_step_index >= 0
            && state.pending_step_index < static_cast<int>(state.active_hints->steps.size())) {
        const auto& pending_step = state.active_hints->steps[static_cast<size_t>(state.pending_step_index)];
        if (pending_step.readout) {
            return "readout";
        }
    }
    const auto tool_name_by_id = build_tool_name_by_id(messages);
    const int latest_gesture_index = find_latest_tool_message_index(messages, tool_name_by_id, "android_gesture_tool");
    const int latest_observation_index = find_latest_tool_message_index(messages, tool_name_by_id, "android_view_context_tool");
    if (latest_gesture_index > latest_observation_index) {
        return "follow_up";
    }
    return latest_observation_index >= 0 ? "follow_up" : "discovery";
}



