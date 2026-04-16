#pragma once

// Internal AgentLoop implementation chunk. Included only by agent_loop.cpp.

void append_runtime_section_to_request_system(ChatCompletionRequest& request,
                                              const std::string& heading,
                                              const std::string& body) {
    if (body.empty()) {
        return;
    }
    for (auto& msg : request.messages) {
        if (msg.role != "system") {
            continue;
        }
        std::string merged = msg.text();
        if (!merged.empty() && merged.back() != '\n') {
            merged += '\n';
        }
        merged += "\n[" + heading + "]\n";
        merged += body;
        msg.content.clear();
        msg.content.push_back(ContentBlock::make_text(std::move(merged)));
        return;
    }
    request.messages.insert(request.messages.begin(), Message("system", body));
}

std::string build_skill_summary_prompt(const SkillMetadata& skill) {
    std::ostringstream stream;
    stream << "## Skill: " << skill.name << "\n";
    if (!skill.description.empty()) {
        stream << "Description: " << truncate_runtime_text(skill.description, 220) << "\n";
    }
    if (skill.execution_hints.is_object()) {
        const auto parsed = parse_execution_hints(std::vector<SkillMetadata>{skill});
        if (parsed && !parsed->steps.empty()) {
            stream << "Execution hints:\n";
            for (size_t i = 0; i < parsed->steps.size(); ++i) {
                stream << i + 1 << ". " << describe_step(parsed->steps[i]) << "\n";
            }
        }
        if (skill.execution_hints.contains("stop_condition")
                && skill.execution_hints["stop_condition"].is_object()) {
            const auto& stop = skill.execution_hints["stop_condition"];
            auto append_values = [&](const std::string& label,
                                     const std::vector<std::string>& values) {
                if (values.empty()) {
                    return;
                }
                stream << label << ": ";
                for (size_t i = 0; i < values.size(); ++i) {
                    if (i > 0) {
                        stream << ", ";
                    }
                    stream << truncate_runtime_text(values[i], 40);
                }
                stream << "\n";
            };
            append_values("Success signals", string_array_values(stop,
                    {"success_signals", "successSignals", "success"}));
            append_values("Failure signals", string_array_values(stop,
                    {"failure_signals", "failureSignals", "failure"}));
        }
    }
    return truncate_runtime_text(stream.str(), SKILL_SUMMARY_MAX_CHARS);
}

void inject_selected_skills_into_request(ChatCompletionRequest& request,
                                         const std::vector<SkillMetadata>& selected_skills,
                                         int iteration) {
    if (selected_skills.empty()) {
        return;
    }

    std::ostringstream body;
    body << "Matched skill summaries. Full SKILL.md content is intentionally omitted; ";
    body << "use the structured execution hints and request escalation only if more detail is required.\n\n";

    size_t injected_count = 0;
    for (const auto& skill : selected_skills) {
        if (injected_count >= SKILL_PRELOAD_MAX_COUNT) {
            break;
        }
        body << build_skill_summary_prompt(skill) << "\n";
        injected_count++;
    }

    const std::string section = body.str();
    append_runtime_section_to_request_system(request, "Selected Skills", section);
    ICRAW_LOG_INFO(
            "[AgentLoop][skill_context_injected] iteration={} skill_count={} full_content_injected=false chars={}",
            iteration,
            injected_count,
            section.size());
}

std::string summarize_pending_step_json(const nlohmann::json& step);

void inject_navigation_escalation_into_request(ChatCompletionRequest& request,
                                               const ExecutionState& state) {
    if (state.latest_escalation.reason.empty()) {
        return;
    }
    std::ostringstream body;
    body << "Local deterministic navigation could not proceed safely.\n";
    body << "reason: " << state.latest_escalation.reason << "\n";
    if (!state.latest_escalation.detail.empty()) {
        body << "detail: " << truncate_runtime_text(state.latest_escalation.detail, 220) << "\n";
    }
    if (state.navigation_checkpoint.current_step_index >= 0) {
        body << "checkpoint_step_index: " << state.navigation_checkpoint.current_step_index << "\n";
    }
    body << "stagnant_rounds: " << state.navigation_checkpoint.stagnant_rounds << "\n";
    if (state.pending_step.is_object() && !state.pending_step.empty()) {
        body << "pending_step: " << summarize_pending_step_json(state.pending_step) << "\n";
    }
    if (!state.latest_navigation_observation_summary.empty()) {
        body << "observation_summary: "
             << truncate_runtime_text(state.latest_navigation_observation_summary, 320) << "\n";
    }
    if (!state.latest_canonical_candidate_summary.empty()) {
        body << "canonical_candidates: "
             << truncate_runtime_text(state.latest_canonical_candidate_summary, 240) << "\n";
    }
    append_runtime_section_to_request_system(request, "Navigation Escalation", body.str());
    ICRAW_LOG_INFO(
            "[AgentLoop][navigation_escalation_summary_chars] chars={}",
            body.str().size());
}

