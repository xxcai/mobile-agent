#pragma once

// Internal AgentLoop implementation chunk. Included only by agent_loop.cpp.

std::optional<std::pair<int, Message>> find_latest_observation_assistant(
        const std::vector<Message>& messages) {
    const auto tool_name_by_id = build_tool_name_by_id(messages);
    const int latest_observation_index = find_latest_tool_message_index(
            messages, tool_name_by_id, "android_view_context_tool");
    if (latest_observation_index < 0) {
        return std::nullopt;
    }
    const std::string tool_call_id = messages[static_cast<size_t>(latest_observation_index)].tool_call_id;
    for (int i = latest_observation_index - 1; i >= 0; --i) {
        const auto& message = messages[static_cast<size_t>(i)];
        if (message.role != "assistant") {
            continue;
        }
        for (const auto& tool_call : message.tool_calls) {
            if (tool_call.id == tool_call_id) {
                return std::make_pair(latest_observation_index, message);
            }
        }
    }
    return std::nullopt;
}

std::string build_readout_user_objective(const ExecutionState& state) {
    std::ostringstream objective;
    objective << state.readout_context.objective;
    if (!state.readout_context.readout_goal.empty()
            && !contains_runtime_match(state.readout_context.objective, state.readout_context.readout_goal)) {
        objective << "\nreadout_goal: "
                  << truncate_runtime_text(state.readout_context.readout_goal, 160);
    }
    if (!state.readout_context.selected_skill.empty()) {
        objective << "\nselected_skill: " << state.readout_context.selected_skill;
    }
    if (!state.readout_context.current_page.empty()) {
        objective << "\ncurrent_page: "
                  << truncate_runtime_text(state.readout_context.current_page, 120);
    }
    const auto& stop_condition = state.intent_route.stop_condition;
    if (!stop_condition.success_signals.empty()) {
        objective << "\nsuccess_signals: "
                  << truncate_runtime_text(join_string_values(stop_condition.success_signals, ", "), 260);
        objective << "\nIf the current page visibly contains any success signal, treat the task as completed successfully.";
        objective << "\nFor time-related success signals, a visible concrete time value such as HH:MM also indicates success.";
    }
    if (!stop_condition.failure_signals.empty()) {
        objective << "\nfailure_signals: "
                  << truncate_runtime_text(join_string_values(stop_condition.failure_signals, ", "), 220);
        objective << "\nOnly report failure when a failure signal is visible and no success signal is visible.";
    }
    objective << "\nFocus on the current page only.";
    return objective.str();
}

void reset_messages_for_readout(std::vector<Message>& messages,
                                const ExecutionState& state) {
    if (messages.empty()) {
        return;
    }
    std::vector<Message> reduced;
    bool kept_system = false;
    for (const auto& message : messages) {
        if (message.role == "system" && !kept_system) {
            Message readout_system = message;
            const std::string before = readout_system.text();
            const std::string after = build_readout_system_prompt(before);
            readout_system.content.clear();
            if (!after.empty()) {
                readout_system.content.push_back(ContentBlock::make_text(after));
            }
            ICRAW_LOG_INFO(
                    "[AgentLoop][readout_system_prompt_reduced] before_chars={} after_chars={} saved_chars={}",
                    before.size(),
                    after.size(),
                    before.size() > after.size() ? (before.size() - after.size()) : 0);
            reduced.push_back(std::move(readout_system));
            kept_system = true;
            break;
        }
    }
    reduced.emplace_back("user", build_readout_user_objective(state));
    const auto latest_observation_assistant = find_latest_observation_assistant(messages);
    if (latest_observation_assistant) {
        reduced.push_back(latest_observation_assistant->second);
        Message summarized_tool_message = messages[static_cast<size_t>(latest_observation_assistant->first)];
        for (auto& block : summarized_tool_message.content) {
            if (block.type != "tool_result") {
                continue;
            }
            const std::string readout_summary =
                    state.latest_readout_observation_summary.empty()
                    ? build_readout_observation_summary(block.content)
                    : state.latest_readout_observation_summary;
            block.content = readout_summary;
            ICRAW_LOG_INFO(
                    "[AgentLoop][readout_observation_summary_chars] chars={}",
                    readout_summary.size());
        }
        reduced.push_back(std::move(summarized_tool_message));
    }
    messages = std::move(reduced);
}

