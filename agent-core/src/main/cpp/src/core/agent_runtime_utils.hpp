#pragma once

// Internal AgentLoop implementation chunk. Included only by agent_loop.cpp.
// It centralizes small runtime helpers and tuning constants used by the
// Route/Navigate/Readout slices, keeping AgentLoop focused on orchestration.

std::string generate_tool_id() {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    static std::uniform_int_distribution<> dis(0, 15);

    std::string id = "toolu_";
    for (int i = 0; i < 24; ++i) {
        int val = dis(gen);
        id += (val < 10) ? ('0' + val) : ('a' + val - 10);
    }
    return id;
}

std::string trim_whitespace(const std::string& text) {
    const size_t start = text.find_first_not_of(" \t\n\r");
    if (start == std::string::npos) {
        return "";
    }
    const size_t end = text.find_last_not_of(" \t\n\r");
    return text.substr(start, end - start + 1);
}

std::string remove_markdown_section(const std::string& markdown,
                                    const std::string& heading) {
    if (markdown.empty() || heading.empty()) {
        return markdown;
    }

    const std::string marker = "# " + heading + "\n";
    const size_t section_begin = markdown.find(marker);
    if (section_begin == std::string::npos) {
        return markdown;
    }

    const size_t next_heading_break = markdown.find("\n# ", section_begin + marker.size());
    const size_t section_end = next_heading_break == std::string::npos
            ? markdown.size()
            : next_heading_break + 1;

    std::string prefix = markdown.substr(0, section_begin);
    std::string suffix = section_end < markdown.size() ? markdown.substr(section_end) : "";

    while (!prefix.empty() && (prefix.back() == '\n' || prefix.back() == '\r')) {
        prefix.pop_back();
    }
    while (!suffix.empty() && (suffix.front() == '\n' || suffix.front() == '\r')) {
        suffix.erase(suffix.begin());
    }

    if (prefix.empty()) {
        return suffix;
    }
    if (suffix.empty()) {
        return prefix;
    }
    return prefix + "\n\n" + suffix;
}

std::string build_readout_system_prompt(const std::string& system_prompt) {
    std::string reduced = remove_markdown_section(system_prompt, "Available Skills");
    reduced = remove_markdown_section(reduced, "Available Tools");
    reduced = remove_markdown_section(reduced, "Memory");
    return reduced;
}

void append_text_block_if_not_empty(std::vector<ContentBlock>& blocks,
                                    const std::string& text) {
    if (!text.empty()) {
        blocks.push_back(ContentBlock::make_text(text));
    }
}

void append_think_block_if_not_empty(std::vector<ContentBlock>& blocks,
                                     const std::string& text) {
    const std::string trimmed = trim_whitespace(text);
    if (!trimmed.empty()) {
        blocks.push_back(ContentBlock::make_think(trimmed));
    }
}

std::vector<ContentBlock> build_response_blocks(const std::string& visible_text,
                                                const std::string& reasoning_text) {
    std::vector<ContentBlock> blocks;
    append_think_block_if_not_empty(blocks, reasoning_text);
    append_text_block_if_not_empty(blocks, visible_text);
    return blocks;
}

constexpr int CONTEXT_RECENT_TOOL_RESULT_MAX_CHARS = 32000;
constexpr int CONTEXT_OLDER_TOOL_RESULT_MAX_CHARS = 12000;
constexpr size_t CONTEXT_DETAILED_TOOL_RESULT_COUNT = 3;
constexpr size_t EXECUTION_STATE_MAX_TOOL_EVENTS = 3;
constexpr size_t EXECUTION_STATE_MAX_OBJECTIVE_CHARS = 220;
constexpr size_t EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS = 180;