std::string summarize_pending_step_json(const nlohmann::json& step) {
    if (!step.is_object() || step.empty()) {
        return "";
    }
    std::vector<std::string> parts;
    auto append = [&](const std::string& key, const std::string& label) {
        if (step.contains(key) && step[key].is_string()) {
            const std::string value = trim_whitespace(step[key].get<std::string>());
            if (!value.empty()) {
                parts.push_back(label + "=" + truncate_runtime_text(value, 80));
            }
        }
    };
    append("action", "action");
    append("target", "target");
    append("page", "page");
    append("activity", "activity");
    append("region", "region");
    append("anchor_type", "anchor");
    append("container_role", "container");
    if (step.contains("aliases") && step["aliases"].is_array() && !step["aliases"].empty()) {
        std::ostringstream aliases;
        size_t count = 0;
        for (const auto& alias : step["aliases"]) {
            if (!alias.is_string()) {
                continue;
            }
            if (count > 0) {
                aliases << ",";
            }
            aliases << truncate_runtime_text(alias.get<std::string>(), 40);
            count++;
            if (count >= 3) {
                break;
            }
        }
        if (count > 0) {
            parts.push_back("aliases=" + aliases.str());
        }
    }
    std::ostringstream stream;
    for (size_t i = 0; i < parts.size(); ++i) {
        if (i > 0) {
            stream << "; ";
        }
        stream << parts[i];
    }
    return truncate_runtime_text(stream.str(), 260);
}

std::string build_navigation_trace_summary_text(const ExecutionState& state) {
    std::ostringstream trace;
    trace << "current_page=" << truncate_runtime_text(state.current_page, 80);
    trace << "; step_index=" << state.pending_step_index;
    const std::string pending = summarize_pending_step_json(state.pending_step);
    if (!pending.empty()) {
        trace << "; pending={" << pending << "}";
    }
    if (!state.latest_action_result.empty()) {
        trace << "; last_action=" << truncate_runtime_text(state.latest_action_result, 120);
    }
    if (!state.latest_observation_summary.empty()) {
        trace << "; observation=" << truncate_runtime_text(state.latest_observation_summary, 120);
    }
    if (!state.latest_canonical_candidate_summary.empty()) {
        trace << "; canonical=" << truncate_runtime_text(state.latest_canonical_candidate_summary, 180);
    }
    return trace.str();
}
void refresh_navigation_trace_summary(ExecutionState& state) {
    state.latest_navigation_trace.current_page = state.current_page;
    state.latest_navigation_trace.current_step_index = state.pending_step_index;
    state.latest_navigation_trace.pending_target = first_string_value(state.pending_step, {"target", "page", "activity"});
    state.latest_navigation_trace.latest_action_result = state.latest_action_result;
    state.latest_navigation_trace.latest_observation_summary = state.latest_observation_summary;
    state.latest_navigation_trace.summary = build_navigation_trace_summary_text(state);
}
std::string build_execution_state_prompt_v2(const ExecutionState& state, int iteration) {
    if (iteration <= 1) {
        return "";
    }

    std::ostringstream prompt;
    prompt << "goal: " << state.goal << "\n";
    prompt << "mode: " << state.mode << "\n";
    prompt << "phase: " << state.phase << "\n";
    if (!state.current_page.empty()) {
        prompt << "current_page: " << truncate_runtime_text(state.current_page, 180) << "\n";
    }
    if (!state.selected_skills.empty()) {
        prompt << "selected_skills: ";
        for (size_t i = 0; i < state.selected_skills.size(); ++i) {
            if (i > 0) {
                prompt << ", ";
            }
            prompt << state.selected_skills[i];
        }
        prompt << "\n";
    }
    if (state.route_ready) {
        prompt << "route.task_type: " << state.intent_route.task_type << "\n";
        if (!state.intent_route.navigation_goal.empty()) {
            prompt << "route.navigation_goal: "
                   << truncate_runtime_text(state.intent_route.navigation_goal, 180) << "\n";
        }
        if (!state.intent_route.readout_goal.empty()) {
            prompt << "route.readout_goal: "
                   << truncate_runtime_text(state.intent_route.readout_goal, 180) << "\n";
        }
    }
    if (!state.completed_steps.empty()) {
        prompt << "completed_steps:\n";
        const size_t begin = state.completed_steps.size() > EXECUTION_STATE_MAX_TOOL_EVENTS
                ? state.completed_steps.size() - EXECUTION_STATE_MAX_TOOL_EVENTS
                : 0;
        for (size_t i = begin; i < state.completed_steps.size(); ++i) {
            prompt << "- " << truncate_runtime_text(state.completed_steps[i], 160) << "\n";
        }
    }
    if (state.pending_step.is_object() && !state.pending_step.empty()) {
        prompt << "pending_step: " << summarize_pending_step_json(state.pending_step) << "\n";
    }
    if (state.awaiting_step_confirmation_index >= 0) {
        prompt << "awaiting_confirmation: true";
        if (!state.awaiting_confirmation_target.empty()) {
            prompt << " target=" << truncate_runtime_text(state.awaiting_confirmation_target, 120);
        }
        prompt << "\n";
    }
    if (!state.latest_observation_summary.empty()) {
        prompt << "latest_observation: " << truncate_runtime_text(state.latest_observation_summary, 180) << "\n";
    }
    if (!state.latest_canonical_candidate_summary.empty()) {
        prompt << "canonical_candidates: "
               << truncate_runtime_text(state.latest_canonical_candidate_summary, 220) << "\n";
    }
    if (!state.latest_action_result.empty()) {
        prompt << "latest_action_result: " << truncate_runtime_text(state.latest_action_result, 180) << "\n";
    }
    if (!state.latest_escalation.reason.empty()) {
        prompt << "navigation_escalation.reason: " << state.latest_escalation.reason << "\n";
        if (!state.latest_escalation.detail.empty()) {
            prompt << "navigation_escalation.detail: "
                   << truncate_runtime_text(state.latest_escalation.detail, 140) << "\n";
        }
    }
    if (state.navigation_checkpoint.current_step_index >= 0) {
        prompt << "navigation_checkpoint.step_index: " << state.navigation_checkpoint.current_step_index << "\n";
        prompt << "navigation_checkpoint.stagnant_rounds: " << state.navigation_checkpoint.stagnant_rounds << "\n";
    }
    if (!state.latest_observation_fingerprint.value.empty()) {
        prompt << "observation_fingerprint: "
               << truncate_runtime_text(state.latest_observation_fingerprint.value, 160) << "\n";
    }
    if (!state.latest_navigation_trace.summary.empty()) {
        prompt << "navigation_trace: "
               << truncate_runtime_text(state.latest_navigation_trace.summary, 220) << "\n";
    }
    prompt << "policy:\n";
    prompt << "- Continue from confirmed progress instead of replanning from scratch.\n";
    prompt << "- Prefer the next concrete action that advances the same objective.\n";
    if (state.awaiting_step_confirmation_index >= 0) {
        prompt << "- The previous UI action is not confirmed yet. First inspect the current page before advancing the plan.\n";
    }
    if (state.goal_reached || state.phase == "readout") {
        prompt << "- The goal page is already reached. Focus on reading and summarizing the current page only.\n";
    }
    return prompt.str();
}