void rebuild_tools_for_phase(const std::vector<ToolSchema>& tool_schemas,
                             const ExecutionState& state,
                             ChatCompletionRequest& request) {
    request.tools.clear();
    for (const auto& schema : tool_schemas) {
        const bool is_gesture = schema.name == "android_gesture_tool";
        const bool is_route_phase = state.phase == "discovery"
                && state.route_ready
                && state.latest_escalation.reason.empty()
                && !state.goal_reached;
        const bool is_readout_phase = state.phase == "readout" || state.goal_reached;
        if (is_readout_phase) {
            continue;
        }
        if (is_route_phase && is_gesture) {
            continue;
        }
        nlohmann::json tool;
        tool["type"] = "function";
        tool["function"]["name"] = schema.name;
        tool["function"]["description"] = schema.description;
        tool["function"]["parameters"] = schema.parameters;
        request.tools.push_back(std::move(tool));
    }
}

std::string first_system_text(const ChatCompletionRequest& request) {
    for (const auto& message : request.messages) {
        if (message.role == "system") {
            return message.text();
        }
    }
    return "";
}

std::string build_navigation_escalation_payload(const NavigationEscalationRequest& escalation_request) {
    std::ostringstream body;
    body << "objective: " << truncate_runtime_text(escalation_request.objective, 180) << "\n";
    body << "reason: " << escalation_request.escalation.reason << "\n";
    if (!escalation_request.escalation.detail.empty()) {
        body << "reason_detail: " << truncate_runtime_text(escalation_request.escalation.detail, 160) << "\n";
    }
    body << "route.task_type: " << escalation_request.intent_route.task_type << "\n";
    body << "route.navigation_goal: " << truncate_runtime_text(escalation_request.intent_route.navigation_goal, 120) << "\n";
    body << "route.readout_goal: " << truncate_runtime_text(escalation_request.intent_route.readout_goal, 120) << "\n";
    body << "checkpoint.step_index: " << escalation_request.checkpoint.current_step_index << "\n";
    body << "checkpoint.stagnant_rounds: " << escalation_request.checkpoint.stagnant_rounds << "\n";
    body << "checkpoint.last_activity: " << truncate_runtime_text(escalation_request.checkpoint.last_activity, 100) << "\n";
    body << "checkpoint.last_fingerprint: " << truncate_runtime_text(escalation_request.checkpoint.last_fingerprint, 160) << "\n";
    if (!escalation_request.pending_step_summary.empty()) {
        body << "pending_step: " << escalation_request.pending_step_summary << "\n";
    }
    if (!escalation_request.observation_summary.empty()) {
        body << "latest_observation: " << truncate_runtime_text(escalation_request.observation_summary, 180) << "\n";
    }
    if (!escalation_request.latest_action_result.empty()) {
        body << "latest_action_result: " << truncate_runtime_text(escalation_request.latest_action_result, 160) << "\n";
    }
    if (!escalation_request.trace_summary.empty()) {
        body << "navigation_trace: " << truncate_runtime_text(escalation_request.trace_summary, 320) << "\n";
    }
    body << "\nPolicy:\n";
    body << "- This is a local navigation escalation, not a full replan.\n";
    body << "- If a concrete UI action is safe, call the tool directly. Do not emit explanatory text before the tool call.\n";
    body << "- If the current page must be verified first, call android_view_context_tool only.\n";
    body << "- Continue from the pending step; do not restart from the app home page unless the observation is clearly off-route.\n";
    return body.str();
}