constexpr size_t CONTEXT_KEEP_FULL_OBSERVATION_COUNT = 1;
constexpr size_t CONTEXT_KEEP_RECENT_ASSISTANT_COUNT = 2;
constexpr int CONTEXT_TOTAL_SOFT_MAX_CHARS = 60000;
constexpr int CONTEXT_READOUT_SOFT_MAX_CHARS = 40000;
constexpr int CONTEXT_OLDER_ASSISTANT_MAX_CHARS = 600;
constexpr int CONTEXT_MIN_SUMMARY_MAX_CHARS = 900;
constexpr int TOOL_RESULT_SUMMARY_MAX_CHARS = 1200;
constexpr int PLANNED_ADVANCE_OBSERVATION_MAX_CHARS = 7000;
constexpr int NAVIGATION_ESCALATION_OBSERVATION_MAX_CHARS = 4200;
constexpr int READOUT_OBSERVATION_MAX_CHARS = 4200;
constexpr size_t OBSERVATION_SUMMARY_ACTIONABLE_COUNT = 4;
constexpr size_t CANONICAL_CANDIDATE_SUMMARY_COUNT = 6;
constexpr size_t SKILL_PRELOAD_MAX_COUNT = 2;
constexpr size_t SKILL_PRELOAD_MAX_TOTAL_CHARS = 20000;
constexpr size_t SKILL_SUMMARY_MAX_CHARS = 1200;
constexpr double FAST_EXECUTE_NATIVE_SCORE_MIN = 0.82;
constexpr double FAST_EXECUTE_VISION_ONLY_SCORE_MIN = 0.97;
constexpr size_t OBSERVATION_VISIBLE_TEXT_MAX_CHARS = 16000;
constexpr bool COMPACT_NAVIGATION_ESCALATION_ENABLED = false;

constexpr int kMaxPendingConfirmationRetries = 1;
constexpr auto kPendingConfirmationInitialSettleDelay = std::chrono::milliseconds(850);
constexpr auto kPendingConfirmationRetryDelay = std::chrono::milliseconds(450);

std::string truncate_runtime_text(const std::string& text, size_t max_chars);

int count_tool_result_messages(const std::vector<Message>& messages) {
    int count = 0;
    for (const auto& message : messages) {
        if (message.role != "tool") {
            continue;
        }
        for (const auto& block : message.content) {
            if (block.type == "tool_result") {
                count++;
                break;
            }
        }
    }
    return count;
}