std::unordered_map<std::string, std::string> build_tool_name_by_id(
        const std::vector<Message>& messages) {
    std::unordered_map<std::string, std::string> tool_names_by_id;
    for (const auto& message : messages) {
        if (message.role != "assistant") {
            continue;
        }
        for (const auto& tool_call : message.tool_calls) {
            if (!tool_call.id.empty() && !tool_call.function_name.empty()) {
                tool_names_by_id[tool_call.id] = tool_call.function_name;
            }
        }
    }
    return tool_names_by_id;
}

int find_latest_tool_message_index(const std::vector<Message>& messages,
                                   const std::unordered_map<std::string, std::string>& tool_name_by_id,
                                   const std::string& tool_name) {
    for (int i = static_cast<int>(messages.size()) - 1; i >= 0; --i) {
        const auto& message = messages[static_cast<size_t>(i)];
        if (message.role != "tool") {
            continue;
        }
        const auto it = tool_name_by_id.find(message.tool_call_id);
        if (it != tool_name_by_id.end() && it->second == tool_name) {
            return i;
        }
    }
    return -1;
}

size_t message_payload_length(const Message& message) {
    size_t length = message.role.size();
    for (const auto& block : message.content) {
        length += block.text.size();
        length += block.content.size();
        length += block.image_url.size();
    }
    for (const auto& tool_call : message.tool_calls) {
        length += tool_call.function_name.size();
        length += tool_call.function_arguments.size();
    }
    return length;
}

size_t total_payload_length(const std::vector<Message>& messages) {
    size_t total = 0;
    for (const auto& message : messages) {
        total += message_payload_length(message);
    }
    return total;
}

}  // namespace

namespace {

std::string build_observation_result_summary(const std::string& content) {
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return truncate_runtime_text(content, TOOL_RESULT_SUMMARY_MAX_CHARS);
        }

        nlohmann::json summary = nlohmann::json::object();
        if (json.contains("success")) {
            summary["success"] = json["success"];
        }
        if (json.contains("activityClassName")) {
            summary["activityClassName"] = json["activityClassName"];
        }
        if (json.contains("source")) {
            summary["source"] = json["source"];
        }
        if (json.contains("visualObservationMode")) {
            summary["visualObservationMode"] = json["visualObservationMode"];
        }
        if (json.contains("snapshotId")) {
            summary["snapshotId"] = json["snapshotId"];
        }
        if (json.contains("hybridObservation") && json["hybridObservation"].is_object()) {
            const auto& hybrid = json["hybridObservation"];
            nlohmann::json hybrid_summary = nlohmann::json::object();
            if (hybrid.contains("summary")) {
                hybrid_summary["summary"] = hybrid["summary"];
            }
            if (hybrid.contains("quality")) {
                hybrid_summary["quality"] = hybrid["quality"];
            }
            if (hybrid.contains("actionableNodes") && hybrid["actionableNodes"].is_array()) {
                nlohmann::json labels = nlohmann::json::array();
                for (size_t i = 0; i < hybrid["actionableNodes"].size()
                        && i < OBSERVATION_SUMMARY_ACTIONABLE_COUNT; ++i) {
                    const auto& node = hybrid["actionableNodes"][i];
                    std::string label = first_string_value(node,
                            {"text", "visionLabel", "resourceId", "className", "id"});
                    if (!label.empty()) {
                        labels.push_back(label);
                    }
                }
                hybrid_summary["topActionableLabels"] = labels;
            }
            summary["hybridObservation"] = hybrid_summary;
        }
        return truncate_runtime_text(summary.dump(), TOOL_RESULT_SUMMARY_MAX_CHARS);
    } catch (...) {
        return truncate_runtime_text(content, TOOL_RESULT_SUMMARY_MAX_CHARS);
    }
}