NavigationEscalationRequest build_navigation_escalation_request_context(
        const std::string& objective,
        const ExecutionState& state) {
    NavigationEscalationRequest context;
    context.objective = objective;
    context.intent_route = state.intent_route;
    context.checkpoint = state.navigation_checkpoint;
    context.escalation = state.latest_escalation;
    context.pending_step_summary = summarize_pending_step_json(state.pending_step);
    context.observation_summary = state.latest_navigation_observation_summary.empty()
            ? state.latest_observation_summary
            : state.latest_navigation_observation_summary;
    context.latest_action_result = state.latest_action_result;
    context.trace_summary = build_navigation_trace_summary_text(state);
    return context;
}

bool navigation_escalation_allows_tool(const ToolSchema& schema, const ExecutionState& state) {
    if (state.goal_reached || state.phase == "readout") {
        return false;
    }
    const bool is_view_context = schema.name == "android_view_context_tool";
    const bool is_gesture = schema.name == "android_gesture_tool";
    if (!is_view_context && !is_gesture) {
        return false;
    }
    if (state.awaiting_step_confirmation_index >= 0 || state.latest_observation_summary.empty()) {
        return is_view_context;
    }
    return is_view_context || is_gesture;
}

void append_navigation_escalation_tools(ChatCompletionRequest& request,
                                        const std::vector<ToolSchema>& tool_schemas,
                                        const ExecutionState& state) {
    for (const auto& schema : tool_schemas) {
        if (!navigation_escalation_allows_tool(schema, state)) {
            continue;
        }
        nlohmann::json tool;
        tool["type"] = "function";
        tool["function"]["name"] = schema.name;
        tool["function"]["description"] = truncate_runtime_text(schema.description, 260);
        tool["function"]["parameters"] = schema.parameters;
        request.tools.push_back(std::move(tool));
    }
}

ChatCompletionRequest build_compact_navigation_escalation_chat_request(
        const ChatCompletionRequest& base_request,
        const std::string& objective,
        const std::vector<ToolSchema>& tool_schemas,
        const ExecutionState& state) {
    ChatCompletionRequest request;
    request.model = base_request.model;
    request.temperature = base_request.temperature;
    request.max_tokens = base_request.max_tokens;
    request.enable_thinking = base_request.enable_thinking;
    request.stream = base_request.stream;
    request.request_profile = "navigation_escalation";
    request.payload_log_mode = base_request.payload_log_mode;

    const std::string reduced_system = build_readout_system_prompt(first_system_text(base_request));
    std::ostringstream system;
    if (!reduced_system.empty()) {
        system << reduced_system << "\n\n";
    }
    system << "[Navigation Escalation]\n";
    system << "Make one short UI navigation decision. Prefer a tool call over visible text.\n";
    system << "When using a tool, output the tool call immediately with no preamble.\n";
    request.messages.emplace_back("system", system.str());

    const auto escalation_context = build_navigation_escalation_request_context(objective, state);
    request.messages.emplace_back("user", build_navigation_escalation_payload(escalation_context));

    append_navigation_escalation_tools(request, tool_schemas, state);
    request.tool_choice_auto = request.tools.empty();
    ICRAW_LOG_INFO(
            "[AgentLoop][navigation_escalation_request_compacted] message_count={} tool_count={} payload_chars={} trace_chars={}",
            request.messages.size(),
            request.tools.size(),
            total_payload_length(request.messages),
            escalation_context.trace_summary.size());
    return request;
}

std::string build_route_skill_catalog(const std::vector<SkillMetadata>& selected_skills) {
    if (selected_skills.empty()) {
        return "(none)";
    }
    std::ostringstream catalog;
    size_t count = 0;
    for (const auto& skill : selected_skills) {
        if (count >= SKILL_PRELOAD_MAX_COUNT) {
            break;
        }
        catalog << build_skill_summary_prompt(skill) << "\n";
        count++;
    }
    return catalog.str();
}