std::string summarize_tool_result_for_state(const std::string& tool_name,
                                            const std::string& content) {
    auto summarize_text = [](const std::string& text, size_t max_chars) {
        return truncate_runtime_text(text, max_chars);
    };

    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return summarize_text(content, EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
        }

        std::vector<std::string> parts;
        if (json.contains("success")) {
            parts.push_back(std::string("success=") + (json["success"].get<bool>() ? "true" : "false"));
        }
        if (json.contains("error") && json["error"].is_string()) {
            parts.push_back("error=" + summarize_text(json["error"].get<std::string>(), 80));
        }
        if (json.contains("activityClassName") && json["activityClassName"].is_string()) {
            parts.push_back("activity=" + json["activityClassName"].get<std::string>());
        }
        if (json.contains("source") && json["source"].is_string()) {
            parts.push_back("source=" + json["source"].get<std::string>());
        }
        if (json.contains("visualObservationMode") && json["visualObservationMode"].is_string()) {
            parts.push_back("visual=" + json["visualObservationMode"].get<std::string>());
        }
        if (json.contains("hybridObservation") && json["hybridObservation"].is_object()) {
            const auto& hybrid = json["hybridObservation"];
            if (hybrid.contains("summary") && hybrid["summary"].is_string()) {
                parts.push_back("summary=" + summarize_text(hybrid["summary"].get<std::string>(), 80));
            }
        }
        if (json.contains("message") && json["message"].is_string()) {
            parts.push_back("message=" + summarize_text(json["message"].get<std::string>(), 80));
        }
        if (json.contains("result") && json["result"].is_string()) {
            parts.push_back("result=" + summarize_text(json["result"].get<std::string>(), 80));
        }

        std::ostringstream stream;
        stream << tool_name;
        if (!parts.empty()) {
            stream << " -> ";
            for (size_t i = 0; i < parts.size(); ++i) {
                if (i > 0) {
                    stream << ", ";
                }
                stream << parts[i];
            }
        }
        return summarize_text(stream.str(), EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
    } catch (...) {
        return summarize_text(tool_name + " -> " + content, EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
    }
}

std::vector<std::string> collect_recent_tool_events(const std::vector<Message>& messages) {
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

    std::vector<std::string> events;
    for (auto it = messages.rbegin(); it != messages.rend() && events.size() < EXECUTION_STATE_MAX_TOOL_EVENTS; ++it) {
        if (it->role != "tool" || it->content.empty()) {
            continue;
        }
        const auto tool_name_it = tool_names_by_id.find(it->tool_call_id);
        const std::string tool_name = tool_name_it == tool_names_by_id.end() ? "tool" : tool_name_it->second;
        for (const auto& block : it->content) {
            if (block.type != "tool_result") {
                continue;
            }
            events.push_back(summarize_tool_result_for_state(tool_name, block.content));
            break;
        }
    }
    std::reverse(events.begin(), events.end());
    return events;
}

std::string build_execution_state_prompt(const std::string& objective,
                                         int iteration,
                                         const std::vector<Message>& messages) {
    const auto recent_tool_events = collect_recent_tool_events(messages);
    if (iteration <= 1 || recent_tool_events.empty()) {
        return "";
    }

    const std::string objective_trimmed = trim_whitespace(objective);
    const std::string objective_compact = objective_trimmed.size() <= EXECUTION_STATE_MAX_OBJECTIVE_CHARS
            ? objective_trimmed
            : objective_trimmed.substr(0, EXECUTION_STATE_MAX_OBJECTIVE_CHARS) + "...";
    const std::string last_event = recent_tool_events.back();

    std::ostringstream prompt;
    prompt << "# Turn Execution State\n\n";
    prompt << "Primary objective: " << objective_compact << "\n";
    prompt << "Current iteration: " << iteration << "\n";
    prompt << "Execution policy:\n";
    prompt << "- Continue from the latest confirmed progress toward the same objective.\n";
    prompt << "- Do not restart planning from scratch unless the latest tool result proves the plan is blocked or the objective is already complete.\n";
    prompt << "- Reuse any skill or plan already established in this turn instead of re-reading or re-deriving it.\n";
    if (last_event.find("android_gesture_tool") != std::string::npos) {
        prompt << "- The last concrete action was a gesture. Prefer a fresh observation next to confirm the new page state.\n";
    } else if (last_event.find("android_view_context_tool") != std::string::npos) {
        prompt << "- The last concrete action was an observation. Prefer the next concrete action that advances the same objective using that observation.\n";
    }
    prompt << "\nRecent confirmed tool events:\n";
    for (size_t i = 0; i < recent_tool_events.size(); ++i) {
        prompt << i + 1 << ". " << recent_tool_events[i] << "\n";
    }
    return prompt.str();
}

void inject_execution_state_into_request(ChatCompletionRequest& request,
                                         const std::string& execution_state_prompt) {
    if (execution_state_prompt.empty()) {
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
        merged += "\n[Execution State]\n";
        merged += execution_state_prompt;

        msg.content.clear();
        msg.content.push_back(ContentBlock::make_text(std::move(merged)));
        return;
    }

    request.messages.insert(request.messages.begin(), Message("system", execution_state_prompt));
}

enum class LlmRequestProfileKind {
    Route,
    NavigationEscalation,
    ToolCall,
    Readout,
    FinalAnswer
};

struct LlmRequestProfile {
    LlmRequestProfileKind kind = LlmRequestProfileKind::ToolCall;
    double temperature = 0.7;
    int max_tokens = 4096;
    PayloadLogMode payload_log_mode = PayloadLogMode::Summary;
};

const char* llm_request_profile_name(LlmRequestProfileKind kind) {
    switch (kind) {
        case LlmRequestProfileKind::Route:
            return "route";
        case LlmRequestProfileKind::NavigationEscalation:
            return "navigation_escalation";
        case LlmRequestProfileKind::Readout:
            return "readout";
        case LlmRequestProfileKind::FinalAnswer:
            return "final_answer";
        case LlmRequestProfileKind::ToolCall:
        default:
            return "tool_call";
    }
}

LlmRequestProfile resolve_llm_request_profile(const AgentConfig& config,
                                             const ExecutionState& state,
                                             const ChatCompletionRequest& request) {
    LlmRequestProfile profile;
    profile.temperature = config.temperature;
    profile.max_tokens = config.max_tokens;

    if (request.request_profile == "route") {
        profile.kind = LlmRequestProfileKind::Route;
        profile.max_tokens = std::min(config.max_tokens, 512);
        profile.temperature = std::min(config.temperature, 0.20);
        return profile;
    }

    if (request.request_profile == "navigation_escalation" || !state.latest_escalation.reason.empty()) {
        profile.kind = LlmRequestProfileKind::NavigationEscalation;
        profile.max_tokens = std::min(config.max_tokens, 640);
        profile.temperature = std::min(config.temperature, 0.15);
        return profile;
    }

    if (state.goal_reached || state.phase == "readout" || request.request_profile == "readout") {
        profile.kind = LlmRequestProfileKind::Readout;
        profile.max_tokens = std::min(config.max_tokens, 1280);
        profile.temperature = std::min(config.temperature, 0.45);
        return profile;
    }

    if (request.tools.empty()) {
        profile.kind = LlmRequestProfileKind::FinalAnswer;
        profile.max_tokens = std::min(config.max_tokens, 1536);
        profile.temperature = std::min(config.temperature, 0.45);
        return profile;
    }

    profile.kind = LlmRequestProfileKind::ToolCall;
    profile.max_tokens = std::min(config.max_tokens, 640);
    profile.temperature = std::min(config.temperature, 0.20);
    return profile;
}

void apply_llm_request_profile(ChatCompletionRequest& request,
                               const LlmRequestProfile& profile) {
    request.temperature = profile.temperature;
    request.max_tokens = profile.max_tokens;
    request.request_profile = llm_request_profile_name(profile.kind);
    request.payload_log_mode = profile.payload_log_mode;
}

size_t utf8_safe_truncation_index(const std::string& text, size_t max_chars) {
    if (text.size() <= max_chars) {
        return text.size();
    }
    size_t index = max_chars;
    while (index > 0 && (static_cast<unsigned char>(text[index]) & 0xC0u) == 0x80u) {
        index--;
    }
    if (index == 0) {
        return max_chars;
    }
    const unsigned char lead = static_cast<unsigned char>(text[index]);
    size_t sequence_length = 1;
    if ((lead & 0x80u) == 0x00u) {
        sequence_length = 1;
    } else if ((lead & 0xE0u) == 0xC0u) {
        sequence_length = 2;
    } else if ((lead & 0xF0u) == 0xE0u) {
        sequence_length = 3;
    } else if ((lead & 0xF8u) == 0xF0u) {
        sequence_length = 4;
    }
    if (index + sequence_length > max_chars) {
        return index;
    }
    return max_chars;
}

std::string truncate_runtime_text(const std::string& text, size_t max_chars) {
    const std::string trimmed = trim_whitespace(text);
    if (trimmed.size() <= max_chars) {
        return trimmed;
    }
    const size_t safe_index = utf8_safe_truncation_index(trimmed, max_chars);
    return trimmed.substr(0, safe_index) + "...";
}

std::string normalize_for_runtime_match(const std::string& text) {
    std::string normalized;
    normalized.reserve(text.size());
    for (unsigned char ch : text) {
        if (std::isalnum(ch)) {
            normalized.push_back(static_cast<char>(std::tolower(ch)));
            continue;
        }
        if (ch >= 0x80) {
            normalized.push_back(static_cast<char>(ch));
        }
    }
    return normalized;
}

bool contains_runtime_match(const std::string& haystack, const std::string& needle) {
    const std::string normalized_needle = normalize_for_runtime_match(needle);
    if (normalized_needle.empty()) {
        return false;
    }
    return normalize_for_runtime_match(haystack).find(normalized_needle) != std::string::npos;
}

std::string first_string_value(const nlohmann::json& object,
                               const std::vector<std::string>& keys) {
    if (!object.is_object()) {
        return "";
    }
    for (const auto& key : keys) {
        if (!object.contains(key) || !object[key].is_string()) {
            continue;
        }
        return trim_whitespace(object[key].get<std::string>());
    }
    return "";
}

nlohmann::json build_string_array_json(const std::vector<std::string>& values) {
    nlohmann::json array = nlohmann::json::array();
    for (const auto& value : values) {
        if (!value.empty()) {
            array.push_back(value);
        }
    }
    return array;
}

std::vector<std::string> string_array_values(const nlohmann::json& object,
                                             const std::vector<std::string>& keys) {
    std::vector<std::string> values;
    if (!object.is_object()) {
        return values;
    }
    for (const auto& key : keys) {
        if (!object.contains(key)) {
            continue;
        }
        const auto& value = object[key];
        if (value.is_string()) {
            const std::string text = trim_whitespace(value.get<std::string>());
            if (!text.empty()) {
                values.push_back(text);
            }
            continue;
        }
        if (!value.is_array()) {
            continue;
        }
        for (const auto& item : value) {
            if (!item.is_string()) {
                continue;
            }
            const std::string text = trim_whitespace(item.get<std::string>());
            if (!text.empty()) {
                values.push_back(text);
            }
        }
        if (!values.empty()) {
            break;
        }
    }
    return values;
}

nlohmann::json append_unique_strings(const nlohmann::json& values) {
    std::set<std::string> unique_values;
    if (values.is_array()) {
        for (const auto& item : values) {
            if (!item.is_string()) {
                continue;
            }
            const std::string value = trim_whitespace(item.get<std::string>());
            if (!value.empty()) {
                unique_values.insert(value);
            }
        }
    }
    nlohmann::json array = nlohmann::json::array();
    for (const auto& value : unique_values) {
        array.push_back(value);
    }
    return array;
}