std::string build_planned_advance_observation_summary(const std::string& content) {
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return prune_tool_result(content, PLANNED_ADVANCE_OBSERVATION_MAX_CHARS);
        }

        nlohmann::json summary = nlohmann::json::object();
        for (const auto& key : {
                     "success",
                     "activityClassName",
                     "source",
                     "observationMode",
                     "visualObservationMode",
                     "snapshotId",
                     "targetHint"
             }) {
            if (json.contains(key)) {
                summary[key] = json[key];
            }
        }

        if (json.contains("hybridObservation") && json["hybridObservation"].is_object()) {
            const auto& hybrid = json["hybridObservation"];
            nlohmann::json hybrid_summary = nlohmann::json::object();
            for (const auto& key : {"summary", "executionHint", "page", "quality"}) {
                if (hybrid.contains(key)) {
                    hybrid_summary[key] = hybrid[key];
                }
            }
            if (hybrid.contains("actionableNodes") && hybrid["actionableNodes"].is_array()) {
                nlohmann::json actionable = nlohmann::json::array();
                for (size_t i = 0; i < hybrid["actionableNodes"].size() && i < 8; ++i) {
                    const auto& node = hybrid["actionableNodes"][i];
                    if (!node.is_object()) {
                        continue;
                    }
                    nlohmann::json compact_node = nlohmann::json::object();
                    for (const auto& key : {
                                 "text",
                                 "visionLabel",
                                 "resourceId",
                                 "className",
                                 "id",
                                 "source",
                                 "score",
                                 "bounds",
                                 "nativeNodeIndex"
                         }) {
                        if (node.contains(key)) {
                            compact_node[key] = node[key];
                        }
                    }
                    actionable.push_back(std::move(compact_node));
                }
                hybrid_summary["actionableNodes"] = std::move(actionable);
            }
            if (hybrid.contains("conflicts") && hybrid["conflicts"].is_array()) {
                nlohmann::json conflicts = nlohmann::json::array();
                for (size_t i = 0; i < hybrid["conflicts"].size() && i < 4; ++i) {
                    const auto& conflict = hybrid["conflicts"][i];
                    if (!conflict.is_object()) {
                        continue;
                    }
                    nlohmann::json compact_conflict = nlohmann::json::object();
                    for (const auto& key : {"code", "severity", "message", "bounds"}) {
                        if (conflict.contains(key)) {
                            compact_conflict[key] = conflict[key];
                        }
                    }
                    conflicts.push_back(std::move(compact_conflict));
                }
                hybrid_summary["conflicts"] = std::move(conflicts);
            }
            summary["hybridObservation"] = std::move(hybrid_summary);
        }

        if (json.contains("screenVisionCompact")) {
            const auto& compact = json["screenVisionCompact"];
            if (compact.is_object()) {
                nlohmann::json compact_summary = nlohmann::json::object();
                for (const auto& key : {"summary", "page"}) {
                    if (compact.contains(key)) {
                        compact_summary[key] = compact[key];
                    }
                }
                summary["screenVisionCompact"] = std::move(compact_summary);
            }
        }

        return truncate_runtime_text(summary.dump(), PLANNED_ADVANCE_OBSERVATION_MAX_CHARS);
    } catch (...) {
        return prune_tool_result(content, PLANNED_ADVANCE_OBSERVATION_MAX_CHARS);
    }
}

std::string join_string_values(const std::vector<std::string>& values,
                               const std::string& separator) {
    std::ostringstream stream;
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            stream << separator;
        }
        stream << values[i];
    }
    return stream.str();
}

std::vector<std::string> collect_unique_readout_labels(const nlohmann::json& actionable_nodes,
                                                       size_t max_count) {
    std::vector<std::string> labels;
    std::set<std::string> seen;
    if (!actionable_nodes.is_array()) {
        return labels;
    }
    for (const auto& node : actionable_nodes) {
        if (!node.is_object()) {
            continue;
        }
        const bool decorative_like = node.value("decorativeLike", false);
        const bool numeric_like = node.value("numericLike", false);
        const std::string label = trim_whitespace(first_string_value(
                node, {"text", "visionLabel", "contentDescription", "content_description"}));
        if (label.empty() || decorative_like || numeric_like) {
            continue;
        }
        const std::string normalized = normalize_for_runtime_match(label);
        if (normalized.empty() || seen.count(normalized) > 0) {
            continue;
        }
        seen.insert(normalized);
        labels.push_back(truncate_runtime_text(label, 48));
        if (labels.size() >= max_count) {
            break;
        }
    }
    return labels;
}