ChatCompletionRequest build_compact_route_chat_request(const AgentConfig& config,
                                                       const std::string& objective,
                                                       const std::vector<SkillMetadata>& selected_skills) {
    ChatCompletionRequest request;
    request.model = config.model;
    request.temperature = config.temperature;
    request.max_tokens = config.max_tokens;
    request.enable_thinking = config.enable_thinking;
    request.stream = false;
    request.request_profile = "route";
    request.tool_choice_auto = true;
    request.messages.emplace_back("system",
            "You are the lightweight route stage for an Android UI agent. Return only a compact JSON object. "
            "Do not call tools. Do not include markdown. Required keys: task_type, selected_skill, navigation_goal, readout_goal, escalation_policy, stop_condition. "
            "stop_condition must contain page_predicates, content_predicates, success_signals, failure_signals, requires_readout.");
    std::ostringstream user;
    user << "User objective:\n" << objective << "\n\n";
    user << "Matched skill summaries:\n" << build_route_skill_catalog(selected_skills) << "\n";
    user << "Return route JSON now.";
    request.messages.emplace_back("user", user.str());
    return request;
}

std::vector<std::string> json_string_array_or_empty(const nlohmann::json& object,
                                                    const std::string& key) {
    std::vector<std::string> values;
    if (!object.is_object() || !object.contains(key) || !object[key].is_array()) {
        return values;
    }
    for (const auto& item : object[key]) {
        if (item.is_string()) {
            const std::string value = trim_whitespace(item.get<std::string>());
            if (!value.empty()) {
                values.push_back(value);
            }
        }
    }
    return values;
}

bool apply_route_json_to_execution_state(ExecutionState& state,
                                         const std::string& objective,
                                         const std::string& content) {
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return false;
        }
        state.intent_route.task_type = first_string_value(json, {"task_type", "taskType"});
        state.intent_route.selected_skill = first_string_value(json, {"selected_skill", "selectedSkill"});
        state.intent_route.navigation_goal = first_string_value(json, {"navigation_goal", "navigationGoal"});
        state.intent_route.readout_goal = first_string_value(json, {"readout_goal", "readoutGoal"});
        state.intent_route.escalation_policy = first_string_value(json, {"escalation_policy", "escalationPolicy"});
        if (state.intent_route.task_type.empty()) {
            state.intent_route.task_type = state.intent_route.stop_condition.requires_readout ? "navigate_and_read" : "navigate_and_trigger";
        }
        if (state.intent_route.readout_goal.empty()) {
            state.intent_route.readout_goal = truncate_runtime_text(objective, 160);
        }
        if (state.intent_route.navigation_goal.empty()) {
            state.intent_route.navigation_goal = truncate_runtime_text(objective, 120);
        }
        if (json.contains("stop_condition") && json["stop_condition"].is_object()) {
            const auto& stop = json["stop_condition"];
            StopConditionSpec spec;
            spec.page_predicates = json_string_array_or_empty(stop, "page_predicates");
            spec.content_predicates = json_string_array_or_empty(stop, "content_predicates");
            spec.success_signals = json_string_array_or_empty(stop, "success_signals");
            spec.failure_signals = json_string_array_or_empty(stop, "failure_signals");
            spec.requires_readout = stop.value("requires_readout", state.intent_route.stop_condition.requires_readout);
            if (!spec.page_predicates.empty() || !spec.content_predicates.empty() || !spec.success_signals.empty()) {
                if (spec.failure_signals.empty()) {
                    spec.failure_signals = state.intent_route.stop_condition.failure_signals;
                }
                state.intent_route.stop_condition = std::move(spec);
            }
        }
        state.route_ready = true;
        state.route_resolved_by_llm = true;
        state.readout_context.readout_goal = state.intent_route.readout_goal;
        state.readout_context.selected_skill = state.intent_route.selected_skill;
        ICRAW_LOG_INFO("[AgentLoop][route_request_applied] task_type={} selected_skill={} navigation_goal={} requires_readout={}",
                state.intent_route.task_type,
                state.intent_route.selected_skill,
                state.intent_route.navigation_goal,
                state.intent_route.stop_condition.requires_readout);
        return true;
    } catch (...) {
        return false;
    }
}

void maybe_resolve_route_with_llm(ExecutionState& state,
                                  const AgentConfig& config,
                                  LLMProvider* llm_provider,
                                  const std::string& objective,
                                  const std::vector<SkillMetadata>& selected_skills) {
    if (llm_provider == nullptr || state.active_hints || state.route_resolved_by_llm) {
        return;
    }
    ChatCompletionRequest route_request = build_compact_route_chat_request(config, objective, selected_skills);
    const auto route_profile = resolve_llm_request_profile(config, state, route_request);
    apply_llm_request_profile(route_request, route_profile);
    ICRAW_LOG_INFO("[AgentLoop][route_request_start] profile={} skill_count={} payload_chars={}",
            route_request.request_profile, selected_skills.size(), total_payload_length(route_request.messages));
    const auto start = std::chrono::steady_clock::now();
    const auto response = llm_provider->chat_completion(route_request);
    const auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - start).count();
    const bool applied = apply_route_json_to_execution_state(state, objective, response.content);
    ICRAW_LOG_INFO("[AgentLoop][route_request_complete] applied={} duration_ms={} content_chars={}",
            applied, duration_ms, response.content.size());
}
std::string extract_tool_result_content(const Message& message) {
    for (const auto& block : message.content) {
        if (block.type == "tool_result") {
            return block.content;
        }
    }
    return "";
}

std::optional<std::string> find_latest_tool_result_content(const std::vector<Message>& messages,
                                                           const std::string& tool_name) {
    const auto tool_name_by_id = build_tool_name_by_id(messages);
    const int latest_index = find_latest_tool_message_index(messages, tool_name_by_id, tool_name);
    if (latest_index < 0 || latest_index >= static_cast<int>(messages.size())) {
        return std::nullopt;
    }
    const std::string content = extract_tool_result_content(messages[static_cast<size_t>(latest_index)]);
    if (content.empty()) {
        return std::nullopt;
    }
    return content;
}

ToolCall build_navigation_observation_call(const ExecutionState& state) {
    ToolCall tool_call;
    tool_call.id = generate_tool_id();
    tool_call.name = "android_view_context_tool";
    tool_call.arguments = nlohmann::json::object();
    std::string target_hint;
    if (state.pending_step.is_object() && state.pending_step.contains("target")
            && state.pending_step["target"].is_string()) {
        target_hint = trim_whitespace(state.pending_step["target"].get<std::string>());
    }
    if (target_hint.empty() && !state.last_observation_target_hint.empty()) {
        target_hint = state.last_observation_target_hint;
    }
    if (target_hint.empty() && state.route_ready) {
        target_hint = state.intent_route.navigation_goal;
    }
    if (!target_hint.empty()) {
        tool_call.arguments["targetHint"] = target_hint;
    }
    return tool_call;
}

void normalize_tool_call_arguments(ToolCall& tool_call) {
    if (!tool_call.arguments.is_object()) {
        return;
    }
    if (tool_call.name != "android_gesture_tool") {
        return;
    }
    if (!tool_call.arguments.contains("observation")
            || !tool_call.arguments["observation"].is_string()) {
        return;
    }
    try {
        const auto parsed = nlohmann::json::parse(
                tool_call.arguments["observation"].get<std::string>());
        if (parsed.is_object()) {
            tool_call.arguments["observation"] = parsed;
            ICRAW_LOG_INFO(
                    "[AgentLoop][gesture_observation_normalized] tool_id={} action={}",
                    tool_call.id,
                    first_string_value(tool_call.arguments, {"action"}));
        }
    } catch (...) {
        // Keep the original payload and let downstream validation reject it if needed.
    }
}

Message build_assistant_tool_call_message(const ToolCall& tool_call) {
    Message assistant_msg;
    assistant_msg.role = "assistant";
    ToolCallForMessage tc_msg;
    tc_msg.id = tool_call.id;
    tc_msg.type = "function";
    tc_msg.function_name = tool_call.name;
    tc_msg.function_arguments = tool_call.arguments.dump();
    assistant_msg.tool_calls.push_back(std::move(tc_msg));
    return assistant_msg;
}