struct ReadoutTextEntry {
    std::string text;
    std::string source;
    int left = 0;
    int top = 0;
    int right = 0;
    int bottom = 0;
    bool has_bbox = false;
};

bool parse_json_bbox(const nlohmann::json& bbox,
                     int& left,
                     int& top,
                     int& right,
                     int& bottom) {
    if (bbox.is_array() && bbox.size() >= 4) {
        left = bbox[0].get<int>();
        top = bbox[1].get<int>();
        right = bbox[2].get<int>();
        bottom = bbox[3].get<int>();
        return true;
    }
    if (bbox.is_string()) {
        return std::sscanf(bbox.get<std::string>().c_str(),
                "[%d,%d][%d,%d]",
                &left,
                &top,
                &right,
                &bottom) == 4;
    }
    return false;
}

std::string readout_text_from_object(const nlohmann::json& item) {
    if (item.is_string()) {
        return item.get<std::string>();
    }
    if (!item.is_object()) {
        return "";
    }
    return first_string_value(item, {
            "summaryText",
            "text",
            "label",
            "title",
            "name",
            "contentDescription",
            "content_description"
    });
}

bool is_low_value_readout_text(const std::string& text) {
    const std::string normalized = normalize_for_runtime_match(text);
    return normalized.empty()
            || normalized == "iconbutton"
            || normalized == "button"
            || normalized == "imageview"
            || normalized == "textview";
}

nlohmann::json collect_ordered_readout_entries(const nlohmann::json& array,
                                               const std::string& source,
                                               size_t max_count) {
    nlohmann::json result = nlohmann::json::array();
    if (!array.is_array()) {
        return result;
    }

    std::vector<ReadoutTextEntry> entries;
    std::set<std::string> seen;
    for (const auto& item : array) {
        const std::string text = trim_whitespace(readout_text_from_object(item));
        if (is_low_value_readout_text(text)) {
            continue;
        }
        const std::string normalized = normalize_for_runtime_match(text);
        if (seen.count(normalized) > 0) {
            continue;
        }
        seen.insert(normalized);

        ReadoutTextEntry entry;
        entry.text = truncate_runtime_text(text, 96);
        entry.source = source;
        if (item.is_object()) {
            if (item.contains("bbox")) {
                entry.has_bbox = parse_json_bbox(item["bbox"],
                        entry.left,
                        entry.top,
                        entry.right,
                        entry.bottom);
            } else if (item.contains("bounds")) {
                entry.has_bbox = parse_json_bbox(item["bounds"],
                        entry.left,
                        entry.top,
                        entry.right,
                        entry.bottom);
            }
        }
        entries.push_back(std::move(entry));
    }

    std::sort(entries.begin(), entries.end(), [](const ReadoutTextEntry& left,
                                                 const ReadoutTextEntry& right) {
        if (left.has_bbox != right.has_bbox) {
            return left.has_bbox;
        }
        if (left.has_bbox && right.has_bbox) {
            if (left.top != right.top) {
                return left.top < right.top;
            }
            return left.left < right.left;
        }
        return left.text < right.text;
    });

    for (const auto& entry : entries) {
        if (result.size() >= max_count) {
            break;
        }
        nlohmann::json object = nlohmann::json::object();
        object["text"] = entry.text;
        object["source"] = entry.source;
        if (entry.has_bbox) {
            object["bbox"] = {entry.left, entry.top, entry.right, entry.bottom};
        }
        result.push_back(std::move(object));
    }
    return result;
}