std::set<std::string> collect_allowed_tool_names(const ChatCompletionRequest& request) {
    std::set<std::string> names;
    for (const auto& tool : request.tools) {
        if (!tool.is_object() || !tool.contains("function") || !tool["function"].is_object()) {
            continue;
        }
        const std::string name = tool["function"].value("name", "");
        if (!name.empty()) {
            names.insert(name);
        }
    }
    return names;
}

std::vector<ToolCall> filter_tool_calls_for_request(const std::vector<ToolCall>& tool_calls,
                                                    const ChatCompletionRequest& request,
                                                    const ExecutionState& state) {
    std::vector<ToolCall> filtered;
    const auto allowed_tool_names = collect_allowed_tool_names(request);
    const bool forbid_tool_calls = request.tools.empty() || state.goal_reached || state.phase == "readout";
    for (const auto& original_tool_call : tool_calls) {
        ToolCall tool_call = original_tool_call;
        if (forbid_tool_calls) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][tool_call_rejected] reason=phase_disallows_tools phase={} goal_reached={} tool_name={}",
                    state.phase,
                    state.goal_reached,
                    tool_call.name);
            continue;
        }
        if (!allowed_tool_names.empty() && allowed_tool_names.count(tool_call.name) == 0) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][tool_call_rejected] reason=tool_not_in_request tool_name={} allowed_count={}",
                    tool_call.name,
                    allowed_tool_names.size());
            continue;
        }
        if (tool_call.name == "android_gesture_tool"
                && state.active_hints
                && state.pending_step_index >= 0
                && state.pending_step_index < static_cast<int>(state.active_hints->steps.size())) {
            const auto& pending_step =
                    state.active_hints->steps[static_cast<size_t>(state.pending_step_index)];
            if (step_attempt_limit_reached(state, pending_step, state.pending_step_index)) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][tool_call_rejected] reason=max_attempts_reached tool_name={} step_index={} target={} attempt_count={} max_attempts={}",
                        tool_call.name,
                        state.pending_step_index,
                        truncate_runtime_text(pending_step.target, 80),
                        step_action_attempt_count(state, state.pending_step_index),
                        pending_step.max_attempts);
                continue;
            }
            if (is_pull_refresh_swipe_tool_call(tool_call)
                    && should_guard_pull_refresh_for_pending_step(pending_step)) {
                if (step_supports_forward_scroll_search(pending_step)
                        && tool_call.arguments.is_object()) {
                    const std::string old_direction = first_string_value(
                            tool_call.arguments, {"direction", "scrollDirection", "swipeDirection"});
                    tool_call.arguments["direction"] = "down";
                    ICRAW_LOG_INFO(
                            "[AgentLoop][tool_call_direction_rewritten] reason=avoid_pull_refresh old_direction={} new_direction=down step_index={} target={} anchor_type={} container_role={}",
                            old_direction,
                            state.pending_step_index,
                            truncate_runtime_text(pending_step.target, 80),
                            truncate_runtime_text(pending_step.anchor_type, 60),
                            truncate_runtime_text(pending_step.container_role, 60));
                } else {
                    ICRAW_LOG_INFO(
                            "[AgentLoop][tool_call_rejected] reason=unsafe_pull_refresh_swipe tool_name={} step_index={} target={} action={}",
                            tool_call.name,
                            state.pending_step_index,
                            truncate_runtime_text(pending_step.target, 80),
                            pending_step.action);
                    continue;
                }
            }
        }
        filtered.push_back(tool_call);
    }
    return filtered;
}

std::string build_readout_retry_instruction(const ExecutionState& state) {
    std::ostringstream prompt;
    prompt << "Tools are disabled for the current readout stage. ";
    prompt << "Answer directly in plain text using only the current page observation. ";
    prompt << "Do not call any tool. ";
    if (!state.readout_context.readout_goal.empty()) {
        prompt << "Readout goal: "
               << truncate_runtime_text(state.readout_context.readout_goal, 160) << ". ";
    }
    if (!state.current_page.empty()) {
        prompt << "Current page: "
               << truncate_runtime_text(state.current_page, 120) << ".";
    }
    return prompt.str();
}

std::string build_local_readout_fallback_text(const ExecutionState& state) {
    try {
        const std::string summary_text = !state.latest_readout_observation_summary.empty()
                ? state.latest_readout_observation_summary
                : state.latest_navigation_observation_summary;
        if (summary_text.empty()) {
            return "";
        }

        const auto json = nlohmann::json::parse(summary_text);
        if (!json.is_object()) {
            return "";
        }

        std::string page_name = json.value("activityClassName", "");
        std::string summary;
        std::vector<std::string> visible_items;
        std::vector<std::string> key_actions;

        if (json.contains("hybridObservation") && json["hybridObservation"].is_object()) {
            const auto& hybrid = json["hybridObservation"];
            summary = hybrid.value("summary", "");
            if (hybrid.contains("page") && hybrid["page"].is_object()) {
                page_name = first_string_value(hybrid["page"], {"title", "name"});
            }
            if (hybrid.contains("visibleItems") && hybrid["visibleItems"].is_array()) {
                for (const auto& item : hybrid["visibleItems"]) {
                    if (item.is_string()) {
                        visible_items.push_back(item.get<std::string>());
                    }
                }
            }
            if (hybrid.contains("keyActions") && hybrid["keyActions"].is_array()) {
                for (const auto& item : hybrid["keyActions"]) {
                    if (item.is_string()) {
                        key_actions.push_back(item.get<std::string>());
                    }
                }
            }
        }
        if (summary.empty() && json.contains("screenVisionCompact") && json["screenVisionCompact"].is_object()) {
            summary = json["screenVisionCompact"].value("summary", "");
        }

        std::ostringstream response;
        if (!page_name.empty()) {
            response << "Current page: " << truncate_runtime_text(page_name, 80) << ". ";
        }
        if (!summary.empty()) {
            response << "Page summary: " << truncate_runtime_text(summary, 140) << ". ";
        }
        if (!visible_items.empty()) {
            response << "Visible content: ";
            for (size_t i = 0; i < visible_items.size() && i < 6; ++i) {
                if (i > 0) {
                    response << ", ";
                }
                response << truncate_runtime_text(visible_items[i], 24);
            }
            response << ". ";
        }
        if (!key_actions.empty()) {
            response << "Available actions: ";
            for (size_t i = 0; i < key_actions.size() && i < 4; ++i) {
                if (i > 0) {
                    response << ", ";
                }
                response << truncate_runtime_text(key_actions[i], 24);
            }
            response << ".";
        }
        return truncate_runtime_text(response.str(), 320);
    } catch (...) {
        return "";
    }
}

ChatCompletionRequest build_turn_request(const AgentConfig& agent_config,
                                          bool stream,
                                          const std::string& system_prompt,
                                          const std::vector<Message>& history,
                                          const std::string& user_message) {
    ChatCompletionRequest request;
    request.model = agent_config.model;
    request.temperature = agent_config.temperature;
    request.max_tokens = agent_config.max_tokens;
    request.enable_thinking = agent_config.enable_thinking;
    request.stream = stream;

    request.messages.emplace_back("system", system_prompt);
    for (const auto& msg : history) {
        request.messages.push_back(msg);
    }
    request.messages.emplace_back("user", user_message);
    return request;
}

void log_route_resolved_if_needed(const ExecutionState& execution_state, const std::string& mode) {
    if (!execution_state.route_ready) {
        return;
    }

    if (mode == "stream") {
        ICRAW_LOG_INFO(
                "[AgentLoop][route_resolved] mode=stream task_type={} selected_skill={} navigation_goal={} readout_goal={} stop_requires_readout={}",
                execution_state.intent_route.task_type,
                execution_state.intent_route.selected_skill,
                execution_state.intent_route.navigation_goal,
                execution_state.intent_route.readout_goal,
                execution_state.intent_route.stop_condition.requires_readout);
        return;
    }

    ICRAW_LOG_INFO(
            "[AgentLoop][route_resolved] task_type={} selected_skill={} navigation_goal={} readout_goal={} stop_requires_readout={}",
            execution_state.intent_route.task_type,
            execution_state.intent_route.selected_skill,
            execution_state.intent_route.navigation_goal,
            execution_state.intent_route.readout_goal,
            execution_state.intent_route.stop_condition.requires_readout);
}