std::string build_navigation_observation_summary(const std::string& content,
                                                 const std::string& canonical_candidate_summary) {
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return prune_tool_result(content, NAVIGATION_ESCALATION_OBSERVATION_MAX_CHARS);
        }

        nlohmann::json summary = nlohmann::json::object();
        for (const auto& key : {
                     "success",
                     "activityClassName",
                     "source",
                     "observationMode",
                     "visualObservationMode",
                     "snapshotId",
                     "targetHint"
             }) {
            if (json.contains(key)) {
                summary[key] = json[key];
            }
        }

        if (json.contains("hybridObservation") && json["hybridObservation"].is_object()) {
            const auto& hybrid = json["hybridObservation"];
            nlohmann::json hybrid_summary = nlohmann::json::object();
            for (const auto& key : {"summary", "executionHint", "page", "quality"}) {
                if (hybrid.contains(key)) {
                    hybrid_summary[key] = hybrid[key];
                }
            }
            if (hybrid.contains("actionableNodes") && hybrid["actionableNodes"].is_array()) {
                nlohmann::json labels = nlohmann::json::array();
                const auto visible_labels =
                        collect_unique_readout_labels(hybrid["actionableNodes"], OBSERVATION_SUMMARY_ACTIONABLE_COUNT);
                for (const auto& label : visible_labels) {
                    labels.push_back(label);
                }
                if (!labels.empty()) {
                    hybrid_summary["topActionableLabels"] = std::move(labels);
                }
            }
            if (hybrid.contains("conflicts") && hybrid["conflicts"].is_array()) {
                nlohmann::json conflicts = nlohmann::json::array();
                for (size_t i = 0; i < hybrid["conflicts"].size() && i < 3; ++i) {
                    const auto& conflict = hybrid["conflicts"][i];
                    if (!conflict.is_object()) {
                        continue;
                    }
                    nlohmann::json compact_conflict = nlohmann::json::object();
                    for (const auto& key : {"code", "severity", "bounds"}) {
                        if (conflict.contains(key)) {
                            compact_conflict[key] = conflict[key];
                        }
                    }
                    conflicts.push_back(std::move(compact_conflict));
                }
                if (!conflicts.empty()) {
                    hybrid_summary["conflicts"] = std::move(conflicts);
                }
            }
            summary["hybridObservation"] = std::move(hybrid_summary);
        }

        if (!canonical_candidate_summary.empty()) {
            summary["canonicalCandidates"] = canonical_candidate_summary;
        }

        if (json.contains("screenVisionCompact") && json["screenVisionCompact"].is_object()) {
            const auto& compact = json["screenVisionCompact"];
            nlohmann::json compact_summary = nlohmann::json::object();
            for (const auto& key : {"summary", "page"}) {
                if (compact.contains(key)) {
                    compact_summary[key] = compact[key];
                }
            }
            if (!compact_summary.empty()) {
                summary["screenVisionCompact"] = std::move(compact_summary);
            }
        }

        return truncate_runtime_text(summary.dump(), NAVIGATION_ESCALATION_OBSERVATION_MAX_CHARS);
    } catch (...) {
        return prune_tool_result(content, NAVIGATION_ESCALATION_OBSERVATION_MAX_CHARS);
    }
}

std::string build_readout_observation_summary(const std::string& content) {
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return prune_tool_result(content, READOUT_OBSERVATION_MAX_CHARS);
        }

        nlohmann::json summary = nlohmann::json::object();
        for (const auto& key : {
                     "success",
                     "activityClassName",
                     "source",
                     "visualObservationMode",
                     "snapshotId"
             }) {
            if (json.contains(key)) {
                summary[key] = json[key];
            }
        }

        if (json.contains("hybridObservation") && json["hybridObservation"].is_object()) {
            const auto& hybrid = json["hybridObservation"];
            nlohmann::json hybrid_summary = nlohmann::json::object();
            for (const auto& key : {"summary", "executionHint", "quality"}) {
                if (hybrid.contains(key)) {
                    hybrid_summary[key] = hybrid[key];
                }
            }
            if (hybrid.contains("page") && hybrid["page"].is_object()) {
                const auto& page = hybrid["page"];
                nlohmann::json page_summary = nlohmann::json::object();
                for (const auto& key : {"title", "name", "width", "height"}) {
                    if (page.contains(key)) {
                        page_summary[key] = page[key];
                    }
                }
                if (!page_summary.empty()) {
                    hybrid_summary["page"] = std::move(page_summary);
                }
            }
            if (hybrid.contains("visibleItems") && hybrid["visibleItems"].is_array()) {
                auto visible_items = collect_ordered_readout_entries(
                        hybrid["visibleItems"], "hybrid.visibleItems", 16);
                if (!visible_items.empty()) {
                    hybrid_summary["orderedVisibleItems"] = std::move(visible_items);
                }
            }
            if (hybrid.contains("actionableNodes") && hybrid["actionableNodes"].is_array()) {
                const auto labels = collect_unique_readout_labels(hybrid["actionableNodes"], 10);
                if (!labels.empty()) {
                    hybrid_summary["visibleItems"] = labels;
                }
                nlohmann::json key_actions = nlohmann::json::array();
                size_t key_action_count = 0;
                for (const auto& node : hybrid["actionableNodes"]) {
                    if (!node.is_object()) {
                        continue;
                    }
                    if (!(node.value("clickable", false) || node.value("containerClickable", false))) {
                        continue;
                    }
                    const std::string label = trim_whitespace(first_string_value(
                            node, {"text", "visionLabel", "contentDescription", "content_description"}));
                    if (label.empty()) {
                        continue;
                    }
                    key_actions.push_back(truncate_runtime_text(label, 48));
                    key_action_count++;
                    if (key_action_count >= 6) {
                        break;
                    }
                }
                if (!key_actions.empty()) {
                    hybrid_summary["keyActions"] = std::move(key_actions);
                }
            }
            if (hybrid.contains("conflicts") && hybrid["conflicts"].is_array()) {
                nlohmann::json conflicts = nlohmann::json::array();
                for (size_t i = 0; i < hybrid["conflicts"].size() && i < 2; ++i) {
                    const auto& conflict = hybrid["conflicts"][i];
                    if (!conflict.is_object()) {
                        continue;
                    }
                    nlohmann::json compact_conflict = nlohmann::json::object();
                    for (const auto& key : {"code", "severity"}) {
                        if (conflict.contains(key)) {
                            compact_conflict[key] = conflict[key];
                        }
                    }
                    conflicts.push_back(std::move(compact_conflict));
                }
                if (!conflicts.empty()) {
                    hybrid_summary["conflicts"] = std::move(conflicts);
                }
            }
            summary["hybridObservation"] = std::move(hybrid_summary);
        }

        if (json.contains("screenVisionCompact") && json["screenVisionCompact"].is_object()) {
            const auto& compact = json["screenVisionCompact"];
            nlohmann::json compact_summary = nlohmann::json::object();
            for (const auto& key : {"summary", "page"}) {
                if (compact.contains(key)) {
                    compact_summary[key] = compact[key];
                }
            }
            if (compact.contains("items") && compact["items"].is_array()) {
                auto items = collect_ordered_readout_entries(compact["items"], "screenVision.items", 16);
                if (!items.empty()) {
                    compact_summary["orderedItems"] = std::move(items);
                }
            }
            if (compact.contains("texts" ) && compact["texts"].is_array()) {
                auto texts = collect_ordered_readout_entries(compact["texts"], "screenVision.texts", 24);
                if (!texts.empty()) {
                    compact_summary["orderedTexts"] = std::move(texts);
                }
            }
            if (compact.contains("sections") && compact["sections"].is_array()) {
                auto sections = collect_ordered_readout_entries(compact["sections"], "screenVision.sections", 8);
                if (!sections.empty()) {
                    compact_summary["orderedSections"] = std::move(sections);
                }
            }
            if (!compact_summary.empty()) {
                summary["screenVisionCompact"] = std::move(compact_summary);
            }
        }

        return truncate_runtime_text(summary.dump(), READOUT_OBSERVATION_MAX_CHARS);
    } catch (...) {
        return prune_tool_result(content, READOUT_OBSERVATION_MAX_CHARS);
    }
}