std::string serialize_tool_call_arguments(const ToolCall& tool_call) {
    return tool_call.arguments.is_string()
            ? tool_call.arguments.get<std::string>()
            : tool_call.arguments.dump();
}

void append_tool_calls_to_assistant_message(Message& assistant_msg,
                                            const std::vector<ToolCall>& tool_calls,
                                            const AgentEventCallback& callback = AgentEventCallback{}) {
    for (const auto& tc : tool_calls) {
        const std::string tool_id = tc.id.empty() ? generate_tool_id() : tc.id;
        ToolCallForMessage tc_msg;
        tc_msg.id = tool_id;
        tc_msg.type = "function";
        tc_msg.function_name = tc.name;
        tc_msg.function_arguments = serialize_tool_call_arguments(tc);
        assistant_msg.tool_calls.push_back(std::move(tc_msg));

        if (callback) {
            AgentEvent event;
            event.type = "tool_use";
            event.data["id"] = tool_id;
            event.data["name"] = tc.name;
            event.data["input"] = tc.arguments;
            callback(event);
        }
    }
}

ChatCompletionRequest build_effective_request_for_iteration(const ChatCompletionRequest& request,
                                                            const std::string& user_message,
                                                            const std::vector<ToolSchema>& tool_schemas,
                                                            const std::vector<SkillMetadata>& selected_skills,
                                                            ExecutionState& execution_state,
                                                            const AgentConfig& agent_config,
                                                            int iteration,
                                                            const std::string& mode) {
    const bool has_navigation_escalation = !execution_state.latest_escalation.reason.empty();
    const bool use_compact_navigation_escalation_request =
            COMPACT_NAVIGATION_ESCALATION_ENABLED && has_navigation_escalation;
    if (has_navigation_escalation && !use_compact_navigation_escalation_request) {
        ICRAW_LOG_INFO(
                "[AgentLoop][navigation_escalation_compact_disabled] mode={} iteration={} reason={}",
                mode,
                iteration,
                execution_state.latest_escalation.reason);
    }

    ChatCompletionRequest effective_request = use_compact_navigation_escalation_request
            ? build_compact_navigation_escalation_chat_request(
                    request, user_message, tool_schemas, execution_state)
            : request;
    if (!use_compact_navigation_escalation_request) {
        rebuild_tools_for_phase(tool_schemas, execution_state, effective_request);
        inject_selected_skills_into_request(effective_request, selected_skills, iteration);
        inject_navigation_escalation_into_request(effective_request, execution_state);
        const std::string execution_state_prompt =
                build_execution_state_prompt_v2(execution_state, iteration);
        if (!execution_state_prompt.empty()) {
            inject_execution_state_into_request(effective_request, execution_state_prompt);
            ICRAW_LOG_INFO("[AgentLoop][execution_state_injected] mode={} iteration={} prompt_length={}",
                    mode, iteration, execution_state_prompt.size());
        }
    } else {
        ICRAW_LOG_INFO("[AgentLoop][navigation_escalation_request_selected] mode={} iteration={} reason={}",
                mode, iteration, execution_state.latest_escalation.reason);
    }

    ICRAW_LOG_INFO("[AgentLoop][execution_state_mode] mode={} phase={} iteration={}",
            execution_state.mode, execution_state.phase, iteration);

    const auto llm_request_profile =
            resolve_llm_request_profile(agent_config, execution_state, effective_request);
    apply_llm_request_profile(effective_request, llm_request_profile);
    ICRAW_LOG_INFO("[AgentLoop][request_profile] mode={} iteration={} profile={} max_tokens={} temperature={}",
            mode,
            iteration,
            effective_request.request_profile,
            effective_request.max_tokens,
            effective_request.temperature);
    return effective_request;
}

}  // namespace