std::string build_gesture_result_summary(const std::string& content) {
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return truncate_runtime_text(content, TOOL_RESULT_SUMMARY_MAX_CHARS);
        }

        nlohmann::json summary = nlohmann::json::object();
        if (json.contains("success")) {
            summary["success"] = json["success"];
        }
        if (json.contains("action")) {
            summary["action"] = json["action"];
        }
        if (json.contains("message")) {
            summary["message"] = json["message"];
        }
        if (json.contains("observation") && json["observation"].is_object()) {
            const auto& observation = json["observation"];
            nlohmann::json observation_summary = nlohmann::json::object();
            if (observation.contains("snapshotId")) {
                observation_summary["snapshotId"] = observation["snapshotId"];
            }
            if (observation.contains("targetDescriptor")) {
                observation_summary["targetDescriptor"] = observation["targetDescriptor"];
            }
            if (observation.contains("referencedBounds")) {
                observation_summary["referencedBounds"] = observation["referencedBounds"];
            }
            summary["observation"] = observation_summary;
        }
        return truncate_runtime_text(summary.dump(), TOOL_RESULT_SUMMARY_MAX_CHARS);
    } catch (...) {
        return truncate_runtime_text(content, TOOL_RESULT_SUMMARY_MAX_CHARS);
    }
}

std::string build_generic_tool_result_summary(const std::string& tool_name,
                                              const std::string& content) {
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return truncate_runtime_text(tool_name + ": " + content, TOOL_RESULT_SUMMARY_MAX_CHARS);
        }

        nlohmann::json summary = nlohmann::json::object();
        summary["tool"] = tool_name;
        if (json.contains("success")) {
            summary["success"] = json["success"];
        }
        if (json.contains("error")) {
            summary["error"] = json["error"];
        }
        if (json.contains("message")) {
            summary["message"] = json["message"];
        }
        if (json.contains("result")) {
            summary["result"] = json["result"];
        }
        return truncate_runtime_text(summary.dump(), TOOL_RESULT_SUMMARY_MAX_CHARS);
    } catch (...) {
        return truncate_runtime_text(tool_name + ": " + content, TOOL_RESULT_SUMMARY_MAX_CHARS);
    }
}

std::string summarize_tool_result_for_context(const std::string& tool_name,
                                              const std::string& content) {
    if (tool_name == "android_view_context_tool") {
        return build_observation_result_summary(content);
    }
    if (tool_name == "android_gesture_tool") {
        return build_gesture_result_summary(content);
    }
    return build_generic_tool_result_summary(tool_name, content);
}

void shrink_old_assistant_messages(std::vector<Message>& messages) {
    std::vector<size_t> assistant_indexes;
    for (size_t i = 0; i < messages.size(); ++i) {
        if (messages[i].role == "assistant") {
            assistant_indexes.push_back(i);
        }
    }
    if (assistant_indexes.size() <= CONTEXT_KEEP_RECENT_ASSISTANT_COUNT) {
        return;
    }

    const size_t keep_from = assistant_indexes.size() - CONTEXT_KEEP_RECENT_ASSISTANT_COUNT;
    size_t compacted_tool_use_count = 0;
    size_t truncated_text_count = 0;
    for (size_t position = 0; position < assistant_indexes.size(); ++position) {
        auto& message = messages[assistant_indexes[position]];
        const bool keep_full = position >= keep_from;
        if (!keep_full && !message.tool_calls.empty()) {
            message.content.clear();
            compacted_tool_use_count++;
            continue;
        }

        std::vector<ContentBlock> compact_blocks;
        compact_blocks.reserve(message.content.size());
        for (const auto& block : message.content) {
            if (block.type == "thinking" && !keep_full) {
                continue;
            }
            if (block.type == "text" && !keep_full) {
                compact_blocks.push_back(ContentBlock::make_text(
                        truncate_runtime_text(block.text, CONTEXT_OLDER_ASSISTANT_MAX_CHARS)));
                truncated_text_count++;
            } else {
                compact_blocks.push_back(block);
            }
        }
        message.content = std::move(compact_blocks);
    }

    if (compacted_tool_use_count > 0 || truncated_text_count > 0) {
        ICRAW_LOG_INFO(
                "[AgentLoop][context_assistant_compacted] compacted_tool_use_count={} truncated_text_count={}",
                compacted_tool_use_count,
                truncated_text_count);
    }
}

void aggressively_compact_navigation_assistant_messages(std::vector<Message>& messages) {
    std::vector<size_t> assistant_indexes;
    for (size_t i = 0; i < messages.size(); ++i) {
        if (messages[i].role == "assistant") {
            assistant_indexes.push_back(i);
        }
    }
    if (assistant_indexes.size() <= 1) {
        return;
    }

    size_t compacted_count = 0;
    for (size_t position = 0; position + 1 < assistant_indexes.size(); ++position) {
        auto& message = messages[assistant_indexes[position]];
        if (!message.tool_calls.empty()) {
            message.content.clear();
            compacted_count++;
            continue;
        }
        std::vector<ContentBlock> compact_blocks;
        compact_blocks.reserve(message.content.size());
        for (const auto& block : message.content) {
            if (block.type == "text") {
                compact_blocks.push_back(ContentBlock::make_text(
                        truncate_runtime_text(block.text, 180)));
            }
        }
        message.content = std::move(compact_blocks);
        compacted_count++;
    }

    if (compacted_count > 0) {
        ICRAW_LOG_INFO(
                "[AgentLoop][context_navigation_assistant_compacted] compacted_count={}",
                compacted_count);
    }
}

void prune_context_messages_for_state(std::vector<Message>& messages,
                                      const ExecutionState& state) {
    const auto tool_name_by_id = build_tool_name_by_id(messages);
    const int latest_observation_index = find_latest_tool_message_index(
            messages, tool_name_by_id, "android_view_context_tool");
    const int latest_gesture_index = find_latest_tool_message_index(
            messages, tool_name_by_id, "android_gesture_tool");

    size_t summarized_count = 0;
    size_t original_chars = 0;
    size_t summarized_chars = 0;
    int kept_full_observations = 0;
    for (int i = static_cast<int>(messages.size()) - 1; i >= 0; --i) {
        auto& message = messages[static_cast<size_t>(i)];
        if (message.role != "tool") {
            continue;
        }
        const auto tool_name_it = tool_name_by_id.find(message.tool_call_id);
        const std::string tool_name = tool_name_it == tool_name_by_id.end() ? "tool" : tool_name_it->second;
        const bool keep_full_observation =
                tool_name == "android_view_context_tool"
                && latest_observation_index >= 0
                && i == latest_observation_index
                && state.latest_escalation.reason.empty()
                && kept_full_observations < static_cast<int>(CONTEXT_KEEP_FULL_OBSERVATION_COUNT);
        const bool keep_full_gesture =
                tool_name == "android_gesture_tool"
                && latest_gesture_index >= 0
                && i == latest_gesture_index;
        if (keep_full_observation) {
            kept_full_observations++;
            continue;
        }
        if (keep_full_gesture) {
            continue;
        }
        for (auto& block : message.content) {
            if (block.type != "tool_result") {
                continue;
            }
            original_chars += block.content.size();
            if (tool_name == "android_view_context_tool"
                    && latest_observation_index >= 0
                    && i == latest_observation_index
                    && !state.latest_escalation.reason.empty()
                    && !state.latest_navigation_observation_summary.empty()) {
                block.content = state.latest_navigation_observation_summary;
            } else {
                block.content = summarize_tool_result_for_context(tool_name, block.content);
            }
            summarized_chars += block.content.size();
            summarized_count++;
        }
    }

    shrink_old_assistant_messages(messages);
    if (!state.latest_escalation.reason.empty() && !state.goal_reached) {
        aggressively_compact_navigation_assistant_messages(messages);
    }

    if (summarized_count > 0) {
        ICRAW_LOG_INFO(
                "[AgentLoop][context_tool_results_summarized] summarized_count={} original_chars={} summarized_chars={} saved_chars={}",
                summarized_count,
                original_chars,
                summarized_chars,
                original_chars > summarized_chars ? (original_chars - summarized_chars) : 0);
    }
}

}  // namespace

namespace {


