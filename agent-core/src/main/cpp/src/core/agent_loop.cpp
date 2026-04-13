#include "icraw/core/agent_loop.hpp"
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
#include <limits>
#include <unordered_map>

namespace icraw {

// Helper to generate unique tool call IDs (must be defined before use)
static std::string generate_tool_id() {
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

static std::string trim_whitespace(const std::string& text) {
    const size_t start = text.find_first_not_of(" \t\n\r");
    if (start == std::string::npos) {
        return "";
    }
    const size_t end = text.find_last_not_of(" \t\n\r");
    return text.substr(start, end - start + 1);
}

static std::string remove_markdown_section(const std::string& markdown,
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

static std::string build_readout_system_prompt(const std::string& system_prompt) {
    std::string reduced = remove_markdown_section(system_prompt, "Available Skills");
    reduced = remove_markdown_section(reduced, "Memory");
    return reduced;
}

static void append_text_block_if_not_empty(std::vector<ContentBlock>& blocks,
                                           const std::string& text) {
    if (!text.empty()) {
        blocks.push_back(ContentBlock::make_text(text));
    }
}

static void append_think_block_if_not_empty(std::vector<ContentBlock>& blocks,
                                            const std::string& text) {
    const std::string trimmed = trim_whitespace(text);
    if (!trimmed.empty()) {
        blocks.push_back(ContentBlock::make_think(trimmed));
    }
}

static std::vector<ContentBlock> build_response_blocks(const std::string& visible_text,
                                                       const std::string& reasoning_text) {
    std::vector<ContentBlock> blocks;
    append_think_block_if_not_empty(blocks, reasoning_text);
    append_text_block_if_not_empty(blocks, visible_text);
    return blocks;
}

namespace {

constexpr int CONTEXT_RECENT_TOOL_RESULT_MAX_CHARS = 32000;
constexpr int CONTEXT_OLDER_TOOL_RESULT_MAX_CHARS = 12000;
constexpr size_t CONTEXT_DETAILED_TOOL_RESULT_COUNT = 3;
constexpr size_t EXECUTION_STATE_MAX_TOOL_EVENTS = 3;
constexpr size_t EXECUTION_STATE_MAX_OBJECTIVE_CHARS = 220;
constexpr size_t EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS = 180;

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

}  // namespace

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

namespace {

constexpr size_t CONTEXT_KEEP_FULL_OBSERVATION_COUNT = 1;
constexpr size_t CONTEXT_KEEP_RECENT_ASSISTANT_COUNT = 2;
constexpr int CONTEXT_TOTAL_SOFT_MAX_CHARS = 60000;
constexpr int CONTEXT_READOUT_SOFT_MAX_CHARS = 40000;
constexpr int CONTEXT_OLDER_ASSISTANT_MAX_CHARS = 600;
constexpr int CONTEXT_MIN_SUMMARY_MAX_CHARS = 900;
constexpr int TOOL_RESULT_SUMMARY_MAX_CHARS = 1200;
constexpr int PLANNED_ADVANCE_OBSERVATION_MAX_CHARS = 7000;
constexpr int NAVIGATION_ESCALATION_OBSERVATION_MAX_CHARS = 4200;
constexpr int READOUT_OBSERVATION_MAX_CHARS = 2600;
constexpr size_t OBSERVATION_SUMMARY_ACTIONABLE_COUNT = 4;
constexpr size_t CANONICAL_CANDIDATE_SUMMARY_COUNT = 6;
constexpr size_t SKILL_PRELOAD_MAX_COUNT = 2;
constexpr size_t SKILL_PRELOAD_MAX_TOTAL_CHARS = 20000;
constexpr size_t SKILL_SUMMARY_MAX_CHARS = 1200;
constexpr double FAST_EXECUTE_NATIVE_SCORE_MIN = 0.82;
constexpr double FAST_EXECUTE_VISION_ONLY_SCORE_MIN = 0.97;
constexpr bool COMPACT_NAVIGATION_ESCALATION_ENABLED = false;

struct SkillStepHint {
    std::string page;
    std::string activity;
    std::string target;
    std::vector<std::string> aliases;
    std::string region;
    std::string anchor_type;
    std::string container_role;
    std::string action;
    bool readout = false;
};

struct ParsedExecutionHints {
    std::string kind;
    std::vector<SkillStepHint> steps;

    bool empty() const {
        return steps.empty();
    }
};

struct ObservationCandidate {
    std::string label;
    std::string match_text;
    std::string source;
    std::string resource_id;
    std::string bounds;
    std::string region;
    std::string anchor_type;
    std::string container_role;
    bool clickable = false;
    bool container_clickable = false;
    bool badge_like = false;
    bool numeric_like = false;
    bool decorative_like = false;
    bool repeat_group = false;
    double score = 0.0;
};

struct ObservationConflict {
    std::string code;
    std::string severity;
    std::string message;
    std::string bounds;
};

struct ObservationSnapshot {
    std::string activity;
    std::string source;
    std::string visual_mode;
    std::string summary;
    std::string snapshot_id;
    int screen_width = 0;
    int screen_height = 0;
    bool has_warning_conflict = false;
    size_t warning_conflict_count = 0;
    std::vector<ObservationConflict> warning_conflicts;
    std::vector<ObservationCandidate> actionable_candidates;
};

struct CanonicalCandidate {
    ObservationCandidate aggregate;
    std::vector<ObservationCandidate> members;
    std::string display_label;
    std::string tap_bounds;
    std::string source_set;
    std::string risk_flags;
    int actionability_rank = 0;
    size_t member_count = 0;
};

struct NavigationAttemptCacheEntry {
    std::string step_target;
    std::string selected_candidate;
    std::string tap_bounds;
    int attempt_count = 0;
};

struct ExecutionState {
    std::string goal;
    std::string mode = "free_llm";
    std::string phase = "discovery";
    std::string current_page;
    std::vector<std::string> completed_steps;
    nlohmann::json pending_step;
    std::string latest_observation_summary;
    std::string latest_navigation_observation_summary;
    std::string latest_canonical_candidate_summary;
    std::string latest_readout_observation_summary;
    std::string latest_action_result;
    std::string last_observation_target_hint;
    std::string last_gesture_target;
    std::vector<std::string> selected_skills;
    std::optional<ParsedExecutionHints> active_hints;
    int pending_step_index = -1;
    int awaiting_step_confirmation_index = -1;
    std::string awaiting_confirmation_target;
    std::string awaiting_confirmation_previous_page;
    int awaiting_confirmation_retry_count = 0;
    bool goal_reached = false;
    bool context_reset = false;
    bool route_ready = false;
    bool route_resolved_by_llm = false;
    IntentRoute intent_route;
    NavigationPlan navigation_plan;
    NavigationCheckpoint navigation_checkpoint;
    NavigationEscalation latest_escalation;
    RouteRequestContext route_request_context;
    ObservationFingerprint latest_observation_fingerprint;
    NavigationTraceSummary latest_navigation_trace;
    ReadoutRequestContext readout_context;
    std::unordered_map<std::string, NavigationAttemptCacheEntry> navigation_attempt_cache;
    int readout_retry_count = 0;
};

constexpr int kMaxPendingConfirmationRetries = 1;
constexpr auto kPendingConfirmationRetryDelay = std::chrono::milliseconds(350);

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

nlohmann::json build_step_json(const SkillStepHint& step) {
    nlohmann::json json = nlohmann::json::object();
    if (!step.page.empty()) {
        json["page"] = step.page;
    }
    if (!step.activity.empty()) {
        json["activity"] = step.activity;
    }
    if (!step.target.empty()) {
        json["target"] = step.target;
    }
    if (!step.aliases.empty()) {
        json["aliases"] = build_string_array_json(step.aliases);
    }
    if (!step.region.empty()) {
        json["region"] = step.region;
    }
    if (!step.anchor_type.empty()) {
        json["anchor_type"] = step.anchor_type;
    }
    if (!step.container_role.empty()) {
        json["container_role"] = step.container_role;
    }
    if (!step.action.empty()) {
        json["action"] = step.action;
    }
    if (step.readout) {
        json["readout"] = true;
    }
    return json;
}

std::string describe_step(const SkillStepHint& step) {
    std::ostringstream stream;
    if (!step.action.empty()) {
        stream << step.action;
    }
    if (!step.target.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "'" << step.target << "'";
    }
    if (!step.aliases.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "(aliases:";
        for (size_t i = 0; i < std::min<size_t>(step.aliases.size(), 3); ++i) {
            stream << (i == 0 ? " " : ", ") << step.aliases[i];
        }
        if (step.aliases.size() > 3) {
            stream << ", ...";
        }
        stream << ")";
    }
    if (!step.region.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "[region=" << step.region << "]";
    }
    if (!step.anchor_type.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "[anchor_type=" << step.anchor_type << "]";
    }
    if (!step.container_role.empty()) {
        if (stream.tellp() > 0) {
            stream << " ";
        }
        stream << "[container_role=" << step.container_role << "]";
    }
    if (!step.page.empty() || !step.activity.empty()) {
        if (stream.tellp() > 0) {
            stream << " on ";
        }
        stream << (!step.page.empty() ? step.page : step.activity);
    }
    if (step.readout) {
        if (stream.tellp() > 0) {
            stream << " -> ";
        }
        stream << "readout";
    }
    return truncate_runtime_text(stream.str(), EXECUTION_STATE_MAX_TOOL_SUMMARY_CHARS);
}

std::optional<ParsedExecutionHints> parse_execution_hints(const std::vector<SkillMetadata>& selected_skills) {
    for (const auto& skill : selected_skills) {
        if (!skill.execution_hints.is_object()) {
            continue;
        }
        ParsedExecutionHints parsed;
        parsed.kind = first_string_value(skill.execution_hints, {"kind"});
        const auto& steps = skill.execution_hints["steps"];
        if (!steps.is_array()) {
            continue;
        }
        for (const auto& step_json : steps) {
            if (!step_json.is_object()) {
                continue;
            }
            SkillStepHint step;
            step.page = first_string_value(step_json,
                    {"page", "pageContains", "summaryContains", "pageSummaryContains"});
            step.activity = first_string_value(step_json,
                    {"activity", "activityContains", "activityClassNameContains"});
            step.target = first_string_value(step_json,
                    {"target", "targetHint", "targetContains", "label"});
            step.aliases = string_array_values(step_json,
                    {"aliases", "alias", "targets", "targetAliases"});
            step.region = first_string_value(step_json,
                    {"region", "preferredRegion", "anchorRegion"});
            step.anchor_type = first_string_value(step_json,
                    {"anchor_type", "anchorType", "entryType"});
            step.container_role = first_string_value(step_json,
                    {"container_role", "containerRole"});
            step.action = first_string_value(step_json,
                    {"action", "gesture", "type"});
            const std::string phase = first_string_value(step_json, {"phase"});
            step.readout = step_json.value("goalReached", false)
                    || phase == "readout"
                    || step.action == "read"
                    || step.action == "readout";
            if (step.page.empty() && step.activity.empty() && step.target.empty()
                    && step.aliases.empty() && step.region.empty()
                    && step.anchor_type.empty() && step.container_role.empty()
                    && step.action.empty() && !step.readout) {
                continue;
            }
            parsed.steps.push_back(std::move(step));
        }
        if (!parsed.steps.empty()) {
            return parsed;
        }
    }
    return std::nullopt;
}

bool goal_requires_readout(const std::string& goal) {
    static const std::vector<std::string> readout_terms = {
        u8"\u67e5\u770b", u8"\u9605\u8bfb", u8"\u603b\u7ed3", u8"\u5185\u5bb9", u8"\u8be6\u60c5",
        "read", "summary", "summarize", "content", "details", "list"
    };
    for (const auto& term : readout_terms) {
        if (contains_runtime_match(goal, term)) {
            return true;
        }
    }
    return false;
}

NavigationPlan build_navigation_plan(const std::optional<ParsedExecutionHints>& hints) {
    NavigationPlan plan;
    if (!hints || hints->steps.empty()) {
        return plan;
    }
    for (const auto& hint_step : hints->steps) {
        NavigationStep step;
        step.page = hint_step.page;
        step.activity = hint_step.activity;
        step.target = hint_step.target;
        step.aliases = hint_step.aliases;
        step.action = hint_step.action;
        step.readout = hint_step.readout;
        plan.steps.push_back(std::move(step));
    }
    return plan;
}

const SkillMetadata* pick_route_skill(const std::vector<SkillMetadata>& selected_skills) {
    if (selected_skills.empty()) {
        return nullptr;
    }
    for (const auto& skill : selected_skills) {
        if (skill.execution_hints.is_object()) {
            return &skill;
        }
    }
    return &selected_skills.front();
}

StopConditionSpec build_stop_condition_spec(const std::string& objective,
                                            const SkillMetadata* route_skill,
                                            const std::optional<ParsedExecutionHints>& hints) {
    StopConditionSpec spec;
    spec.requires_readout = goal_requires_readout(objective);

    if (hints && !hints->steps.empty()) {
        const SkillStepHint* terminal_step = nullptr;
        for (const auto& step : hints->steps) {
            const bool is_terminal = step.readout
                    || (&step == &hints->steps.back());
            if (!is_terminal) {
                continue;
            }
            terminal_step = &step;
            break;
        }
        if (!terminal_step) {
            terminal_step = &hints->steps.back();
        }

        if (terminal_step) {
            if (!terminal_step->page.empty()) {
                spec.page_predicates.push_back(terminal_step->page);
            }
            if (!terminal_step->activity.empty()) {
                spec.page_predicates.push_back(terminal_step->activity);
            }
            if (!terminal_step->target.empty()) {
                spec.content_predicates.push_back(terminal_step->target);
            }
            for (const auto& alias : terminal_step->aliases) {
                if (!alias.empty()) {
                    spec.content_predicates.push_back(alias);
                }
            }
            spec.requires_readout = spec.requires_readout || terminal_step->readout;
        }
    }

    if (route_skill && route_skill->execution_hints.is_object()
            && route_skill->execution_hints.contains("stop_condition")
            && route_skill->execution_hints["stop_condition"].is_object()) {
        const auto& stop = route_skill->execution_hints["stop_condition"];
        auto merge_values = [&](std::vector<std::string>& target,
                                const std::vector<std::string>& values) {
            for (const auto& value : values) {
                if (value.empty()) {
                    continue;
                }
                if (std::find(target.begin(), target.end(), value) == target.end()) {
                    target.push_back(value);
                }
            }
        };
        merge_values(spec.page_predicates, string_array_values(stop,
                {"page_predicates", "pagePredicates", "pages"}));
        merge_values(spec.content_predicates, string_array_values(stop,
                {"content_predicates", "contentPredicates", "content"}));
        merge_values(spec.success_signals, string_array_values(stop,
                {"success_signals", "successSignals", "success"}));
        merge_values(spec.failure_signals, string_array_values(stop,
                {"failure_signals", "failureSignals", "failure"}));
        if (stop.contains("requires_readout") && stop["requires_readout"].is_boolean()) {
            spec.requires_readout = stop["requires_readout"].get<bool>();
        }
    }

    if (spec.page_predicates.empty()) {
        if (hints && !hints->steps.empty()) {
            const auto& last = hints->steps.back();
            if (!last.page.empty()) {
                spec.page_predicates.push_back(last.page);
            }
            if (!last.activity.empty()) {
                spec.page_predicates.push_back(last.activity);
            }
        }
    }
    if (spec.page_predicates.empty()) {
        spec.page_predicates.push_back(objective);
    }
    if (spec.failure_signals.empty()) {
        spec.failure_signals = {"failed", "error", "denied", "not found"};
    }
    return spec;
}

IntentRoute build_intent_route(const std::string& objective,
                               const std::vector<SkillMetadata>& selected_skills,
                               const std::optional<ParsedExecutionHints>& hints) {
    IntentRoute route;
    const SkillMetadata* route_skill = pick_route_skill(selected_skills);
    if (route_skill) {
        route.selected_skill = route_skill->name;
    }

    route.stop_condition = build_stop_condition_spec(objective, route_skill, hints);
    route.readout_goal = truncate_runtime_text(objective, 160);
    route.escalation_policy = "on_ambiguity_or_no_progress";
    route.task_type = route.stop_condition.requires_readout ? "navigate_and_read" : "navigate_and_trigger";

    if (hints && !hints->steps.empty()) {
        for (const auto& step : hints->steps) {
            if (!step.readout) {
                continue;
            }
            if (!step.page.empty()) {
                route.navigation_goal = step.page;
                break;
            }
            if (!step.activity.empty()) {
                route.navigation_goal = step.activity;
                break;
            }
            if (!step.target.empty()) {
                route.navigation_goal = step.target;
                break;
            }
        }
        if (route.navigation_goal.empty()) {
            const auto& first = hints->steps.front();
            route.navigation_goal = !first.page.empty()
                    ? first.page
                    : (!first.activity.empty() ? first.activity : first.target);
        }
    }

    if (route.navigation_goal.empty()) {
        route.navigation_goal = truncate_runtime_text(objective, 120);
    }

    return route;
}

ExecutionState initialize_execution_state(const std::string& objective,
                                          const std::vector<SkillMetadata>& selected_skills) {
    ExecutionState state;
    state.goal = truncate_runtime_text(objective, EXECUTION_STATE_MAX_OBJECTIVE_CHARS);
    state.selected_skills.reserve(selected_skills.size());
    for (const auto& skill : selected_skills) {
        state.selected_skills.push_back(skill.name);
    }
    state.active_hints = parse_execution_hints(selected_skills);
    state.navigation_plan = build_navigation_plan(state.active_hints);
    state.intent_route = build_intent_route(objective, selected_skills, state.active_hints);
    state.route_ready = true;
    state.readout_context.objective = truncate_runtime_text(objective, 220);
    state.readout_context.readout_goal = state.intent_route.readout_goal;
    state.readout_context.selected_skill = state.intent_route.selected_skill;

    if (state.active_hints && !state.active_hints->empty()) {
        state.mode = "planned_fast_execute";
        state.pending_step_index = 0;
        state.pending_step = build_step_json(state.active_hints->steps.front());
    }
    return state;
}

}  // namespace

namespace {

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

ObservationSnapshot parse_observation_snapshot(const std::string& content) {
    ObservationSnapshot snapshot;
    try {
        const auto json = nlohmann::json::parse(content);
        if (!json.is_object()) {
            return snapshot;
        }

        snapshot.activity = json.value("activityClassName", "");
        snapshot.source = json.value("source", "");
        snapshot.visual_mode = json.value("visualObservationMode", "");
        snapshot.snapshot_id = json.value("snapshotId", "");
        snapshot.screen_width = json.value("screenSnapshotWidth", 0);
        snapshot.screen_height = json.value("screenSnapshotHeight", 0);
        if (!json.contains("hybridObservation") || !json["hybridObservation"].is_object()) {
            return snapshot;
        }

        const auto& hybrid = json["hybridObservation"];
        snapshot.summary = hybrid.value("summary", "");
        if (hybrid.contains("page") && hybrid["page"].is_object()) {
            const auto& page = hybrid["page"];
            if (snapshot.screen_width <= 0) {
                snapshot.screen_width = page.value("width", 0);
            }
            if (snapshot.screen_height <= 0) {
                snapshot.screen_height = page.value("height", 0);
            }
        }
        if (hybrid.contains("conflicts") && hybrid["conflicts"].is_array()) {
            for (const auto& conflict : hybrid["conflicts"]) {
                if (!conflict.is_object()) {
                    continue;
                }
                const std::string severity = conflict.value("severity", "");
                if (severity == "warning") {
                    snapshot.has_warning_conflict = true;
                    snapshot.warning_conflict_count++;
                    ObservationConflict parsed_conflict;
                    parsed_conflict.code = conflict.value("code", "");
                    parsed_conflict.severity = severity;
                    parsed_conflict.message = conflict.value("message", "");
                    parsed_conflict.bounds = conflict.value("bounds", "");
                    snapshot.warning_conflicts.push_back(std::move(parsed_conflict));
                }
            }
        }
        if (hybrid.contains("actionableNodes") && hybrid["actionableNodes"].is_array()) {
            for (const auto& node : hybrid["actionableNodes"]) {
                if (!node.is_object()) {
                    continue;
                }
                ObservationCandidate candidate;
                candidate.label = first_string_value(node,
                        {"text", "visionLabel", "resourceId", "className", "id"});
                candidate.match_text = trim_whitespace(
                        first_string_value(node, {"text"}) + " "
                        + first_string_value(node, {"visionLabel"}) + " "
                        + first_string_value(node, {"contentDescription", "content_description"}) + " "
                        + first_string_value(node, {"parentSemanticContext", "parent_semantic_context"}) + " "
                        + first_string_value(node, {"resourceId"}) + " "
                        + first_string_value(node, {"className"}) + " "
                        + first_string_value(node, {"anchorType", "anchor_type"}) + " "
                        + first_string_value(node, {"containerRole", "container_role"}) + " "
                        + first_string_value(node, {"id"}));
                candidate.source = node.value("source", "");
                candidate.resource_id = trim_whitespace(first_string_value(node, {"resourceId"}));
                candidate.bounds = node.value("bounds", "");
                candidate.region = trim_whitespace(first_string_value(node, {"region"}));
                candidate.anchor_type = trim_whitespace(first_string_value(node, {"anchorType", "anchor_type"}));
                candidate.container_role = trim_whitespace(first_string_value(node, {"containerRole", "container_role"}));
                candidate.clickable = node.value("clickable", false);
                candidate.container_clickable = node.value("containerClickable", false);
                candidate.badge_like = node.value("badgeLike", false);
                candidate.numeric_like = node.value("numericLike", false);
                candidate.decorative_like = node.value("decorativeLike", false);
                candidate.repeat_group = node.value("repeatGroup", false);
                candidate.score = node.value("score", 0.0);
                snapshot.actionable_candidates.push_back(std::move(candidate));
            }
        }
        if (snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
            int max_right = 0;
            int max_bottom = 0;
            for (const auto& candidate : snapshot.actionable_candidates) {
                int left = 0;
                int top = 0;
                int right = 0;
                int bottom = 0;
                if (std::sscanf(candidate.bounds.c_str(),
                            "[%d,%d][%d,%d]",
                            &left,
                            &top,
                            &right,
                            &bottom) == 4) {
                    max_right = std::max(max_right, right);
                    max_bottom = std::max(max_bottom, bottom);
                }
            }
            if (snapshot.screen_width <= 0) {
                snapshot.screen_width = max_right;
            }
            if (snapshot.screen_height <= 0) {
                snapshot.screen_height = max_bottom;
            }
        }
    } catch (...) {
        return snapshot;
    }
    return snapshot;
}

ObservationFingerprint build_observation_fingerprint(const ObservationSnapshot& snapshot) {
    ObservationFingerprint fingerprint;
    fingerprint.activity = snapshot.activity;
    fingerprint.summary = truncate_runtime_text(snapshot.summary, 120);

    std::set<std::string> seen_labels;
    for (const auto& candidate : snapshot.actionable_candidates) {
        std::string label = trim_whitespace(candidate.label);
        if (label.empty()) {
            label = truncate_runtime_text(candidate.match_text, 60);
        }
        if (label.empty()) {
            continue;
        }
        const std::string normalized = normalize_for_runtime_match(label);
        if (normalized.empty() || seen_labels.count(normalized) > 0) {
            continue;
        }
        seen_labels.insert(normalized);
        fingerprint.actionable_labels.push_back(truncate_runtime_text(label, 48));
        if (fingerprint.actionable_labels.size() >= 6) {
            break;
        }
    }

    std::set<std::string> seen_conflicts;
    for (const auto& conflict : snapshot.warning_conflicts) {
        const std::string code = conflict.code.empty() ? conflict.message : conflict.code;
        const std::string normalized = normalize_for_runtime_match(code);
        if (normalized.empty() || seen_conflicts.count(normalized) > 0) {
            continue;
        }
        seen_conflicts.insert(normalized);
        fingerprint.conflict_codes.push_back(truncate_runtime_text(code, 48));
        if (fingerprint.conflict_codes.size() >= 4) {
            break;
        }
    }

    std::ostringstream stream;
    stream << normalize_for_runtime_match(snapshot.activity) << "|";
    stream << normalize_for_runtime_match(truncate_runtime_text(snapshot.summary, 160)) << "|";
    for (const auto& label : fingerprint.actionable_labels) {
        stream << normalize_for_runtime_match(label) << ",";
    }
    stream << "|";
    for (const auto& code : fingerprint.conflict_codes) {
        stream << normalize_for_runtime_match(code) << ",";
    }
    fingerprint.value = truncate_runtime_text(stream.str(), 512);
    return fingerprint;
}

std::string join_fingerprint_values(const std::vector<std::string>& values) {
    std::ostringstream stream;
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            stream << ",";
        }
        stream << values[i];
    }
    return stream.str();
}

std::string build_navigation_attempt_cache_key(int step_index,
                                               const std::string& fingerprint) {
    if (step_index < 0 || fingerprint.empty()) {
        return "";
    }
    std::ostringstream stream;
    stream << step_index << "|" << truncate_runtime_text(fingerprint, 256);
    return stream.str();
}

std::vector<CanonicalCandidate> build_canonical_candidates(
        const std::vector<ObservationCandidate>& candidates);
std::string build_canonical_candidate_summary(const std::vector<CanonicalCandidate>& candidates,
                                              size_t max_count);
std::string build_navigation_observation_summary(const std::string& content,
                                                 const std::string& canonical_candidate_summary);
std::string build_readout_observation_summary(const std::string& content);
std::string build_navigation_attempt_cache_key(int step_index,
                                               const std::string& fingerprint);
bool candidate_label_matches_step_target_exactly(const ObservationCandidate& candidate,
                                                 const SkillStepHint& step);

std::string activity_simple_name(const std::string& activity);
bool matches_activity_name(const std::string& actual_activity,
                           const std::string& expected_activity);

bool matches_step_page(const ObservationSnapshot& snapshot, const SkillStepHint& step) {
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    if (!step.activity.empty() && matches_activity_name(snapshot.activity, step.activity)) {
        return true;
    }
    if (!step.page.empty() && contains_runtime_match(page_context, step.page)) {
        return true;
    }
    return step.activity.empty() && step.page.empty();
}

bool matches_goal_page(const ObservationSnapshot& snapshot, const std::string& goal) {
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    return contains_runtime_match(page_context, goal);
}

bool matches_any_predicate(const std::string& text,
                           const std::vector<std::string>& predicates) {
    if (predicates.empty()) {
        return true;
    }
    for (const auto& predicate : predicates) {
        if (!predicate.empty() && contains_runtime_match(text, predicate)) {
            return true;
        }
    }
    return false;
}

bool matches_stop_condition(const ObservationSnapshot& snapshot,
                            const StopConditionSpec& stop_condition,
                            const std::string& goal) {
    const std::string page_context = snapshot.activity + " " + snapshot.summary;
    const bool page_hit = matches_any_predicate(page_context, stop_condition.page_predicates);
    const bool content_hit = stop_condition.content_predicates.empty()
            ? matches_goal_page(snapshot, goal)
            : matches_any_predicate(page_context, stop_condition.content_predicates);
    const bool success_hit = !stop_condition.success_signals.empty()
            && matches_any_predicate(page_context, stop_condition.success_signals);
    const bool failure_hit = matches_any_predicate(page_context, stop_condition.failure_signals);
    if (failure_hit && !success_hit) {
        return false;
    }
    if (success_hit && page_hit) {
        return true;
    }
    if (page_hit && content_hit) {
        return true;
    }
    if (page_hit
            && stop_condition.requires_readout
            && !stop_condition.content_predicates.empty()
            && matches_goal_page(snapshot, goal)) {
        return true;
    }
    return false;
}

bool is_mock_observation_result(const nlohmann::json& json) {
    if (!json.is_object()) {
        return false;
    }
    const bool mock = json.value("mock", false);
    const std::string source = json.value("source", "");
    const std::string selection_status = json.value("selectionStatus", "");
    if (mock) {
        return true;
    }
    if (source == "web_dom") {
        return true;
    }
    if (selection_status != "FALLBACK_RESOLVED") {
        return false;
    }
    const bool missing_snapshot = !json.contains("snapshotId") || json.value("snapshotId", "").empty();
    const bool missing_activity = json.value("activityClassName", "").empty();
    const bool missing_native = !json.contains("nativeViewXml") || json["nativeViewXml"].is_null();
    const bool missing_hybrid = !json.contains("hybridObservation") || !json["hybridObservation"].is_object();
    return (missing_snapshot && missing_activity) || (missing_native && missing_hybrid);
}

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

bool looks_like_readout_target_hint(const std::string& target_hint) {
    static const std::vector<std::string> keywords = {
        u8"\u4e91\u7a7a\u95f4", u8"\u6587\u6863", u8"\u6587\u4ef6", u8"\u5185\u5bb9",
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
    if (state.awaiting_step_confirmation_index < 0 || state.awaiting_confirmation_retry_count <= 0) {
        return;
    }

    ICRAW_LOG_INFO(
            "[AgentLoop][execution_state_confirmation_retry_wait] step_index={} retry_count={} delay_ms={}",
            state.awaiting_step_confirmation_index,
            state.awaiting_confirmation_retry_count,
            static_cast<int>(kPendingConfirmationRetryDelay.count()));
    std::this_thread::sleep_for(kPendingConfirmationRetryDelay);
}

void record_completed_step(ExecutionState& state, const SkillStepHint& step) {
    state.completed_steps.push_back(describe_step(step));
    if (state.completed_steps.size() > EXECUTION_STATE_MAX_TOOL_EVENTS) {
        state.completed_steps.erase(state.completed_steps.begin());
    }
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
        confirmed = matches_step_page(snapshot, next_step);
        if (!confirmed && next_step.readout) {
            confirmed = matches_goal_page(snapshot, state.goal)
                    || (state.route_ready
                        && matches_stop_condition(snapshot, state.intent_route.stop_condition, state.goal));
        }
        if (confirmed) {
            record_completed_step(state, current_step);
            state.pending_step_index = next_index;
            state.pending_step = build_step_json(next_step);
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
    for (size_t i = 0; i < steps.size(); ++i) {
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
                refresh_pending_step_from_observation(state, snapshot);
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
    if (last_hint.empty()) {
        return false;
    }
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

void normalize_tool_call_arguments(ToolCall& tool_call);

std::vector<ToolCall> enrich_tool_calls_for_execution(const std::vector<ToolCall>& tool_calls,
                                                      const ExecutionState& state,
                                                      const std::vector<Message>& messages) {
    std::vector<ToolCall> enriched = tool_calls;
    const std::string detail_mode = determine_observation_detail_mode(state, messages);
    for (auto& tool_call : enriched) {
        normalize_tool_call_arguments(tool_call);
        if (tool_call.name != "android_view_context_tool" || !tool_call.arguments.is_object()) {
            continue;
        }
        tool_call.arguments["__detailMode"] = detail_mode;
        if (state.pending_step.is_object()
                && state.pending_step.contains("target")
                && state.pending_step["target"].is_string()) {
            const std::string pending_target = trim_whitespace(
                    state.pending_step["target"].get<std::string>());
            const std::string existing_target = trim_whitespace(
                    first_string_value(tool_call.arguments, {"targetHint", "target", "pageHint"}));
            if (!pending_target.empty()
                    && (existing_target.empty()
                        || (!contains_runtime_match(existing_target, pending_target)
                            && !contains_runtime_match(pending_target, existing_target)))) {
                tool_call.arguments["targetHint"] = pending_target;
            }
        }
        ICRAW_LOG_INFO("[AgentLoop][observation_detail_mode] mode={} phase={}", detail_mode, state.phase);
    }
    return enriched;
}

bool parse_bounds_center(const std::string& bounds, int& center_x, int& center_y) {
    int left = 0;
    int top = 0;
    int right = 0;
    int bottom = 0;
    if (std::sscanf(bounds.c_str(), "[%d,%d][%d,%d]", &left, &top, &right, &bottom) != 4) {
        return false;
    }
    center_x = (left + right) / 2;
    center_y = (top + bottom) / 2;
    return true;
}

struct BoundsRect {
    int left = 0;
    int top = 0;
    int right = 0;
    int bottom = 0;
};

bool parse_bounds_rect(const std::string& bounds, BoundsRect& rect) {
    return std::sscanf(bounds.c_str(),
            "[%d,%d][%d,%d]",
            &rect.left,
            &rect.top,
            &rect.right,
            &rect.bottom) == 4;
}

bool bounds_overlap(const std::string& left_bounds, const std::string& right_bounds) {
    BoundsRect left;
    BoundsRect right;
    if (!parse_bounds_rect(left_bounds, left) || !parse_bounds_rect(right_bounds, right)) {
        return false;
    }
    return std::max(left.left, right.left) < std::min(left.right, right.right)
            && std::max(left.top, right.top) < std::min(left.bottom, right.bottom);
}

bool is_low_risk_warning_conflict(const ObservationConflict& conflict) {
    return conflict.code == "vision_compaction_drop_summary";
}

bool is_stable_non_vision_candidate(const ObservationCandidate& candidate) {
    return candidate.source == "native" || candidate.source == "fused";
}

bool is_relaxable_vision_only_conflict(const ObservationConflict& conflict,
                                       const ObservationCandidate& candidate) {
    if (conflict.code != "vision_only_candidate") {
        return false;
    }
    if (!is_stable_non_vision_candidate(candidate)) {
        return false;
    }
    if (!conflict.bounds.empty() && !candidate.bounds.empty()
            && bounds_overlap(conflict.bounds, candidate.bounds)) {
        return false;
    }
    return true;
}

bool should_ignore_exact_target_vision_only_conflict(const ObservationConflict& conflict,
                                                     const SkillStepHint& step,
                                                     const ObservationCandidate& candidate) {
    if (conflict.code != "vision_only_candidate") {
        return false;
    }
    if (!is_stable_non_vision_candidate(candidate)) {
        return false;
    }
    if (!candidate_label_matches_step_target_exactly(candidate, step)) {
        return false;
    }
    // Canonical candidates may inherit decorative/repeat hints from sibling icon/text members in the same
    // clickable container. Keep blocking truly risky badge/numeric targets, but allow exact stable entries
    // to ignore unrelated vision-only warnings.
    if (candidate.badge_like || candidate.numeric_like) {
        return false;
    }
    return candidate.clickable || candidate.container_clickable;
}

bool is_target_related_warning_conflict(const ObservationConflict& conflict,
                                        const SkillStepHint& step,
                                        const ObservationCandidate& candidate) {
    if (!conflict.bounds.empty() && !candidate.bounds.empty()
            && bounds_overlap(conflict.bounds, candidate.bounds)) {
        return true;
    }
    const std::string conflict_text =
            trim_whitespace(conflict.code + " " + conflict.message);
    if (!step.target.empty() && contains_runtime_match(conflict_text, step.target)) {
        return true;
    }
    for (const auto& alias : step.aliases) {
        if (contains_runtime_match(conflict_text, alias)) {
            return true;
        }
    }
    if (!candidate.label.empty() && contains_runtime_match(conflict_text, candidate.label)) {
        return true;
    }
    if (!candidate.match_text.empty() && contains_runtime_match(conflict_text, candidate.match_text)) {
        return true;
    }
    return false;
}

bool should_block_fast_execute_on_conflicts(const ObservationSnapshot& snapshot,
                                            const SkillStepHint& step,
                                            const ObservationCandidate& candidate,
                                            std::string& reason) {
    if (!snapshot.has_warning_conflict || snapshot.warning_conflicts.empty()) {
        return false;
    }
    size_t non_low_risk_warning_count = 0;
    for (const auto& conflict : snapshot.warning_conflicts) {
        if (is_low_risk_warning_conflict(conflict)) {
            continue;
        }
        if (is_relaxable_vision_only_conflict(conflict, candidate)) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_conflict_ignored] code={} candidate={} candidate_source={} reason=stable_non_vision_candidate_non_overlapping",
                    conflict.code,
                    candidate.label,
                    candidate.source);
            continue;
        }
        if (should_ignore_exact_target_vision_only_conflict(conflict, step, candidate)) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_conflict_ignored] code={} candidate={} candidate_source={} reason=stable_exact_target_candidate",
                    conflict.code,
                    candidate.label,
                    candidate.source);
            continue;
        }
        non_low_risk_warning_count++;
        if (is_target_related_warning_conflict(conflict, step, candidate)) {
            reason = conflict.code.empty()
                    ? "warning_conflict_target_related"
                    : ("warning_conflict_" + conflict.code);
            return true;
        }
    }
    if (non_low_risk_warning_count >= 2) {
        reason = "warning_conflict_multiple";
        return true;
    }
    return false;
}

std::string normalize_runtime_key(const std::string& text) {
    return normalize_for_runtime_match(text);
}

std::string activity_simple_name(const std::string& activity) {
    const std::string trimmed = trim_whitespace(activity);
    const size_t last_dot = trimmed.find_last_of('.');
    if (last_dot == std::string::npos) {
        return trimmed;
    }
    return trimmed.substr(last_dot + 1);
}

bool matches_activity_name(const std::string& actual_activity,
                           const std::string& expected_activity) {
    const std::string actual_trimmed = trim_whitespace(actual_activity);
    const std::string expected_trimmed = trim_whitespace(expected_activity);
    if (actual_trimmed.empty() || expected_trimmed.empty()) {
        return false;
    }

    const std::string normalized_actual = normalize_runtime_key(actual_trimmed);
    const std::string normalized_expected = normalize_runtime_key(expected_trimmed);
    if (normalized_actual == normalized_expected) {
        return true;
    }

    const std::string normalized_actual_simple = normalize_runtime_key(activity_simple_name(actual_trimmed));
    const std::string normalized_expected_simple = normalize_runtime_key(activity_simple_name(expected_trimmed));
    return !normalized_actual_simple.empty()
            && normalized_actual_simple == normalized_expected_simple;
}

int candidate_term_match_strength(const ObservationCandidate& candidate,
                                  const std::string& term,
                                  bool is_primary) {
    const std::string label = normalize_runtime_key(candidate.label);
    const std::string match_text = normalize_runtime_key(candidate.match_text);
    const std::string normalized = normalize_runtime_key(term);
    if (normalized.empty()) {
        return 0;
    }

    const int exact_label = is_primary ? 120 : 90;
    const int contains_label = is_primary ? 90 : 55;
    const int contains_text = is_primary ? 55 : 0;
    if (!label.empty() && label == normalized) {
        return exact_label;
    }
    if (!label.empty() && label.find(normalized) != std::string::npos) {
        return contains_label;
    }
    if (contains_text > 0 && !match_text.empty() && match_text.find(normalized) != std::string::npos) {
        return contains_text;
    }
    return 0;
}

int candidate_match_strength(const ObservationCandidate& candidate,
                             const SkillStepHint& step) {
    int best = candidate_term_match_strength(candidate, step.target, true);
    for (const auto& alias : step.aliases) {
        best = std::max(best, candidate_term_match_strength(candidate, alias, false));
    }
    return best;
}

bool step_prefers_corner_entry(const SkillStepHint& step) {
    if (contains_runtime_match(step.region, "top_left")
            || contains_runtime_match(step.region, "header_left")
            || contains_runtime_match(step.anchor_type, "profile_entry")) {
        return true;
    }

    static const std::vector<std::string> keywords = {
        u8"\u5de6\u4e0a\u89d2", u8"\u5934\u50cf", u8"\u4e2a\u4eba\u5934\u50cf", u8"\u6211\u7684\u5934\u50cf",
        u8"\u4e2a\u4eba\u4e2d\u5fc3", u8"\u6211\u7684", u8"\u6211", "avatar", "profile", "me", "mine"
    };
    for (const auto& keyword : keywords) {
        if ((!step.target.empty() && contains_runtime_match(step.target, keyword))) {
            return true;
        }
        for (const auto& alias : step.aliases) {
            if (contains_runtime_match(alias, keyword)) {
                return true;
            }
        }
    }
    return false;
}

std::string normalize_region_label(const std::string& region) {
    std::string normalized;
    normalized.reserve(region.size());
    for (unsigned char ch : region) {
        if (std::isalnum(ch)) {
            normalized.push_back(static_cast<char>(std::tolower(ch)));
        } else if (ch == '-' || ch == ' ' || ch == '.') {
            normalized.push_back('_');
        } else if (ch >= 0x80) {
            normalized.push_back(static_cast<char>(ch));
        }
    }

    while (!normalized.empty() && normalized.front() == '_') {
        normalized.erase(normalized.begin());
    }
    while (!normalized.empty() && normalized.back() == '_') {
        normalized.pop_back();
    }
    while (normalized.find("__") != std::string::npos) {
        normalized.replace(normalized.find("__"), 2, "_");
    }
    return normalized;
}

std::string preferred_region_for_step(const SkillStepHint& step) {
    const std::string explicit_region = normalize_region_label(step.region);
    if (!explicit_region.empty()) {
        return explicit_region;
    }
    if (step_prefers_corner_entry(step)) {
        return "top_left";
    }
    return "";
}

bool candidate_in_region(const ObservationCandidate& candidate,
                         const std::string& region,
                         const ObservationSnapshot& snapshot) {
    const std::string normalized_region = normalize_region_label(region);
    if (normalized_region.empty()) {
        return false;
    }

    if (!candidate.region.empty()
            && normalize_region_label(candidate.region) == normalized_region) {
        return true;
    }

    if (snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
        return false;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(candidate.bounds, center_x, center_y)) {
        return false;
    }

    const double width = static_cast<double>(snapshot.screen_width);
    const double height = static_cast<double>(snapshot.screen_height);
    const double x = static_cast<double>(center_x);
    const double y = static_cast<double>(center_y);

    const bool top = y <= height * 0.30;
    const bool bottom = y >= height * 0.70;
    const bool left = x <= width * 0.42;
    const bool right = x >= width * 0.58;
    const bool middle_x = x > width * 0.32 && x < width * 0.68;
    const bool middle_y = y > height * 0.28 && y < height * 0.72;

    if (normalized_region == "top_left") {
        return top && left;
    }
    if (normalized_region == "top_right") {
        return top && right;
    }
    if (normalized_region == "top_center") {
        return top && middle_x;
    }
    if (normalized_region == "top") {
        return top;
    }
    if (normalized_region == "bottom_left") {
        return bottom && left;
    }
    if (normalized_region == "bottom_right") {
        return bottom && right;
    }
    if (normalized_region == "bottom_center") {
        return bottom && middle_x;
    }
    if (normalized_region == "bottom") {
        return bottom;
    }
    if (normalized_region == "left") {
        return left && middle_y;
    }
    if (normalized_region == "right") {
        return right && middle_y;
    }
    if (normalized_region == "center") {
        return middle_x && middle_y;
    }
    if (normalized_region == "header_left") {
        return top && left;
    }
    if (normalized_region == "header_right") {
        return top && right;
    }
    if (normalized_region == "bottom_tab") {
        return bottom;
    }
    return false;
}

double region_bonus_for_label(const std::string& region) {
    if (region == "top_left") {
        return 36.0;
    }
    if (region == "top_right") {
        return 28.0;
    }
    if (region == "top_center" || region == "bottom_center") {
        return 20.0;
    }
    if (region == "top" || region == "bottom" || region == "left" || region == "right") {
        return 14.0;
    }
    if (region == "center") {
        return 12.0;
    }
    return 18.0;
}

int candidate_source_bonus(const ObservationCandidate& candidate) {
    if (candidate.source == "fused") {
        return 30;
    }
    if (candidate.source == "native") {
        return 24;
    }
    if (candidate.source == "vision_only") {
        return 8;
    }
    return 12;
}

double candidate_anchor_type_bonus(const ObservationCandidate& candidate,
                                   const SkillStepHint& step) {
    if (step.anchor_type.empty() || candidate.anchor_type.empty()) {
        return 0.0;
    }
    if (normalize_region_label(step.anchor_type) == normalize_region_label(candidate.anchor_type)) {
        return 24.0;
    }
    return 0.0;
}

double candidate_container_role_bonus(const ObservationCandidate& candidate,
                                      const SkillStepHint& step) {
    if (step.container_role.empty() || candidate.container_role.empty()) {
        return 0.0;
    }
    if (normalize_region_label(step.container_role) == normalize_region_label(candidate.container_role)) {
        return 18.0;
    }
    return 0.0;
}

bool candidate_anchor_type_is(const ObservationCandidate& candidate, const std::string& expected) {
    return !candidate.anchor_type.empty()
            && normalize_region_label(candidate.anchor_type) == normalize_region_label(expected);
}

bool candidate_container_role_is(const ObservationCandidate& candidate, const std::string& expected) {
    return !candidate.container_role.empty()
            && normalize_region_label(candidate.container_role) == normalize_region_label(expected);
}

bool candidate_has_corner_entry_semantics(const ObservationCandidate& candidate) {
    if (candidate_anchor_type_is(candidate, "profile_entry")) {
        return true;
    }
    static const std::vector<std::string> keywords = {
        u8"\u5de6\u4e0a\u89d2", u8"\u5934\u50cf", u8"\u4e2a\u4eba\u5934\u50cf", u8"\u6211\u7684\u5934\u50cf",
        u8"\u4e2a\u4eba\u4e2d\u5fc3", u8"\u6211\u7684", u8"\u6211", "avatar", "profile", "me", "mine"
    };
    for (const auto& keyword : keywords) {
        if (contains_runtime_match(candidate.label, keyword)
                || contains_runtime_match(candidate.match_text, keyword)) {
            return true;
        }
    }
    return false;
}

bool candidate_is_corner_entry_noise(const ObservationCandidate& candidate) {
    return candidate_anchor_type_is(candidate, "back_entry")
            || candidate_anchor_type_is(candidate, "tab_entry")
            || candidate_anchor_type_is(candidate, "search_entry")
            || candidate_anchor_type_is(candidate, "overflow_entry")
            || candidate_anchor_type_is(candidate, "primary_action")
            || candidate_container_role_is(candidate, "tab_bar")
            || candidate_container_role_is(candidate, "badge")
            || candidate.badge_like;
}

int candidate_actionability_rank(const ObservationCandidate& candidate) {
    int rank = 0;
    if (candidate.clickable) {
        rank += 3;
    }
    if (candidate.container_clickable) {
        rank += 2;
    }
    if (candidate.source == "fused") {
        rank += 2;
    } else if (candidate.source == "native") {
        rank += 1;
    }
    if (candidate_anchor_type_is(candidate, "profile_entry")) {
        rank += 2;
    }
    return rank;
}

bool bounds_are_nearby(const std::string& left_bounds,
                       const std::string& right_bounds,
                       int max_gap_px = 40) {
    BoundsRect left;
    BoundsRect right;
    if (!parse_bounds_rect(left_bounds, left) || !parse_bounds_rect(right_bounds, right)) {
        return false;
    }

    const int horizontal_gap = std::max(0, std::max(right.left - left.right, left.left - right.right));
    const int vertical_gap = std::max(0, std::max(right.top - left.bottom, left.top - right.bottom));
    return horizontal_gap <= max_gap_px && vertical_gap <= max_gap_px;
}

bool candidates_reference_similar_semantics(const ObservationCandidate& left,
                                            const ObservationCandidate& right) {
    const std::string left_label = normalize_runtime_key(left.label);
    const std::string right_label = normalize_runtime_key(right.label);
    const std::string left_resource = normalize_runtime_key(left.resource_id);
    const std::string right_resource = normalize_runtime_key(right.resource_id);
    if (!left_resource.empty() && left_resource == right_resource) {
        return true;
    }
    if (!left_label.empty() && left_label == right_label) {
        return true;
    }
    if (!left.label.empty() && contains_runtime_match(right.match_text, left.label)) {
        return true;
    }
    if (!right.label.empty() && contains_runtime_match(left.match_text, right.label)) {
        return true;
    }
    return false;
}

bool candidates_should_merge_canonically(const ObservationCandidate& left,
                                         const ObservationCandidate& right) {
    const bool spatially_related =
            bounds_overlap(left.bounds, right.bounds)
            || bounds_are_nearby(left.bounds, right.bounds);
    if (!spatially_related) {
        return false;
    }
    if (candidates_reference_similar_semantics(left, right)) {
        return true;
    }

    const bool same_anchor = !left.anchor_type.empty()
            && normalize_region_label(left.anchor_type) == normalize_region_label(right.anchor_type);
    const bool same_container = !left.container_role.empty()
            && normalize_region_label(left.container_role) == normalize_region_label(right.container_role);
    const bool complementary_actionability =
            (left.clickable != right.clickable)
            || (left.container_clickable != right.container_clickable);
    return complementary_actionability && (same_anchor || same_container);
}

int candidate_display_label_score(const ObservationCandidate& candidate) {
    int score = 0;
    const std::string label = trim_whitespace(candidate.label);
    if (!label.empty()) {
        score += static_cast<int>(std::min<size_t>(label.size(), 40));
    }
    if (!candidate.resource_id.empty() && contains_runtime_match(label, candidate.resource_id)) {
        score -= 4;
    }
    if (contains_runtime_match(label, "imageview")
            || contains_runtime_match(label, "textview")
            || contains_runtime_match(label, "view")) {
        score -= 8;
    }
    if (candidate.clickable || candidate.container_clickable) {
        score += 6;
    }
    if (candidate.source == "fused") {
        score += 4;
    } else if (candidate.source == "native") {
        score += 3;
    }
    return score;
}

int candidate_tap_priority_score(const ObservationCandidate& candidate) {
    int score = candidate_actionability_rank(candidate) * 100;
    score += candidate_source_bonus(candidate) * 4;
    score += static_cast<int>(candidate.score * 100.0);
    if (candidate.clickable) {
        score += 18;
    }
    if (candidate.container_clickable) {
        score += 12;
    }
    return score;
}

CanonicalCandidate build_canonical_candidate(const std::vector<ObservationCandidate>& members) {
    CanonicalCandidate canonical;
    canonical.members = members;
    canonical.member_count = members.size();

    if (members.empty()) {
        return canonical;
    }

    size_t label_index = 0;
    size_t tap_index = 0;
    size_t stable_tap_index = 0;
    int best_label_score = std::numeric_limits<int>::min();
    int best_tap_score = std::numeric_limits<int>::min();
    int best_stable_tap_score = std::numeric_limits<int>::min();
    bool has_stable_tap_candidate = false;
    std::set<std::string> source_set;
    std::vector<std::string> risk_flags;
    std::set<std::string> risk_seen;
    std::vector<std::string> merged_terms;
    std::set<std::string> seen_terms;
    int max_actionability_rank = 0;

    auto append_term = [&](const std::string& term) {
        const std::string trimmed = trim_whitespace(term);
        if (trimmed.empty()) {
            return;
        }
        const std::string normalized = normalize_runtime_key(trimmed);
        if (normalized.empty() || seen_terms.count(normalized) > 0) {
            return;
        }
        seen_terms.insert(normalized);
        merged_terms.push_back(truncate_runtime_text(trimmed, 64));
    };

    auto append_risk_flag = [&](const std::string& flag) {
        if (flag.empty() || risk_seen.count(flag) > 0) {
            return;
        }
        risk_seen.insert(flag);
        risk_flags.push_back(flag);
    };

    for (size_t i = 0; i < members.size(); ++i) {
        const auto& member = members[i];
        const int label_score = candidate_display_label_score(member);
        const int tap_score = candidate_tap_priority_score(member);
        if (label_score > best_label_score) {
            best_label_score = label_score;
            label_index = i;
        }
        if (tap_score > best_tap_score) {
            best_tap_score = tap_score;
            tap_index = i;
        }
        if (is_stable_non_vision_candidate(member) && (member.clickable || member.container_clickable)) {
            has_stable_tap_candidate = true;
            if (tap_score > best_stable_tap_score) {
                best_stable_tap_score = tap_score;
                stable_tap_index = i;
            }
        }
        if (!member.source.empty()) {
            source_set.insert(member.source);
        }
        append_term(member.label);
        append_term(member.match_text);
        append_term(member.resource_id);
        if (member.badge_like) {
            append_risk_flag("badge_like");
        }
        if (member.numeric_like) {
            append_risk_flag("numeric_like");
        }
        if (member.decorative_like) {
            append_risk_flag("decorative_like");
        }
        if (member.repeat_group) {
            append_risk_flag("repeat_group");
        }
        max_actionability_rank = std::max(max_actionability_rank, candidate_actionability_rank(member));
    }

    if (has_stable_tap_candidate) {
        tap_index = stable_tap_index;
    }

    canonical.aggregate = members[tap_index];
    canonical.aggregate.label = members[label_index].label.empty()
            ? members[tap_index].label
            : members[label_index].label;
    canonical.aggregate.match_text = truncate_runtime_text(join_string_values(merged_terms, " | "), 240);
    canonical.aggregate.resource_id = members[tap_index].resource_id.empty()
            ? members[label_index].resource_id
            : members[tap_index].resource_id;
    canonical.aggregate.clickable = false;
    canonical.aggregate.container_clickable = false;
    canonical.aggregate.badge_like = false;
    canonical.aggregate.numeric_like = false;
    canonical.aggregate.decorative_like = false;
    canonical.aggregate.repeat_group = false;
    canonical.aggregate.score = 0.0;
    for (const auto& member : members) {
        canonical.aggregate.clickable = canonical.aggregate.clickable || member.clickable;
        canonical.aggregate.container_clickable =
                canonical.aggregate.container_clickable || member.container_clickable;
        canonical.aggregate.badge_like = canonical.aggregate.badge_like || member.badge_like;
        canonical.aggregate.numeric_like = canonical.aggregate.numeric_like || member.numeric_like;
        canonical.aggregate.decorative_like =
                canonical.aggregate.decorative_like || member.decorative_like;
        canonical.aggregate.repeat_group = canonical.aggregate.repeat_group || member.repeat_group;
        canonical.aggregate.score = std::max(canonical.aggregate.score, member.score);
    }
    canonical.display_label = canonical.aggregate.label.empty()
            ? truncate_runtime_text(canonical.aggregate.match_text, 64)
            : canonical.aggregate.label;
    canonical.tap_bounds = canonical.aggregate.bounds;
    canonical.source_set = join_string_values(
            std::vector<std::string>(source_set.begin(), source_set.end()), ",");
    canonical.risk_flags = join_string_values(risk_flags, ",");
    canonical.actionability_rank = max_actionability_rank;
    return canonical;
}

std::vector<CanonicalCandidate> build_canonical_candidates(
        const std::vector<ObservationCandidate>& candidates) {
    std::vector<CanonicalCandidate> canonical_candidates;
    std::vector<bool> consumed(candidates.size(), false);
    for (size_t i = 0; i < candidates.size(); ++i) {
        if (consumed[i]) {
            continue;
        }
        std::vector<ObservationCandidate> cluster;
        cluster.push_back(candidates[i]);
        consumed[i] = true;

        bool expanded = true;
        while (expanded) {
            expanded = false;
            for (size_t j = i + 1; j < candidates.size(); ++j) {
                if (consumed[j]) {
                    continue;
                }
                bool merge = false;
                for (const auto& member : cluster) {
                    if (candidates_should_merge_canonically(member, candidates[j])) {
                        merge = true;
                        break;
                    }
                }
                if (!merge) {
                    continue;
                }
                cluster.push_back(candidates[j]);
                consumed[j] = true;
                expanded = true;
            }
        }

        canonical_candidates.push_back(build_canonical_candidate(cluster));
    }
    return canonical_candidates;
}

std::string build_canonical_candidate_summary(const std::vector<CanonicalCandidate>& candidates,
                                              size_t max_count) {
    nlohmann::json summary = nlohmann::json::array();
    for (size_t i = 0; i < candidates.size() && i < max_count; ++i) {
        const auto& candidate = candidates[i];
        nlohmann::json item = nlohmann::json::object();
        item["displayLabel"] = candidate.display_label;
        item["tapBounds"] = candidate.tap_bounds;
        item["sourceSet"] = candidate.source_set;
        item["actionabilityRank"] = candidate.actionability_rank;
        item["memberCount"] = candidate.member_count;
        if (!candidate.risk_flags.empty()) {
            item["riskFlags"] = candidate.risk_flags;
        }
        summary.push_back(std::move(item));
    }
    return truncate_runtime_text(summary.dump(), 1200);
}

bool candidates_look_like_overlapping_duplicates(const ObservationCandidate& left,
                                                 const ObservationCandidate& right) {
    if (left.bounds.empty() || right.bounds.empty() || !bounds_overlap(left.bounds, right.bounds)) {
        return false;
    }
    const std::string left_label = normalize_runtime_key(left.label);
    const std::string right_label = normalize_runtime_key(right.label);
    if (!left_label.empty() && left_label == right_label) {
        return true;
    }
    return (!left.label.empty() && contains_runtime_match(right.match_text, left.label))
            || (!right.label.empty() && contains_runtime_match(left.match_text, right.label));
}

bool candidate_label_matches_step_target_exactly(const ObservationCandidate& candidate,
                                                 const SkillStepHint& step) {
    const std::string candidate_label = normalize_runtime_key(candidate.label);
    if (candidate_label.empty()) {
        return false;
    }
    if (!step.target.empty() && candidate_label == normalize_runtime_key(step.target)) {
        return true;
    }
    for (const auto& alias : step.aliases) {
        if (!alias.empty() && candidate_label == normalize_runtime_key(alias)) {
            return true;
        }
    }
    return false;
}

bool candidates_are_overlapping_same_target_label(const ObservationCandidate& left,
                                                  const ObservationCandidate& right,
                                                  const SkillStepHint& step) {
    if (!candidates_look_like_overlapping_duplicates(left, right)) {
        return false;
    }
    const std::string left_label = normalize_runtime_key(left.label);
    const std::string right_label = normalize_runtime_key(right.label);
    return !left_label.empty()
            && left_label == right_label
            && candidate_label_matches_step_target_exactly(left, step)
            && candidate_label_matches_step_target_exactly(right, step);
}

double candidate_entry_semantic_bonus(const ObservationCandidate& candidate,
                                      const SkillStepHint& step,
                                      const ObservationSnapshot& snapshot) {
    if (!step_prefers_corner_entry(step)) {
        return 0.0;
    }

    double bonus = 0.0;
    if (candidate_anchor_type_is(candidate, "profile_entry")) {
        bonus += 42.0;
    }
    if (candidate_container_role_is(candidate, "header")) {
        bonus += 12.0;
    }
    const std::string preferred_region = preferred_region_for_step(step);
    if (!preferred_region.empty() && candidate_in_region(candidate, preferred_region, snapshot)) {
        bonus += 10.0;
    }
    return bonus;
}

double candidate_entry_mismatch_penalty(const ObservationCandidate& candidate,
                                        const SkillStepHint& step) {
    if (!step_prefers_corner_entry(step)) {
        return 0.0;
    }

    double penalty = 0.0;
    if (candidate_anchor_type_is(candidate, "back_entry")) {
        penalty += 80.0;
    }
    if (candidate_anchor_type_is(candidate, "tab_entry")) {
        penalty += 28.0;
    }
    if (candidate_anchor_type_is(candidate, "search_entry")) {
        penalty += 28.0;
    }
    if (candidate_anchor_type_is(candidate, "overflow_entry")) {
        penalty += 24.0;
    }
    if (candidate_anchor_type_is(candidate, "primary_action")) {
        penalty += 16.0;
    }
    if (candidate_container_role_is(candidate, "tab_bar")) {
        penalty += 24.0;
    }
    return penalty;
}

double candidate_risk_penalty(const ObservationCandidate& candidate) {
    double penalty = 0.0;
    if (candidate.badge_like) {
        penalty += 36.0;
    }
    if (candidate.numeric_like) {
        penalty += 18.0;
    }
    if (candidate.decorative_like) {
        penalty += 12.0;
    }
    if (candidate.repeat_group) {
        penalty += 10.0;
    }
    return penalty;
}

double candidate_region_bonus(const ObservationCandidate& candidate,
                              const SkillStepHint& step,
                              const ObservationSnapshot& snapshot) {
    const std::string preferred_region = preferred_region_for_step(step);
    if (preferred_region.empty()) {
        return 0.0;
    }
    if (!candidate_in_region(candidate, preferred_region, snapshot)) {
        return 0.0;
    }
    return region_bonus_for_label(preferred_region);
}

bool candidate_in_preferred_entry_region(const ObservationCandidate& candidate,
                                         const SkillStepHint& step,
                                         const ObservationSnapshot& snapshot) {
    const std::string preferred_region = preferred_region_for_step(step);
    return candidate_in_region(candidate, preferred_region, snapshot);
}

bool candidate_in_tight_corner_entry_region(const ObservationCandidate& candidate,
                                            const ObservationSnapshot& snapshot) {
    if (snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
        return false;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(candidate.bounds, center_x, center_y)) {
        return false;
    }

    const double width = static_cast<double>(snapshot.screen_width);
    const double height = static_cast<double>(snapshot.screen_height);
    return center_x <= static_cast<int>(width * 0.26)
            && center_y <= static_cast<int>(height * 0.16);
}

bool candidate_is_midpage_list_or_card_candidate(const ObservationCandidate& candidate,
                                                 const ObservationSnapshot& snapshot) {
    if (candidate_in_tight_corner_entry_region(candidate, snapshot)) {
        return false;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(candidate.bounds, center_x, center_y)) {
        return false;
    }

    const double width = static_cast<double>(snapshot.screen_width);
    const double height = static_cast<double>(snapshot.screen_height);
    if (width <= 0.0 || height <= 0.0) {
        return false;
    }

    const bool below_header_band = center_y >= static_cast<int>(height * 0.22);
    const bool within_content_band = center_y <= static_cast<int>(height * 0.88);
    const bool within_page_body = center_x >= static_cast<int>(width * 0.05)
            && center_x <= static_cast<int>(width * 0.95);
    const bool list_like = candidate_container_role_is(candidate, "list")
            || candidate_container_role_is(candidate, "card")
            || candidate.repeat_group;
    const bool card_like_action = candidate_anchor_type_is(candidate, "primary_action")
            || candidate_anchor_type_is(candidate, "tab_entry");

    return below_header_band
            && within_content_band
            && within_page_body
            && (list_like || card_like_action);
}

size_t filter_corner_entry_midpage_candidates(std::vector<ObservationCandidate>& candidates,
                                              const ObservationSnapshot& snapshot) {
    const size_t before = candidates.size();
    candidates.erase(std::remove_if(candidates.begin(), candidates.end(),
                            [&](const ObservationCandidate& candidate) {
                                return candidate_is_midpage_list_or_card_candidate(candidate, snapshot);
                            }),
            candidates.end());
    return before > candidates.size() ? (before - candidates.size()) : 0;
}

double candidate_disambiguation_score(const ObservationCandidate& candidate,
                                      const SkillStepHint& step,
                                      const ObservationSnapshot& snapshot) {
    return static_cast<double>(candidate_match_strength(candidate, step))
            + static_cast<double>(candidate_source_bonus(candidate))
            + candidate_region_bonus(candidate, step, snapshot)
            + candidate_anchor_type_bonus(candidate, step)
            + candidate_container_role_bonus(candidate, step)
            + candidate_entry_semantic_bonus(candidate, step, snapshot)
            + (candidate.clickable ? 8.0 : 0.0)
            + (candidate.container_clickable ? 6.0 : 0.0)
            + (candidate.score * 10.0)
            - candidate_risk_penalty(candidate)
            - candidate_entry_mismatch_penalty(candidate, step);
}

bool region_allows_top_left_coordinate_recovery(const std::string& region) {
    const std::string normalized_region = normalize_region_label(region);
    if (normalized_region.empty()) {
        return true;
    }
    if (normalized_region.find("right") != std::string::npos
            || normalized_region.find("bottom") != std::string::npos
            || contains_runtime_match(region, u8"\u53f3")
            || contains_runtime_match(region, u8"\u4e0b")) {
        return false;
    }
    return true;
}

std::optional<ToolCall> build_corner_region_recovery_tool_call(const SkillStepHint& step,
                                                               const ObservationSnapshot& snapshot) {
    if (!step_prefers_corner_entry(step) || snapshot.snapshot_id.empty()
            || snapshot.screen_width <= 0 || snapshot.screen_height <= 0) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_region_coordinate_recovery_skipped] target={} prefers_corner={} has_snapshot={} screen_width={} screen_height={}",
                step.target,
                step_prefers_corner_entry(step),
                !snapshot.snapshot_id.empty(),
                snapshot.screen_width,
                snapshot.screen_height);
        return std::nullopt;
    }

    const std::string preferred_region = preferred_region_for_step(step);
    if (!region_allows_top_left_coordinate_recovery(preferred_region)) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_region_coordinate_recovery_skipped] target={} reason=region_not_top_left preferred_region={}",
                step.target,
                preferred_region);
        return std::nullopt;
    }

    // Corner entries such as avatars are often plain ImageViews and may not appear in
    // hybrid actionableNodes. Use a conservative header coordinate, then verify progress
    // through the normal observation/confirmation path.
    const int tap_x = std::max(1, static_cast<int>(snapshot.screen_width * 0.076));
    const int tap_y = std::max(1, static_cast<int>(snapshot.screen_height * 0.072));
    const int bound_left = std::max(0, tap_x - static_cast<int>(snapshot.screen_width * 0.055));
    const int bound_top = std::max(0, tap_y - static_cast<int>(snapshot.screen_height * 0.040));
    const int bound_right = std::min(snapshot.screen_width, tap_x + static_cast<int>(snapshot.screen_width * 0.055));
    const int bound_bottom = std::min(snapshot.screen_height, tap_y + static_cast<int>(snapshot.screen_height * 0.040));
    std::ostringstream bounds;
    bounds << "[" << bound_left << "," << bound_top << "]["
           << bound_right << "," << bound_bottom << "]";

    ToolCall tool_call;
    tool_call.id = generate_tool_id();
    tool_call.name = "android_gesture_tool";
    tool_call.arguments = nlohmann::json::object({
            {"action", "tap"},
            {"x", tap_x},
            {"y", tap_y},
            {"observation", {
                    {"snapshotId", snapshot.snapshot_id},
                    {"targetDescriptor", step.target.empty() ? "top-left entry" : step.target},
                    {"referencedBounds", bounds.str()},
                    {"recovery", "corner_region_coordinate"}
            }}
    });
    ICRAW_LOG_INFO(
            "[AgentLoop][fast_execute_region_coordinate_recovery] action=tap target={} region={} x={} y={} page={} snapshot_id={}",
            step.target,
            preferred_region.empty() ? "top_left" : preferred_region,
            tap_x,
            tap_y,
            snapshot.activity,
            snapshot.snapshot_id);
    return tool_call;
}

std::optional<ToolCall> maybe_build_fast_execute_tool_call(const ExecutionState& state,
                                                           const std::string& observation_content) {
    if (state.mode != "planned_fast_execute" || state.phase == "readout"
            || !state.active_hints || state.pending_step_index < 0
            || state.pending_step_index >= static_cast<int>(state.active_hints->steps.size())) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_fallback] reason=not_eligible mode={} phase={} has_hints={} pending_step_index={}",
                state.mode,
                state.phase,
                state.active_hints ? "true" : "false",
                state.pending_step_index);
        return std::nullopt;
    }

    const SkillStepHint& step = state.active_hints->steps[static_cast<size_t>(state.pending_step_index)];
    if (step.action != "tap" && step.action != "open") {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=unsupported_action action={}", step.action);
        return std::nullopt;
    }

    const ObservationSnapshot snapshot = parse_observation_snapshot(observation_content);
    if (snapshot.snapshot_id.empty()) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=missing_snapshot");
        return std::nullopt;
    }
    if (!matches_step_page(snapshot, step)) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=page_not_matched page={} activity={}",
                step.page, snapshot.activity);
        return std::nullopt;
    }
    if (step.target.empty() && step.aliases.empty()) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=missing_target");
        return std::nullopt;
    }

    const ObservationFingerprint snapshot_fingerprint = build_observation_fingerprint(snapshot);
    const std::string attempt_cache_key = build_navigation_attempt_cache_key(
            state.pending_step_index, snapshot_fingerprint.value);
    const auto attempt_it = state.navigation_attempt_cache.find(attempt_cache_key);
    if (attempt_it != state.navigation_attempt_cache.end()) {
        ICRAW_LOG_INFO(
                "[AgentLoop][navigation_attempt_cache_hit] step_index={} attempt_count={} target={} candidate={} tap_bounds={}",
                state.pending_step_index,
                attempt_it->second.attempt_count,
                truncate_runtime_text(attempt_it->second.step_target, 80),
                truncate_runtime_text(attempt_it->second.selected_candidate, 80),
                truncate_runtime_text(attempt_it->second.tap_bounds, 80));
    }
    if (attempt_it != state.navigation_attempt_cache.end()
            && attempt_it->second.attempt_count > 0
            && state.navigation_checkpoint.stagnant_rounds > 0) {
        ICRAW_LOG_INFO(
                "[AgentLoop][navigation_no_progress_detected] reason=repeat_fingerprint_after_click step_index={} attempt_count={} fingerprint={} candidate={} tap_bounds={}",
                state.pending_step_index,
                attempt_it->second.attempt_count,
                truncate_runtime_text(snapshot_fingerprint.value, 160),
                truncate_runtime_text(attempt_it->second.selected_candidate, 80),
                truncate_runtime_text(attempt_it->second.tap_bounds, 80));
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_fallback_reason] reason=no_progress_cached canonical_count=0 target={}",
                step.target);
        return std::nullopt;
    }

    std::vector<std::string> match_terms;
    if (!step.target.empty()) {
        match_terms.push_back(step.target);
    }
    for (const auto& alias : step.aliases) {
        if (!alias.empty()) {
            match_terms.push_back(alias);
        }
    }

    std::vector<ObservationCandidate> primary_matched_candidates;
    std::vector<ObservationCandidate> alias_matched_candidates;
    std::vector<ObservationCandidate> high_confidence_candidates;
    for (const auto& candidate : snapshot.actionable_candidates) {
        if (candidate.bounds.empty()) {
            continue;
        }
        const bool high_confidence = candidate.source == "vision_only"
                ? candidate.score >= FAST_EXECUTE_VISION_ONLY_SCORE_MIN
                : candidate.score >= FAST_EXECUTE_NATIVE_SCORE_MIN;
        if (!high_confidence) {
            continue;
        }
        high_confidence_candidates.push_back(candidate);

        const int primary_strength = candidate_term_match_strength(candidate, step.target, true);
        if (primary_strength > 0) {
            primary_matched_candidates.push_back(candidate);
            continue;
        }

        int alias_strength = 0;
        for (const auto& alias : step.aliases) {
            alias_strength = std::max(alias_strength, candidate_term_match_strength(candidate, alias, false));
        }
        if (alias_strength > 0) {
            alias_matched_candidates.push_back(candidate);
        }
    }

    std::vector<ObservationCandidate> matched_candidates =
            !primary_matched_candidates.empty() ? primary_matched_candidates : alias_matched_candidates;

    if (step_prefers_corner_entry(step)) {
        const size_t filtered_high_confidence =
                filter_corner_entry_midpage_candidates(high_confidence_candidates, snapshot);
        if (filtered_high_confidence > 0) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_corner_midpage_filtered] stage=high_confidence removed_count={} remaining_count={}",
                    filtered_high_confidence,
                    high_confidence_candidates.size());
        }
        const size_t filtered_matched =
                filter_corner_entry_midpage_candidates(matched_candidates, snapshot);
        if (filtered_matched > 0) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_corner_midpage_filtered] stage=matched removed_count={} remaining_count={}",
                    filtered_matched,
                    matched_candidates.size());
        }
    }

    auto canonicalize_candidates = [&](std::vector<ObservationCandidate>& candidates,
                                       const char* stage) {
        if (candidates.empty()) {
            return std::vector<CanonicalCandidate>{};
        }
        const auto canonical_candidates = build_canonical_candidates(candidates);
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_count] stage={} raw_count={} canonical_count={} target={} activity={}",
                stage,
                candidates.size(),
                canonical_candidates.size(),
                step.target,
                snapshot.activity);
        if (canonical_candidates.size() < candidates.size()) {
            candidates.clear();
            for (const auto& candidate : canonical_candidates) {
                candidates.push_back(candidate.aggregate);
            }
        }
        return canonical_candidates;
    };

    (void)canonicalize_candidates(high_confidence_candidates, "high_confidence");
    (void)canonicalize_candidates(matched_candidates, "matched");

    ICRAW_LOG_INFO(
            "[AgentLoop][fast_execute_evaluated] target={} aliases={} activity={} source={} candidate_count={} high_confidence_count={} primary_matches={} alias_matches={} warning_conflicts={}",
            step.target,
            append_unique_strings(build_string_array_json(step.aliases)).dump(),
            snapshot.activity,
            snapshot.source,
            snapshot.actionable_candidates.size(),
            high_confidence_candidates.size(),
            primary_matched_candidates.size(),
            alias_matched_candidates.size(),
            snapshot.warning_conflict_count);

    if (matched_candidates.empty() && step_prefers_corner_entry(step)) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_region_coordinate_recovery_attempt] target={} snapshot_id={} screen_width={} screen_height={} high_confidence_count={}",
                step.target,
                snapshot.snapshot_id,
                snapshot.screen_width,
                snapshot.screen_height,
                high_confidence_candidates.size());
        auto recovery_call = build_corner_region_recovery_tool_call(step, snapshot);
        if (recovery_call.has_value()) {
            return recovery_call;
        }
    }

    if (matched_candidates.empty() && step_prefers_corner_entry(step)) {
        std::vector<ObservationCandidate> preferred_region_high_confidence_candidates;
        for (const auto& candidate : high_confidence_candidates) {
            if (candidate_in_preferred_entry_region(candidate, step, snapshot)
                    && candidate_in_tight_corner_entry_region(candidate, snapshot)
                    && !candidate_is_corner_entry_noise(candidate)) {
                preferred_region_high_confidence_candidates.push_back(candidate);
            }
        }
        if (preferred_region_high_confidence_candidates.size() == 1) {
            const auto& only_candidate = preferred_region_high_confidence_candidates.front();
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_region_fallback] reason=single_preferred_region_candidate target={} candidate={} source={} score={}",
                    step.target,
                    only_candidate.label,
                    only_candidate.source,
                    only_candidate.score);
            matched_candidates.push_back(only_candidate);
        } else if (preferred_region_high_confidence_candidates.size() > 1) {
            matched_candidates = preferred_region_high_confidence_candidates;
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_region_fallback] reason=multiple_preferred_region_candidates target={} count={}",
                    step.target,
                    matched_candidates.size());
        }
    }

    if (matched_candidates.empty() && step_prefers_corner_entry(step)) {
        std::vector<ObservationCandidate> semantic_entry_candidates;
        for (const auto& candidate : high_confidence_candidates) {
            if (candidate_is_corner_entry_noise(candidate)) {
                continue;
            }
            if (candidate_has_corner_entry_semantics(candidate)
                    || (candidate_container_role_is(candidate, "header")
                        && candidate_in_preferred_entry_region(candidate, step, snapshot))) {
                semantic_entry_candidates.push_back(candidate);
            }
        }
        if (semantic_entry_candidates.size() > 1) {
            std::vector<ObservationCandidate> profile_entry_candidates;
            for (const auto& candidate : semantic_entry_candidates) {
                if (candidate_anchor_type_is(candidate, "profile_entry")) {
                    profile_entry_candidates.push_back(candidate);
                }
            }
            if (!profile_entry_candidates.empty()
                    && profile_entry_candidates.size() < semantic_entry_candidates.size()) {
                semantic_entry_candidates = std::move(profile_entry_candidates);
            }
        }
        if (semantic_entry_candidates.size() > 1) {
            std::vector<ObservationCandidate> preferred_region_candidates;
            for (const auto& candidate : semantic_entry_candidates) {
                if (candidate_in_preferred_entry_region(candidate, step, snapshot)
                        && candidate_in_tight_corner_entry_region(candidate, snapshot)) {
                    preferred_region_candidates.push_back(candidate);
                }
            }
            if (!preferred_region_candidates.empty()
                    && preferred_region_candidates.size() < semantic_entry_candidates.size()) {
                semantic_entry_candidates = std::move(preferred_region_candidates);
            }
        }
        if (semantic_entry_candidates.size() == 1) {
            const auto& only_candidate = semantic_entry_candidates.front();
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_semantic_fallback] reason=single_entry_semantic_candidate target={} candidate={} source={} score={}",
                    step.target,
                    only_candidate.label,
                    only_candidate.source,
                    only_candidate.score);
            matched_candidates.push_back(only_candidate);
        } else if (!semantic_entry_candidates.empty()) {
            matched_candidates = semantic_entry_candidates;
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_semantic_fallback] reason=multiple_entry_semantic_candidates target={} count={}",
                    step.target,
                    matched_candidates.size());
        }
    }

    if (matched_candidates.empty() && high_confidence_candidates.size() == 1) {
        const auto& only_candidate = high_confidence_candidates.front();
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_alias_fallback] reason=single_high_confidence_candidate target={} aliases={} candidate={} source={} score={}",
                step.target,
                append_unique_strings(build_string_array_json(step.aliases)).dump(),
                only_candidate.label,
                only_candidate.source,
                only_candidate.score);
        matched_candidates.push_back(only_candidate);
    }

    if (matched_candidates.size() > 1) {
        if (step_prefers_corner_entry(step)) {
            std::vector<ObservationCandidate> tight_corner_candidates;
            for (const auto& candidate : matched_candidates) {
                if (candidate_in_tight_corner_entry_region(candidate, snapshot)) {
                    tight_corner_candidates.push_back(candidate);
                }
            }
            if (!tight_corner_candidates.empty()
                    && tight_corner_candidates.size() < matched_candidates.size()) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_tight_corner_filtered] target={} original_count={} filtered_count={}",
                        step.target,
                        matched_candidates.size(),
                        tight_corner_candidates.size());
                matched_candidates = std::move(tight_corner_candidates);
            }
        }
    }

    if (!matched_candidates.empty() && step_prefers_corner_entry(step)) {
        std::vector<ObservationCandidate> strict_corner_candidates;
        for (const auto& candidate : matched_candidates) {
            if (candidate_in_tight_corner_entry_region(candidate, snapshot)) {
                strict_corner_candidates.push_back(candidate);
            }
        }
        if (strict_corner_candidates.empty()) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_fallback] reason=corner_candidate_out_of_region count={} target={}",
                    matched_candidates.size(),
                    step.target);
            return std::nullopt;
        }
        if (strict_corner_candidates.size() < matched_candidates.size()) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_corner_guard_filtered] target={} original_count={} filtered_count={}",
                    step.target,
                    matched_candidates.size(),
                    strict_corner_candidates.size());
            matched_candidates = std::move(strict_corner_candidates);
        }
    }

    if (matched_candidates.size() > 1) {
        if (step_prefers_corner_entry(step)) {
            std::vector<ObservationCandidate> profile_entry_candidates;
            for (const auto& candidate : matched_candidates) {
                if (candidate_anchor_type_is(candidate, "profile_entry")) {
                    profile_entry_candidates.push_back(candidate);
                }
            }
            if (!profile_entry_candidates.empty()
                    && profile_entry_candidates.size() < matched_candidates.size()) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_profile_entry_filtered] target={} original_count={} filtered_count={}",
                        step.target,
                        matched_candidates.size(),
                        profile_entry_candidates.size());
                matched_candidates = std::move(profile_entry_candidates);
            }
        }
    }

    if (matched_candidates.size() > 1) {
        if (step_prefers_corner_entry(step)) {
            std::vector<ObservationCandidate> preferred_region_candidates;
            for (const auto& candidate : matched_candidates) {
                if (candidate_in_preferred_entry_region(candidate, step, snapshot)) {
                    preferred_region_candidates.push_back(candidate);
                }
            }
            if (preferred_region_candidates.size() == 1) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_region_disambiguated] target={} winner={} source={} score={}",
                        step.target,
                        preferred_region_candidates.front().label,
                        preferred_region_candidates.front().source,
                        preferred_region_candidates.front().score);
                matched_candidates = preferred_region_candidates;
            } else if (!preferred_region_candidates.empty()
                    && preferred_region_candidates.size() < matched_candidates.size()) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_region_filtered] target={} original_count={} filtered_count={}",
                        step.target,
                        matched_candidates.size(),
                        preferred_region_candidates.size());
                matched_candidates = preferred_region_candidates;
            }
        }
    }

    if (matched_candidates.size() > 1) {
        std::vector<ObservationCandidate> exact_label_candidates;
        for (const auto& candidate : matched_candidates) {
            if (candidate_label_matches_step_target_exactly(candidate, step)) {
                exact_label_candidates.push_back(candidate);
            }
        }
        if (!step_prefers_corner_entry(step) && exact_label_candidates.size() == 1) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][fast_execute_exact_label_disambiguated] target={} winner={} original_count={}",
                    step.target,
                    exact_label_candidates.front().label,
                    matched_candidates.size());
            matched_candidates = std::move(exact_label_candidates);
        }
    }

    if (matched_candidates.size() > 1) {
        std::sort(matched_candidates.begin(), matched_candidates.end(),
                [&](const ObservationCandidate& left, const ObservationCandidate& right) {
                    return candidate_disambiguation_score(left, step, snapshot)
                            > candidate_disambiguation_score(right, step, snapshot);
                });
        if (matched_candidates.size() >= 2) {
            const double top_score = candidate_disambiguation_score(matched_candidates[0], step, snapshot);
            const double second_score = candidate_disambiguation_score(matched_candidates[1], step, snapshot);
            const double min_gap = step_prefers_corner_entry(step) ? 8.0 : 16.0;
            const bool overlapping_duplicates = candidates_look_like_overlapping_duplicates(
                    matched_candidates[0], matched_candidates[1]);
            const bool same_target_label_duplicates = candidates_are_overlapping_same_target_label(
                    matched_candidates[0], matched_candidates[1], step);
            const bool top_more_actionable = candidate_actionability_rank(matched_candidates[0])
                    > candidate_actionability_rank(matched_candidates[1]);
            const bool top_has_stronger_entry_semantics =
                    step_prefers_corner_entry(step)
                    && candidate_has_corner_entry_semantics(matched_candidates[0])
                    && !candidate_has_corner_entry_semantics(matched_candidates[1]);
            if (top_score >= second_score + min_gap) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_disambiguated] target={} winner={} winner_score={} runner_up={} runner_up_score={}",
                        step.target,
                        matched_candidates[0].label,
                        top_score,
                        matched_candidates[1].label,
                        second_score);
                matched_candidates.resize(1);
            } else if (same_target_label_duplicates) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_same_label_duplicate_disambiguated] target={} winner={} winner_score={} runner_up={} runner_up_score={} top_rank={} runner_up_rank={}",
                        step.target,
                        matched_candidates[0].label,
                        top_score,
                        matched_candidates[1].label,
                        second_score,
                        candidate_actionability_rank(matched_candidates[0]),
                        candidate_actionability_rank(matched_candidates[1]));
                matched_candidates.resize(1);
            } else if (overlapping_duplicates
                    && (top_more_actionable
                        || top_has_stronger_entry_semantics
                        || top_score >= second_score + 4.0)) {
                ICRAW_LOG_INFO(
                        "[AgentLoop][fast_execute_overlap_disambiguated] target={} winner={} winner_score={} runner_up={} runner_up_score={} top_rank={} runner_up_rank={}",
                        step.target,
                        matched_candidates[0].label,
                        top_score,
                        matched_candidates[1].label,
                        second_score,
                        candidate_actionability_rank(matched_candidates[0]),
                        candidate_actionability_rank(matched_candidates[1]));
                matched_candidates.resize(1);
            }
        }
    }

    if (matched_candidates.size() != 1) {
        if (matched_candidates.empty() && step_prefers_corner_entry(step)) {
            auto recovery_call = build_corner_region_recovery_tool_call(step, snapshot);
            if (recovery_call.has_value()) {
                return recovery_call;
            }
        }
        const auto fallback_canonical = build_canonical_candidates(matched_candidates);
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_fallback_reason] reason=ambiguous_candidate canonical_count={} target={} summary={}",
                fallback_canonical.size(),
                step.target,
                truncate_runtime_text(
                        build_canonical_candidate_summary(fallback_canonical, CANONICAL_CANDIDATE_SUMMARY_COUNT),
                        220));
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=ambiguous_candidate count={} target={} aliases={}",
                matched_candidates.size(),
                step.target,
                append_unique_strings(build_string_array_json(step.aliases)).dump());
        return std::nullopt;
    }
    std::string conflict_reason;
    if (should_block_fast_execute_on_conflicts(snapshot, step, matched_candidates[0], conflict_reason)) {
        ICRAW_LOG_INFO(
                "[AgentLoop][fast_execute_fallback] reason={} warning_count={} target={} candidate={}",
                conflict_reason,
                snapshot.warning_conflict_count,
                step.target,
                matched_candidates[0].label);
        return std::nullopt;
    }

    int center_x = 0;
    int center_y = 0;
    if (!parse_bounds_center(matched_candidates[0].bounds, center_x, center_y)) {
        ICRAW_LOG_INFO("[AgentLoop][fast_execute_fallback] reason=invalid_bounds bounds={}",
                matched_candidates[0].bounds);
        return std::nullopt;
    }

    ToolCall tool_call;
    tool_call.id = generate_tool_id();
    tool_call.name = "android_gesture_tool";
    tool_call.arguments = nlohmann::json::object({
            {"action", "tap"},
            {"x", center_x},
            {"y", center_y},
            {"observation", {
                    {"snapshotId", snapshot.snapshot_id},
                    {"targetDescriptor", matched_candidates[0].label.empty() ? step.target : matched_candidates[0].label},
                    {"referencedBounds", matched_candidates[0].bounds}
            }}
    });
    const auto selected_canonical = build_canonical_candidates(matched_candidates);
    if (!selected_canonical.empty()) {
        const auto& selected = selected_canonical.front();
        ICRAW_LOG_INFO(
                "[AgentLoop][canonical_candidate_selected] target={} display_label={} tap_bounds={} source_set={} actionability_rank={} member_count={} risk_flags={}",
                step.target,
                truncate_runtime_text(selected.display_label, 80),
                truncate_runtime_text(selected.tap_bounds, 80),
                truncate_runtime_text(selected.source_set, 80),
                selected.actionability_rank,
                selected.member_count,
                truncate_runtime_text(selected.risk_flags, 80));
    }
    ICRAW_LOG_INFO("[AgentLoop][fast_execute_hit] action=tap target={} source={} score={} page={}",
            step.target,
            matched_candidates[0].source,
            matched_candidates[0].score,
            snapshot.activity);
    return tool_call;
}

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
        const bool is_route_phase = state.phase == "discovery" && state.route_ready && !state.goal_reached;
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
    for (const auto& tool_call : tool_calls) {
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
            response << u8"当前已进入页面：" << truncate_runtime_text(page_name, 80) << "。";
        }
        if (!summary.empty()) {
            response << u8"页面概况：" << truncate_runtime_text(summary, 140) << "。";
        }
        if (!visible_items.empty()) {
            response << u8"当前可见内容包括：";
            for (size_t i = 0; i < visible_items.size() && i < 6; ++i) {
                if (i > 0) {
                    response << u8"、";
                }
                response << truncate_runtime_text(visible_items[i], 24);
            }
            response << u8"。";
        }
        if (!key_actions.empty()) {
            response << u8"可执行操作包括：";
            for (size_t i = 0; i < key_actions.size() && i < 4; ++i) {
                if (i > 0) {
                    response << u8"、";
                }
                response << truncate_runtime_text(key_actions[i], 24);
            }
            response << u8"。";
        }
        return truncate_runtime_text(response.str(), 320);
    } catch (...) {
        return "";
    }
}

}  // namespace

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

std::vector<Message> AgentLoop::process_message(const std::string& message,
                                                  const std::vector<Message>& history,
                                                  const std::string& system_prompt,
                                                  const std::vector<SkillMetadata>& selected_skills) {
    std::vector<Message> new_messages;
    stop_requested_ = false;
    
    // Build request
    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = agent_config_.temperature;
    request.max_tokens = agent_config_.max_tokens;
    request.enable_thinking = agent_config_.enable_thinking;
    request.stream = false;
    
    // Add system message
    request.messages.emplace_back("system", system_prompt);
    
    // Add history
    for (const auto& msg : history) {
        request.messages.push_back(msg);
    }
    
    // Add user message
    request.messages.emplace_back("user", message);

    auto tool_schemas = tool_registry_->get_tool_schemas();
    ExecutionState execution_state = initialize_execution_state(message, selected_skills);
    maybe_resolve_route_with_llm(execution_state, agent_config_, llm_provider_.get(), message, selected_skills);
    rebuild_tools_for_phase(tool_schemas, execution_state, request);
    if (execution_state.route_ready) {
        ICRAW_LOG_INFO(
                "[AgentLoop][route_resolved] task_type={} selected_skill={} navigation_goal={} readout_goal={} stop_requires_readout={}",
                execution_state.intent_route.task_type,
                execution_state.intent_route.selected_skill,
                execution_state.intent_route.navigation_goal,
                execution_state.intent_route.readout_goal,
                execution_state.intent_route.stop_condition.requires_readout);
    }
    
    // Agent loop
    int iteration = 0;
    auto loop_start_time = std::chrono::steady_clock::now();
    ICRAW_LOG_INFO("[AgentLoop][loop_start] mode=non_stream max_iterations={}", max_iterations_);
    while (iteration < max_iterations_ && !stop_requested_) {
        bool deterministic_navigation_executed = false;
        if (execution_state.mode == "planned_fast_execute"
                && execution_state.route_ready
                && !execution_state.goal_reached) {
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

            if (execution_state.navigation_checkpoint.stagnant_rounds >= 2) {
                execution_state.latest_escalation.reason = "no_progress";
                execution_state.latest_escalation.detail =
                        "stagnant_rounds=" + std::to_string(execution_state.navigation_checkpoint.stagnant_rounds);
                ICRAW_LOG_INFO(
                        "[AgentLoop][navigation_escalation] reason={} detail={}",
                        execution_state.latest_escalation.reason,
                        execution_state.latest_escalation.detail);
            } else if (need_observation) {
                if (stale_pending_step_observation) {
                    ICRAW_LOG_INFO(
                            "[AgentLoop][navigation_observe_refresh] mode=non_stream reason=pending_target_changed last_hint={} pending_step={}",
                            truncate_runtime_text(execution_state.last_observation_target_hint, 100),
                            summarize_pending_step_json(execution_state.pending_step));
                }
                maybe_wait_before_confirmation_retry(execution_state);
                ToolCall observation_call = build_navigation_observation_call(execution_state);
                Message observation_assistant = build_assistant_tool_call_message(observation_call);
                new_messages.push_back(observation_assistant);
                request.messages.push_back(observation_assistant);

                std::vector<ToolCall> observation_calls{observation_call};
                auto tool_phase_start = std::chrono::steady_clock::now();
                auto observation_calls_for_execution = enrich_tool_calls_for_execution(
                        observation_calls, execution_state, request.messages);
                auto tool_results = handle_tool_calls(observation_calls_for_execution);
                auto tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                        std::chrono::steady_clock::now() - tool_phase_start).count();
                ICRAW_LOG_INFO(
                        "[AgentLoop][tool_phase_duration_ms] mode=non_stream iteration={} phase=navigation_observe tool_call_count={} duration_ms={}",
                        iteration,
                        observation_calls_for_execution.size(),
                        tool_phase_duration_ms);

                for (size_t i = 0; i < tool_results.size(); ++i) {
                    const auto& result = tool_results[i];
                    Message tool_msg;
                    tool_msg.role = "tool";
                    tool_msg.tool_call_id = result.tool_use_id;
                    tool_msg.content.push_back(ContentBlock::make_tool_result(result.tool_use_id, result.content));
                    new_messages.push_back(tool_msg);
                    request.messages.push_back(tool_msg);
                    update_execution_state_with_tool_result(
                            execution_state,
                            &observation_calls_for_execution[i],
                            observation_calls_for_execution[i].name,
                            result.content);
                }
                deterministic_navigation_executed = !tool_results.empty();
            } else {
                const auto latest_observation = find_latest_tool_result_content(
                        request.messages, "android_view_context_tool");
                if (latest_observation.has_value()) {
                    auto fast_tool_call = maybe_build_fast_execute_tool_call(
                            execution_state, latest_observation.value());
                    if (fast_tool_call.has_value()) {
                        Message fast_assistant = build_assistant_tool_call_message(*fast_tool_call);
                        new_messages.push_back(fast_assistant);
                        request.messages.push_back(fast_assistant);

                        std::vector<ToolCall> fast_calls{*fast_tool_call};
                        auto fast_phase_start = std::chrono::steady_clock::now();
                        auto fast_results = handle_tool_calls(fast_calls);
                        auto fast_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                                std::chrono::steady_clock::now() - fast_phase_start).count();
                        ICRAW_LOG_INFO(
                                "[AgentLoop][tool_phase_duration_ms] mode=non_stream iteration={} phase=navigation_fast_execute tool_call_count={} duration_ms={}",
                                iteration,
                                fast_calls.size(),
                                fast_phase_duration_ms);
                        for (const auto& fast_result : fast_results) {
                            Message fast_tool_msg;
                            fast_tool_msg.role = "tool";
                            fast_tool_msg.tool_call_id = fast_result.tool_use_id;
                            fast_tool_msg.content.push_back(ContentBlock::make_tool_result(
                                    fast_result.tool_use_id, fast_result.content));
                            new_messages.push_back(fast_tool_msg);
                            request.messages.push_back(fast_tool_msg);
                            update_execution_state_with_tool_result(
                                    execution_state, &(*fast_tool_call), fast_tool_call->name, fast_result.content);
                        }
                        deterministic_navigation_executed = !fast_results.empty();
                    } else {
                        execution_state.latest_escalation.reason = "ambiguous_or_low_confidence";
                        execution_state.latest_escalation.detail = "pending_step=" + execution_state.pending_step.dump();
                        ICRAW_LOG_INFO(
                                "[AgentLoop][navigation_escalation] reason={} detail={}",
                                execution_state.latest_escalation.reason,
                                truncate_runtime_text(execution_state.latest_escalation.detail, 160));
                    }
                }
            }

            if (execution_state.goal_reached && !execution_state.context_reset) {
                reset_messages_for_readout(request.messages, execution_state);
                execution_state.context_reset = true;
                ICRAW_LOG_INFO(
                        "[AgentLoop][goal_reached_context_reset] mode=non_stream phase={} current_page={}",
                        execution_state.phase,
                        execution_state.current_page);
            }
            if (deterministic_navigation_executed) {
                continue;
            }
        }

        auto iter_start_time = std::chrono::steady_clock::now();
        iteration++;
        ICRAW_LOG_DEBUG("[AgentLoop][iteration_start] mode=non_stream iteration={}", iteration);

        prune_context_messages_for_state(request.messages, execution_state);

        const bool has_navigation_escalation = !execution_state.latest_escalation.reason.empty();
        const bool use_compact_navigation_escalation_request =
                COMPACT_NAVIGATION_ESCALATION_ENABLED && has_navigation_escalation;
        if (has_navigation_escalation && !use_compact_navigation_escalation_request) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][navigation_escalation_compact_disabled] mode=non_stream iteration={} reason={}",
                    iteration,
                    execution_state.latest_escalation.reason);
        }
        ChatCompletionRequest effective_request = use_compact_navigation_escalation_request
                ? build_compact_navigation_escalation_chat_request(
                        request, message, tool_schemas, execution_state)
                : request;
        if (!use_compact_navigation_escalation_request) {
            rebuild_tools_for_phase(tool_schemas, execution_state, effective_request);
            inject_selected_skills_into_request(effective_request, selected_skills, iteration);
            inject_navigation_escalation_into_request(effective_request, execution_state);
            const std::string execution_state_prompt =
                    build_execution_state_prompt_v2(execution_state, iteration);
            if (!execution_state_prompt.empty()) {
                inject_execution_state_into_request(effective_request, execution_state_prompt);
                ICRAW_LOG_INFO("[AgentLoop][execution_state_injected] mode=non_stream iteration={} prompt_length={}",
                        iteration, execution_state_prompt.size());
            }
        } else {
            ICRAW_LOG_INFO("[AgentLoop][navigation_escalation_request_selected] mode=non_stream iteration={} reason={}",
                    iteration, execution_state.latest_escalation.reason);
        }
        ICRAW_LOG_INFO("[AgentLoop][execution_state_mode] mode={} phase={} iteration={}",
                execution_state.mode, execution_state.phase, iteration);

        const auto llm_request_profile = resolve_llm_request_profile(agent_config_, execution_state, effective_request);
        apply_llm_request_profile(effective_request, llm_request_profile);
        ICRAW_LOG_INFO("[AgentLoop][request_profile] mode=non_stream iteration={} profile={} max_tokens={} temperature={}",
                iteration,
                effective_request.request_profile,
                effective_request.max_tokens,
                effective_request.temperature);

        // Call LLM
        auto response = llm_provider_->chat_completion(effective_request);
        execution_state.latest_escalation = NavigationEscalation{};
        
        // Build assistant message
        Message assistant_msg;
        assistant_msg.role = "assistant";
        assistant_msg.content = build_response_blocks(response.content, response.reasoning_content);
        
        auto filtered_tool_calls = filter_tool_calls_for_request(
                response.tool_calls, effective_request, execution_state);

        // Add tool_calls as separate field (OpenAI format)
        for (const auto& tc : filtered_tool_calls) {
            std::string tool_id = tc.id.empty() ? generate_tool_id() : tc.id;
            ToolCallForMessage tc_msg;
            tc_msg.id = tool_id;
            tc_msg.type = "function";
            tc_msg.function_name = tc.name;
            tc_msg.function_arguments = tc.arguments.is_string() 
                ? tc.arguments.get<std::string>() 
                : tc.arguments.dump();
            assistant_msg.tool_calls.push_back(std::move(tc_msg));
        }
        
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
        
        // Execute tool calls
        auto tool_phase_start = std::chrono::steady_clock::now();
        auto tool_calls_for_execution = enrich_tool_calls_for_execution(
                filtered_tool_calls, execution_state, request.messages);
        auto tool_results = handle_tool_calls(tool_calls_for_execution);
        auto tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - tool_phase_start).count();
        ICRAW_LOG_INFO(
                "[AgentLoop][tool_phase_duration_ms] mode=non_stream iteration={} phase=primary tool_call_count={} duration_ms={}",
                iteration,
                tool_calls_for_execution.size(),
                tool_phase_duration_ms);
        
        // Add tool results
        for (size_t i = 0; i < tool_results.size(); ++i) {
            const auto& result = tool_results[i];
            Message tool_msg;
            tool_msg.role = "tool";
            tool_msg.tool_call_id = result.tool_use_id;
            tool_msg.content.push_back(ContentBlock::make_tool_result(result.tool_use_id, result.content));
            new_messages.push_back(tool_msg);
            request.messages.push_back(tool_msg);
            const std::string tool_name = i < tool_calls_for_execution.size()
                    ? tool_calls_for_execution[i].name
                    : "tool";
            const ToolCall* executed_tool_call = i < tool_calls_for_execution.size()
                    ? &tool_calls_for_execution[i]
                    : nullptr;
            update_execution_state_with_tool_result(
                    execution_state, executed_tool_call, tool_name, result.content);
        }

        if (execution_state.goal_reached && !execution_state.context_reset) {
                    reset_messages_for_readout(request.messages, execution_state);
            execution_state.context_reset = true;
            ICRAW_LOG_INFO("[AgentLoop][goal_reached_context_reset] mode=non_stream phase={} current_page={}",
                    execution_state.phase, execution_state.current_page);
        }

        if (!tool_results.empty()) {
            const std::string last_tool_name = tool_calls_for_execution.back().name;
            if (last_tool_name == "android_view_context_tool") {
                auto fast_tool_call = maybe_build_fast_execute_tool_call(
                        execution_state, tool_results.back().content);
                if (fast_tool_call.has_value()) {
                    Message fast_assistant_msg;
                    fast_assistant_msg.role = "assistant";
                    ToolCallForMessage tc_msg;
                    tc_msg.id = fast_tool_call->id;
                    tc_msg.type = "function";
                    tc_msg.function_name = fast_tool_call->name;
                    tc_msg.function_arguments = fast_tool_call->arguments.dump();
                    fast_assistant_msg.tool_calls.push_back(std::move(tc_msg));
                    new_messages.push_back(fast_assistant_msg);
                    request.messages.push_back(fast_assistant_msg);

                    std::vector<ToolCall> fast_calls{*fast_tool_call};
                    auto fast_tool_phase_start = std::chrono::steady_clock::now();
                    auto fast_results = handle_tool_calls(fast_calls);
                    auto fast_tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::steady_clock::now() - fast_tool_phase_start).count();
                    ICRAW_LOG_INFO(
                            "[AgentLoop][tool_phase_duration_ms] mode=non_stream iteration={} phase=fast_execute tool_call_count={} duration_ms={}",
                            iteration,
                            fast_calls.size(),
                            fast_tool_phase_duration_ms);
                    for (const auto& fast_result : fast_results) {
                        Message fast_tool_msg;
                        fast_tool_msg.role = "tool";
                        fast_tool_msg.tool_call_id = fast_result.tool_use_id;
                        fast_tool_msg.content.push_back(ContentBlock::make_tool_result(
                                fast_result.tool_use_id, fast_result.content));
                        new_messages.push_back(fast_tool_msg);
                        request.messages.push_back(fast_tool_msg);
                        update_execution_state_with_tool_result(
                                execution_state, &(*fast_tool_call), fast_tool_call->name, fast_result.content);
                    }
                }
            }
        }
    }

    auto loop_end_time = std::chrono::steady_clock::now();
    auto loop_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(loop_end_time - loop_start_time).count();
    ICRAW_LOG_INFO("[AgentLoop][loop_complete] mode=non_stream duration_ms={} iteration_count={}",
            loop_duration_ms, iteration);

    return new_messages;
}

std::vector<Message> AgentLoop::process_message_stream(const std::string& message,
                                                        const std::vector<Message>& history,
                                                        const std::string& system_prompt,
                                                        AgentEventCallback callback,
                                                        const std::vector<SkillMetadata>& selected_skills) {
    std::vector<Message> new_messages;
    stop_requested_ = false;
    std::string loop_exit_reason;
    
    // Build request
    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = agent_config_.temperature;
    request.max_tokens = agent_config_.max_tokens;
    request.enable_thinking = agent_config_.enable_thinking;
    request.stream = true;
    
    // Add system message
    request.messages.emplace_back("system", system_prompt);
    
    // Add history
    for (const auto& msg : history) {
        request.messages.push_back(msg);
    }
    
    // Add user message
    request.messages.emplace_back("user", message);

    auto tool_schemas = tool_registry_->get_tool_schemas();
    ExecutionState execution_state = initialize_execution_state(message, selected_skills);
    maybe_resolve_route_with_llm(execution_state, agent_config_, llm_provider_.get(), message, selected_skills);
    rebuild_tools_for_phase(tool_schemas, execution_state, request);
    if (execution_state.route_ready) {
        ICRAW_LOG_INFO(
                "[AgentLoop][route_resolved] mode=stream task_type={} selected_skill={} navigation_goal={} readout_goal={} stop_requires_readout={}",
                execution_state.intent_route.task_type,
                execution_state.intent_route.selected_skill,
                execution_state.intent_route.navigation_goal,
                execution_state.intent_route.readout_goal,
                execution_state.intent_route.stop_condition.requires_readout);
    }
    
    // Agent loop
    int iteration = 0;
    auto loop_start_time = std::chrono::steady_clock::now();
    ICRAW_LOG_INFO("[AgentLoop][loop_start] mode=stream max_iterations={}", max_iterations_);
    while (iteration < max_iterations_ && !stop_requested_) {
        bool deterministic_navigation_executed = false;
        if (execution_state.mode == "planned_fast_execute"
                && execution_state.route_ready
                && !execution_state.goal_reached) {
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

            if (execution_state.navigation_checkpoint.stagnant_rounds >= 2) {
                execution_state.latest_escalation.reason = "no_progress";
                execution_state.latest_escalation.detail =
                        "stagnant_rounds=" + std::to_string(execution_state.navigation_checkpoint.stagnant_rounds);
                ICRAW_LOG_INFO(
                        "[AgentLoop][navigation_escalation] mode=stream reason={} detail={}",
                        execution_state.latest_escalation.reason,
                        execution_state.latest_escalation.detail);
            } else if (need_observation) {
                if (stale_pending_step_observation) {
                    ICRAW_LOG_INFO(
                            "[AgentLoop][navigation_observe_refresh] mode=stream reason=pending_target_changed last_hint={} pending_step={}",
                            truncate_runtime_text(execution_state.last_observation_target_hint, 100),
                            summarize_pending_step_json(execution_state.pending_step));
                }
                maybe_wait_before_confirmation_retry(execution_state);
                ToolCall observation_call = build_navigation_observation_call(execution_state);
                Message observation_assistant = build_assistant_tool_call_message(observation_call);
                new_messages.push_back(observation_assistant);
                request.messages.push_back(observation_assistant);

                AgentEvent tool_use_event;
                tool_use_event.type = "tool_use";
                tool_use_event.data["id"] = observation_call.id;
                tool_use_event.data["name"] = observation_call.name;
                tool_use_event.data["input"] = observation_call.arguments;
                callback(tool_use_event);

                std::vector<ToolCall> observation_calls{observation_call};
                auto tool_phase_start = std::chrono::steady_clock::now();
                auto observation_calls_for_execution = enrich_tool_calls_for_execution(
                        observation_calls, execution_state, request.messages);
                auto tool_results = handle_tool_calls(observation_calls_for_execution);
                auto tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                        std::chrono::steady_clock::now() - tool_phase_start).count();
                ICRAW_LOG_INFO(
                        "[AgentLoop][tool_phase_duration_ms] mode=stream iteration={} phase=navigation_observe tool_call_count={} duration_ms={}",
                        iteration,
                        observation_calls_for_execution.size(),
                        tool_phase_duration_ms);

                for (size_t i = 0; i < tool_results.size(); ++i) {
                    const auto& result = tool_results[i];
                    Message tool_msg;
                    tool_msg.role = "tool";
                    tool_msg.tool_call_id = result.tool_use_id;
                    tool_msg.content.push_back(ContentBlock::make_tool_result(result.tool_use_id, result.content));
                    new_messages.push_back(tool_msg);
                    request.messages.push_back(tool_msg);
                    update_execution_state_with_tool_result(
                            execution_state,
                            &observation_calls_for_execution[i],
                            observation_calls_for_execution[i].name,
                            result.content);
                    AgentEvent tool_result_event;
                    tool_result_event.type = "tool_result";
                    tool_result_event.data["tool_use_id"] = result.tool_use_id;
                    tool_result_event.data["content"] = result.content;
                    callback(tool_result_event);
                }
                deterministic_navigation_executed = !tool_results.empty();
            } else {
                const auto latest_observation = find_latest_tool_result_content(
                        request.messages, "android_view_context_tool");
                if (latest_observation.has_value()) {
                    auto fast_tool_call = maybe_build_fast_execute_tool_call(
                            execution_state, latest_observation.value());
                    if (fast_tool_call.has_value()) {
                        Message fast_assistant = build_assistant_tool_call_message(*fast_tool_call);
                        new_messages.push_back(fast_assistant);
                        request.messages.push_back(fast_assistant);

                        AgentEvent fast_use_event;
                        fast_use_event.type = "tool_use";
                        fast_use_event.data["id"] = fast_tool_call->id;
                        fast_use_event.data["name"] = fast_tool_call->name;
                        fast_use_event.data["input"] = fast_tool_call->arguments;
                        callback(fast_use_event);

                        std::vector<ToolCall> fast_calls{*fast_tool_call};
                        auto fast_phase_start = std::chrono::steady_clock::now();
                        auto fast_results = handle_tool_calls(fast_calls);
                        auto fast_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                                std::chrono::steady_clock::now() - fast_phase_start).count();
                        ICRAW_LOG_INFO(
                                "[AgentLoop][tool_phase_duration_ms] mode=stream iteration={} phase=navigation_fast_execute tool_call_count={} duration_ms={}",
                                iteration,
                                fast_calls.size(),
                                fast_phase_duration_ms);
                        for (const auto& fast_result : fast_results) {
                            Message fast_tool_msg;
                            fast_tool_msg.role = "tool";
                            fast_tool_msg.tool_call_id = fast_result.tool_use_id;
                            fast_tool_msg.content.push_back(ContentBlock::make_tool_result(
                                    fast_result.tool_use_id, fast_result.content));
                            new_messages.push_back(fast_tool_msg);
                            request.messages.push_back(fast_tool_msg);
                            update_execution_state_with_tool_result(
                                    execution_state, &(*fast_tool_call), fast_tool_call->name, fast_result.content);
                            AgentEvent fast_result_event;
                            fast_result_event.type = "tool_result";
                            fast_result_event.data["tool_use_id"] = fast_result.tool_use_id;
                            fast_result_event.data["content"] = fast_result.content;
                            callback(fast_result_event);
                        }
                        deterministic_navigation_executed = !fast_results.empty();
                    } else {
                        execution_state.latest_escalation.reason = "ambiguous_or_low_confidence";
                        execution_state.latest_escalation.detail =
                                "pending_step=" + execution_state.pending_step.dump();
                        ICRAW_LOG_INFO(
                                "[AgentLoop][navigation_escalation] mode=stream reason={} detail={}",
                                execution_state.latest_escalation.reason,
                                truncate_runtime_text(execution_state.latest_escalation.detail, 160));
                    }
                }
            }

            if (execution_state.goal_reached && !execution_state.context_reset) {
                reset_messages_for_readout(request.messages, execution_state);
                execution_state.context_reset = true;
                ICRAW_LOG_INFO(
                        "[AgentLoop][goal_reached_context_reset] mode=stream phase={} current_page={}",
                        execution_state.phase,
                        execution_state.current_page);
            }
            if (deterministic_navigation_executed) {
                continue;
            }
        }

        iteration++;
        auto iter_start_time = std::chrono::steady_clock::now();
        ICRAW_LOG_DEBUG("[AgentLoop][iteration_start] mode=stream iteration={}", iteration);

        prune_context_messages_for_state(request.messages, execution_state);

        const bool has_navigation_escalation = !execution_state.latest_escalation.reason.empty();
        const bool use_compact_navigation_escalation_request =
                COMPACT_NAVIGATION_ESCALATION_ENABLED && has_navigation_escalation;
        if (has_navigation_escalation && !use_compact_navigation_escalation_request) {
            ICRAW_LOG_INFO(
                    "[AgentLoop][navigation_escalation_compact_disabled] mode=stream iteration={} reason={}",
                    iteration,
                    execution_state.latest_escalation.reason);
        }
        ChatCompletionRequest effective_request = use_compact_navigation_escalation_request
                ? build_compact_navigation_escalation_chat_request(
                        request, message, tool_schemas, execution_state)
                : request;
        if (!use_compact_navigation_escalation_request) {
            rebuild_tools_for_phase(tool_schemas, execution_state, effective_request);
            inject_selected_skills_into_request(effective_request, selected_skills, iteration);
            inject_navigation_escalation_into_request(effective_request, execution_state);
            const std::string execution_state_prompt =
                    build_execution_state_prompt_v2(execution_state, iteration);
            if (!execution_state_prompt.empty()) {
                inject_execution_state_into_request(effective_request, execution_state_prompt);
                ICRAW_LOG_INFO("[AgentLoop][execution_state_injected] mode=stream iteration={} prompt_length={}",
                        iteration, execution_state_prompt.size());
            }
        } else {
            ICRAW_LOG_INFO("[AgentLoop][navigation_escalation_request_selected] mode=stream iteration={} reason={}",
                    iteration, execution_state.latest_escalation.reason);
        }
        ICRAW_LOG_INFO("[AgentLoop][execution_state_mode] mode={} phase={} iteration={}",
                execution_state.mode, execution_state.phase, iteration);

        const auto llm_request_profile = resolve_llm_request_profile(agent_config_, execution_state, effective_request);
        apply_llm_request_profile(effective_request, llm_request_profile);
        ICRAW_LOG_INFO("[AgentLoop][request_profile] mode=stream iteration={} profile={} max_tokens={} temperature={}",
                iteration,
                effective_request.request_profile,
                effective_request.max_tokens,
                effective_request.temperature);

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
        
        // Stream LLM response
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

        // Add valid tool calls to assistant message (OpenAI format)
        for (const auto& tc : valid_tool_calls) {
            std::string tool_id = tc.id.empty() ? generate_tool_id() : tc.id;
            ToolCallForMessage tc_msg;
            tc_msg.id = tool_id;
            tc_msg.type = "function";
            tc_msg.function_name = tc.name;
            tc_msg.function_arguments = tc.arguments.is_string() 
                ? tc.arguments.get<std::string>() 
                : tc.arguments.dump();
            assistant_msg.tool_calls.push_back(std::move(tc_msg));
            
            // Emit tool_use event
            AgentEvent event;
            event.type = "tool_use";
            event.data["id"] = tool_id;
            event.data["name"] = tc.name;
            event.data["input"] = tc.arguments;
            callback(event);
        }

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
        
        // Execute valid tool calls
        ICRAW_LOG_INFO("[AgentLoop][tool_call_execute_start] tool_call_count={}", valid_tool_calls.size());
        auto tool_phase_start = std::chrono::steady_clock::now();
        auto tool_calls_for_execution = enrich_tool_calls_for_execution(
                valid_tool_calls, execution_state, request.messages);
        auto tool_results = handle_tool_calls(tool_calls_for_execution);
        auto tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - tool_phase_start).count();
        ICRAW_LOG_INFO(
                "[AgentLoop][tool_phase_duration_ms] mode=stream iteration={} phase=primary tool_call_count={} duration_ms={}",
                iteration,
                tool_calls_for_execution.size(),
                tool_phase_duration_ms);
        
        // Add tool results
        for (size_t i = 0; i < tool_results.size(); ++i) {
            const auto& result = tool_results[i];
            Message tool_msg;
            tool_msg.role = "tool";
            tool_msg.tool_call_id = result.tool_use_id;
            tool_msg.content.push_back(ContentBlock::make_tool_result(result.tool_use_id, result.content));
            new_messages.push_back(tool_msg);
            request.messages.push_back(tool_msg);
            const std::string tool_name = i < tool_calls_for_execution.size()
                    ? tool_calls_for_execution[i].name
                    : "tool";
            const ToolCall* executed_tool_call = i < tool_calls_for_execution.size()
                    ? &tool_calls_for_execution[i]
                    : nullptr;
            update_execution_state_with_tool_result(
                    execution_state, executed_tool_call, tool_name, result.content);
            
            // Debug: Log tool result
            // Parse result JSON to check for success/failure
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
            
            AgentEvent event;
            event.type = "tool_result";
            event.data["tool_use_id"] = result.tool_use_id;
            event.data["content"] = result.content;
            callback(event);
        }

        if (execution_state.goal_reached && !execution_state.context_reset) {
                    reset_messages_for_readout(request.messages, execution_state);
            execution_state.context_reset = true;
            ICRAW_LOG_INFO("[AgentLoop][goal_reached_context_reset] mode=stream phase={} current_page={}",
                    execution_state.phase, execution_state.current_page);
        }

        if (!tool_results.empty()) {
            const std::string last_tool_name = tool_calls_for_execution.back().name;
            if (last_tool_name == "android_view_context_tool") {
                auto fast_tool_call = maybe_build_fast_execute_tool_call(
                        execution_state, tool_results.back().content);
                if (fast_tool_call.has_value()) {
                    Message fast_assistant_msg;
                    fast_assistant_msg.role = "assistant";
                    ToolCallForMessage fast_tc_msg;
                    fast_tc_msg.id = fast_tool_call->id;
                    fast_tc_msg.type = "function";
                    fast_tc_msg.function_name = fast_tool_call->name;
                    fast_tc_msg.function_arguments = fast_tool_call->arguments.dump();
                    fast_assistant_msg.tool_calls.push_back(std::move(fast_tc_msg));
                    new_messages.push_back(fast_assistant_msg);
                    request.messages.push_back(fast_assistant_msg);

                    AgentEvent tool_use_event;
                    tool_use_event.type = "tool_use";
                    tool_use_event.data["id"] = fast_tool_call->id;
                    tool_use_event.data["name"] = fast_tool_call->name;
                    tool_use_event.data["input"] = fast_tool_call->arguments;
                    callback(tool_use_event);

                    std::vector<ToolCall> fast_calls{*fast_tool_call};
                    auto fast_tool_phase_start = std::chrono::steady_clock::now();
                    auto fast_results = handle_tool_calls(fast_calls);
                    auto fast_tool_phase_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::steady_clock::now() - fast_tool_phase_start).count();
                    ICRAW_LOG_INFO(
                            "[AgentLoop][tool_phase_duration_ms] mode=stream iteration={} phase=fast_execute tool_call_count={} duration_ms={}",
                            iteration,
                            fast_calls.size(),
                            fast_tool_phase_duration_ms);
                    for (const auto& fast_result : fast_results) {
                        Message fast_tool_msg;
                        fast_tool_msg.role = "tool";
                        fast_tool_msg.tool_call_id = fast_result.tool_use_id;
                        fast_tool_msg.content.push_back(ContentBlock::make_tool_result(
                                fast_result.tool_use_id, fast_result.content));
                        new_messages.push_back(fast_tool_msg);
                        request.messages.push_back(fast_tool_msg);
                        update_execution_state_with_tool_result(
                                execution_state, &(*fast_tool_call), fast_tool_call->name, fast_result.content);

                        AgentEvent fast_result_event;
                        fast_result_event.type = "tool_result";
                        fast_result_event.data["tool_use_id"] = fast_result.tool_use_id;
                        fast_result_event.data["content"] = fast_result.content;
                        callback(fast_result_event);
                    }
                }
            }
        }

        if (stop_requested_) {
            loop_exit_reason = "cancel";
            ICRAW_LOG_INFO("[AgentLoop][tool_phase_cancelled] iteration={} tool_result_count={}",
                    iteration, tool_results.size());

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

void AgentLoop::maybe_consolidate_memory(const std::vector<Message>& messages) {
    // Check if consolidation is needed
    int message_count = memory_manager_ ? memory_manager_->get_message_count() : 0;
    int threshold = agent_config_.consolidation_threshold;
    
    ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] stage=check message_count={} threshold={}",
            message_count, threshold);
    
    if (message_count > threshold) {
        // Check if previous consolidation is still running
        if (consolidation_future_.valid() && 
            consolidation_future_.wait_for(std::chrono::seconds(0)) != std::future_status::ready) {
            ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] stage=skip reason=previous_task_running");
            return;
        }
        
        ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_start] mode=async message_count={} threshold={}", 
            message_count, threshold);
        
        // Launch async consolidation - captures shared_ptrs to ensure lifetime
        auto memory_mgr = memory_manager_;
        auto llm_prov = llm_provider_;
        auto config = agent_config_;
        auto msgs_copy = messages;
        
        consolidation_future_ = std::async(std::launch::async, 
            [memory_mgr, llm_prov, config, msgs_copy]() {
                // Re-create consolidation logic inline for async execution
                if (!memory_mgr || !llm_prov) {
                    return false;
                }
                
                int keep_count = config.memory_window / 2;
                auto old_messages = memory_mgr->get_messages_for_consolidation(keep_count);
                
                if (old_messages.empty()) {
                    ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=async stage=no_messages");
                    return true;
                }
                
                ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=async stage=prepare message_count={}",
                        old_messages.size());
                
                // Format messages for consolidation
                std::string conversation;
                for (const auto& msg : old_messages) {
                    std::string tools_used;
                    if (msg.metadata.contains("tools_used")) {
                        auto tools = msg.metadata["tools_used"];
                        for (const auto& t : tools) {
                            if (!tools_used.empty()) tools_used += ", ";
                            tools_used += t.get<std::string>();
                        }
                        if (!tools_used.empty()) {
                            tools_used = " [tools: " + tools_used + "]";
                        }
                    }
                    conversation += "[" + msg.timestamp.substr(0, 16) + "] " 
                                + msg.role + tools_used + ": " 
                                + msg.content + "\n";
                }
                
                auto latest_summary = memory_mgr->get_latest_summary();
                std::string current_memory = latest_summary ? latest_summary->summary : "(empty)";
                
                std::string system_prompt = "You are a memory consolidation agent. Call the save_memory tool to save important information from the conversation.";
                std::string user_prompt = "Process this conversation and call the save_memory tool with your consolidation.\n\n"
                                       "## Current Long-term Memory\n" + current_memory + "\n\n"
                                       "## Conversation to Process\n" + conversation;
                
                nlohmann::json save_memory_tool;
                save_memory_tool["type"] = "function";
                save_memory_tool["function"]["name"] = "save_memory";
                save_memory_tool["function"]["description"] = "Save the memory consolidation result to persistent storage.";
                nlohmann::json params_obj;
                params_obj["type"] = "object";
                params_obj["properties"]["history_entry"] = nlohmann::json{{"type", "string"}, {"description", "A paragraph (2-5 sentences) summarizing key events/decisions/topics. Start with [YYYY-MM-DD HH:MM]."}};
                params_obj["properties"]["memory_update"] = nlohmann::json{{"type", "string"}, {"description", "Full updated long-term memory as markdown. Include all existing facts plus new ones."}};
                params_obj["required"] = nlohmann::json{"history_entry", "memory_update"};
                save_memory_tool["function"]["parameters"] = params_obj;
                
                ChatCompletionRequest request;
                request.model = config.model;
                request.temperature = config.temperature;
                request.max_tokens = config.max_tokens;
                request.messages.emplace_back("system", system_prompt);
                request.messages.emplace_back("user", user_prompt);
                request.tools.push_back(save_memory_tool);
                request.tool_choice_auto = true;
                
                ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=async stage=request_llm");
                
                auto response = llm_prov->chat_completion(request);
                
                if (response.tool_calls.empty()) {
                    ICRAW_LOG_WARN("[AgentLoop][memory_consolidation_failed] mode=async reason=missing_save_memory_tool");
                    return false;
                }
                
                try {
                    auto args = response.tool_calls[0].arguments;
                    
                    std::string history_entry;
                    std::string memory_update;
                    
                    if (args.is_string()) {
                        auto parsed = nlohmann::json::parse(args.get<std::string>());
                        history_entry = parsed.value("history_entry", "");
                        memory_update = parsed.value("memory_update", "");
                    } else if (args.is_object()) {
                        history_entry = args.value("history_entry", "");
                        memory_update = args.value("memory_update", "");
                    }
                    
                    if (!history_entry.empty()) {
                        memory_mgr->save_daily_memory(history_entry);
                        ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=async stage=save_history_entry content_length={} preview={}",
                                history_entry.size(), log_utils::truncate_for_debug(history_entry));
                    }
                    
                    if (!memory_update.empty() && memory_update != current_memory) {
                        memory_mgr->create_summary("default", memory_update, static_cast<int>(old_messages.size()));
                        ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=async stage=save_memory_update content_length={} preview={}",
                                memory_update.size(), log_utils::truncate_for_debug(memory_update));
                    }
                    
                    ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_complete] mode=async");
                    return true;
                    
                } catch (const std::exception& e) {
                    ICRAW_LOG_ERROR("[AgentLoop][memory_consolidation_failed] mode=async message={}", e.what());
                    return false;
                }
            });
    }
}

bool AgentLoop::perform_consolidation(const std::vector<Message>& messages) {
    (void)messages; // Suppress unused parameter warning
    
    if (!memory_manager_ || !llm_provider_) {
        ICRAW_LOG_WARN("[AgentLoop][memory_consolidation_failed] mode=sync reason=dependencies_unavailable");
        return false;
    }
    
    // Get messages to consolidate
    int keep_count = agent_config_.memory_window / 2;
    auto old_messages = memory_manager_->get_messages_for_consolidation(keep_count);
    
    if (old_messages.empty()) {
        ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=no_messages");
        return true;
    }
    
    ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_start] mode=sync message_count={}", old_messages.size());
    
    // Format messages for LLM - use simple string concatenation
    std::string conversation;
    for (const auto& msg : old_messages) {
        std::string tools_used;
        if (msg.metadata.contains("tools_used")) {
            auto tools = msg.metadata["tools_used"];
            for (const auto& t : tools) {
                if (!tools_used.empty()) tools_used += ", ";
                tools_used += t.get<std::string>();
            }
            if (!tools_used.empty()) {
                tools_used = " [tools: " + tools_used + "]";
            }
        }
        conversation += "[" + msg.timestamp.substr(0, 16) + "] " 
                    + msg.role + tools_used + ": " 
                    + msg.content + "\n";
    }
    
    // Get current long-term memory
    auto latest_summary = memory_manager_->get_latest_summary();
    std::string current_memory = latest_summary ? latest_summary->summary : "(empty)";
    
    // Build prompt for LLM
    std::string system_prompt = "You are a memory consolidation agent. Call the save_memory tool to save important information from the conversation.";
    std::string user_prompt = "Process this conversation and call the save_memory tool with your consolidation.\n\n"
                           "## Current Long-term Memory\n" + current_memory + "\n\n"
                           "## Conversation to Process\n" + conversation;
    
    // Create tool definition for save_memory
    nlohmann::json save_memory_tool;
    save_memory_tool["type"] = "function";
    save_memory_tool["function"]["name"] = "save_memory";
    save_memory_tool["function"]["description"] = "Save the memory consolidation result to persistent storage.";
    nlohmann::json params_obj;
    params_obj["type"] = "object";
    params_obj["properties"]["history_entry"] = nlohmann::json{{"type", "string"}, {"description", "A paragraph (2-5 sentences) summarizing key events/decisions/topics. Start with [YYYY-MM-DD HH:MM]."}};
    params_obj["properties"]["memory_update"] = nlohmann::json{{"type", "string"}, {"description", "Full updated long-term memory as markdown. Include all existing facts plus new ones."}};
    params_obj["required"] = nlohmann::json{"history_entry", "memory_update"};
    save_memory_tool["function"]["parameters"] = params_obj;
    
    // Create chat request
    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = agent_config_.temperature;
    request.max_tokens = agent_config_.max_tokens;
    request.enable_thinking = agent_config_.enable_thinking;
    request.messages.emplace_back("system", system_prompt);
    request.messages.emplace_back("user", user_prompt);
    request.tools.push_back(save_memory_tool);
    request.tool_choice_auto = true;
    
    // Log the consolidation request
    ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=request_llm");
    
    // Call LLM (non-streaming for consolidation)
    auto response = llm_provider_->chat_completion(request);
    
    // Check if LLM called the save_memory tool
    if (response.tool_calls.empty()) {
        ICRAW_LOG_WARN("[AgentLoop][memory_consolidation_failed] mode=sync reason=missing_save_memory_tool");
        return false;
    }
    
    // Parse and save the result
    try {
        auto args = response.tool_calls[0].arguments;
        
        // Handle both string and object arguments
        std::string history_entry;
        std::string memory_update;
        
        if (args.is_string()) {
            auto parsed = nlohmann::json::parse(args.get<std::string>());
            history_entry = parsed.value("history_entry", "");
            memory_update = parsed.value("memory_update", "");
        } else if (args.is_object()) {
            history_entry = args.value("history_entry", "");
            memory_update = args.value("memory_update", "");
        }
        
        if (!history_entry.empty()) {
            // Save to daily_memory (like mobile-agent's HISTORY.md)
            memory_manager_->save_daily_memory(history_entry);
            ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=save_history_entry content_length={} preview={}",
                    history_entry.size(), log_utils::truncate_for_debug(history_entry));
        }
        
        if (!memory_update.empty() && memory_update != current_memory) {
            // Save summary to summaries table
            memory_manager_->create_summary("default", memory_update, static_cast<int>(old_messages.size()));
            ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=save_memory_update content_length={} preview={}",
                    memory_update.size(), log_utils::truncate_for_debug(memory_update));
        }
        
        ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_complete] mode=sync");
        return true;
        
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[AgentLoop][memory_consolidation_failed] mode=sync message={}", e.what());
        return false;
    }
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
                    truncate_runtime_text(target_hint, 80));
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

// --- New Memory Management Methods ---

bool AgentLoop::should_flush_memory() const {
    if (!memory_manager_) {
        return false;
    }
    
    // Check if memory flush is enabled
    if (!agent_config_.compaction.memory_flush.enabled) {
        return false;
    }
    
    // Don't flush if we already flushed since last compaction
    int compaction_count = memory_manager_->get_compaction_count();
    if (last_flush_compaction_count_ == compaction_count) {
        return false;
    }
    
    return memory_manager_->needs_memory_flush(agent_config_.compaction);
}

bool AgentLoop::execute_memory_flush() {
    if (!memory_manager_ || !llm_provider_) {
        return false;
    }
    
    ICRAW_LOG_INFO("[AgentLoop][memory_flush_start]");
    
    // Get current time for prompt
    auto now = std::chrono::system_clock::now();
    auto now_time = std::chrono::system_clock::to_time_t(now);
    std::tm tm = *std::gmtime(&now_time);
    std::ostringstream time_ss;
    time_ss << std::put_time(&tm, "%Y-%m-%d %H:%M:%S UTC");
    std::string current_time = time_ss.str();
    
    // Build prompts
    std::string system_prompt = agent_config_.compaction.memory_flush.system_prompt;
    std::string user_prompt = agent_config_.compaction.memory_flush.user_prompt;
    
    // Replace placeholder
    size_t pos = user_prompt.find("{current_datetime}");
    if (pos != std::string::npos) {
        user_prompt.replace(pos, 17, current_time);
    }
    
    // Create save_memory tool
    nlohmann::json save_memory_tool;
    save_memory_tool["type"] = "function";
    save_memory_tool["function"]["name"] = "save_memory";
    save_memory_tool["function"]["description"] = "Save important information to long-term memory.";
    nlohmann::json params;
    params["type"] = "object";
    params["properties"]["content"] = nlohmann::json{
        {"type", "string"}, 
        {"description", "The memory content to save"}
    };
    params["required"] = nlohmann::json::array({"content"});
    save_memory_tool["function"]["parameters"] = params;
    
    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = 0.3;  // Lower temperature for memory extraction
    request.max_tokens = 1024;
    request.enable_thinking = agent_config_.enable_thinking;
    request.messages.emplace_back("system", system_prompt);
    request.messages.emplace_back("user", user_prompt);
    request.tools.push_back(save_memory_tool);
    request.tool_choice_auto = true;
    
    try {
        auto response = llm_provider_->chat_completion(request);
        
        // Process save_memory tool calls
        for (const auto& tc : response.tool_calls) {
            if (tc.name == "save_memory") {
                auto args = tc.arguments;
                std::string content;
                
                if (args.is_string()) {
                    auto parsed = nlohmann::json::parse(args.get<std::string>());
                    content = parsed.value("content", "");
                } else if (args.is_object()) {
                    content = args.value("content", "");
                }
                
                if (!content.empty()) {
                    memory_manager_->save_daily_memory(content);
                    ICRAW_LOG_DEBUG("[AgentLoop][memory_flush_debug] content_length={} preview={}",
                            content.size(), log_utils::truncate_for_debug(content));
                }
            }
        }
        
        // Record flush execution
        memory_manager_->record_memory_flush();
        last_flush_compaction_count_ = memory_manager_->get_compaction_count();
        
        ICRAW_LOG_INFO("[AgentLoop][memory_flush_complete]");
        return true;
        
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[AgentLoop][memory_flush_failed] message={}", e.what());
        return false;
    }
}

bool AgentLoop::should_compact() const {
    if (!memory_manager_) {
        return false;
    }
    
    int64_t total_tokens = memory_manager_->get_total_tokens();
    return should_trigger_compaction(static_cast<int>(total_tokens), agent_config_.compaction);
}

int AgentLoop::get_current_token_count() const {
    if (!memory_manager_) {
        return 0;
    }
    return static_cast<int>(memory_manager_->get_total_tokens());
}

MemoryMetrics AgentLoop::get_memory_metrics() const {
    MemoryMetrics metrics;
    
    if (!memory_manager_) {
        return metrics;
    }
    
    metrics.total_messages = memory_manager_->get_message_count();
    metrics.context_tokens = get_current_token_count();
    metrics.compaction_count = static_cast<int>(memory_manager_->get_compaction_count());
    
    auto stats = memory_manager_->get_token_stats();
    if (stats) {
        // Could add more stats here
    }
    
    auto latest_compaction = memory_manager_->get_latest_compaction();
    if (latest_compaction) {
        if (latest_compaction->tokens_before > 0) {
            metrics.compression_ratio = static_cast<double>(latest_compaction->tokens_after) / 
                                        latest_compaction->tokens_before;
        }
    }
    
    return metrics;
}

CompactionResult AgentLoop::perform_compaction_with_fallback(
    const std::vector<MemoryEntry>& messages) {
    
    if (messages.empty()) {
        return CompactionResult::Success;
    }
    
    ICRAW_LOG_INFO("[AgentLoop][compaction_start] message_count={}", messages.size());
    
    // Try full compaction first
    try {
        // Chunk messages if too large
        auto chunks = chunk_messages_by_tokens(messages, agent_config_.compaction.max_chunk_tokens);
        
        std::string combined_summary;
        int total_tokens_before = estimate_memory_entries_tokens(messages);
        int64_t first_kept_id = messages.empty() ? 0 : messages.back().id;
        
        for (size_t i = 0; i < chunks.size(); ++i) {
            ICRAW_LOG_DEBUG("[AgentLoop][compaction_debug] stage=chunk index={} total={} message_count={}", 
                i + 1, chunks.size(), chunks[i].size());
            
            auto current_summary = memory_manager_->get_latest_summary();
            std::string summary_str = current_summary ? current_summary->summary : "";
            
            std::string prompt = build_consolidation_prompt(chunks[i], summary_str);
            
            // Create save_memory tool
            nlohmann::json save_memory_tool;
            save_memory_tool["type"] = "function";
            save_memory_tool["function"]["name"] = "save_memory";
            save_memory_tool["function"]["description"] = "Save the memory consolidation result.";
            nlohmann::json params;
            params["type"] = "object";
            params["properties"]["history_entry"] = nlohmann::json{
                {"type", "string"}, 
                {"description", "A paragraph (2-5 sentences) summarizing key events/decisions/topics. Start with [YYYY-MM-DD HH:MM]."}
            };
            params["properties"]["memory_update"] = nlohmann::json{
                {"type", "string"}, 
                {"description", "Full updated long-term memory as markdown. Include all existing facts plus new ones."}
            };
            params["required"] = nlohmann::json::array({"history_entry", "memory_update"});
            save_memory_tool["function"]["parameters"] = params;
            
            ChatCompletionRequest request;
            request.model = agent_config_.model;
            request.temperature = 0.3;
            request.max_tokens = agent_config_.max_tokens;
            request.enable_thinking = agent_config_.enable_thinking;
            request.messages.emplace_back("system", 
                "You are a memory consolidation agent. Call the save_memory tool to save important information.");
            request.messages.emplace_back("user", prompt);
            request.tools.push_back(save_memory_tool);
            request.tool_choice_auto = true;
            
            auto response = llm_provider_->chat_completion(request);
            
            if (response.tool_calls.empty()) {
                ICRAW_LOG_WARN("[AgentLoop][compaction_failed] reason=missing_save_memory_tool chunk_index={}", i + 1);
                continue;
            }
            
            auto args = response.tool_calls[0].arguments;
            std::string history_entry;
            std::string memory_update;
            
            if (args.is_string()) {
                auto parsed = nlohmann::json::parse(args.get<std::string>());
                history_entry = parsed.value("history_entry", "");
                memory_update = parsed.value("memory_update", "");
            } else if (args.is_object()) {
                history_entry = args.value("history_entry", "");
                memory_update = args.value("memory_update", "");
            }
            
            if (!history_entry.empty()) {
                memory_manager_->save_daily_memory(history_entry);
            }
            
            if (!memory_update.empty()) {
                combined_summary = memory_update;
            }
            
            if (!chunks[i].empty()) {
                first_kept_id = std::min(first_kept_id, chunks[i].back().id);
            }
        }
        
        // Save final summary
        if (!combined_summary.empty()) {
            memory_manager_->create_summary("default", combined_summary, 
                static_cast<int>(messages.size()));
        }
        
        // Mark messages as consolidated
        memory_manager_->mark_consolidated(static_cast<int>(messages.size()));
        
        // Create compaction record
        int total_tokens_after = estimate_tokens(combined_summary);
        memory_manager_->create_compaction_record("default", combined_summary,
            first_kept_id, total_tokens_before, total_tokens_after, "full");
        
        // Update token stats
        memory_manager_->update_token_stats();
        
        ICRAW_LOG_INFO("[AgentLoop][compaction_complete] tokens_before={} tokens_after={} reduction_percent={:.1f}",
            total_tokens_before, total_tokens_after,
            100.0 * (1.0 - static_cast<double>(total_tokens_after) / total_tokens_before));
        
        return CompactionResult::Success;
        
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[AgentLoop][compaction_failed] message={}", e.what());
        
        // Fallback: just record metadata
        try {
            // Generate timestamp locally
            auto now = std::chrono::system_clock::now();
            auto now_time = std::chrono::system_clock::to_time_t(now);
            std::tm tm = *std::gmtime(&now_time);
            std::ostringstream time_ss;
            time_ss << std::put_time(&tm, "%Y-%m-%dT%H:%M:%SZ");
            
            std::string fallback_summary = "Compaction failed at " + time_ss.str() + 
                ". Messages: " + std::to_string(messages.size());
            memory_manager_->create_summary("default", fallback_summary, 
                static_cast<int>(messages.size()));
            memory_manager_->mark_consolidated(static_cast<int>(messages.size()));
            
            return CompactionResult::Fallback;
        } catch (...) {
            return CompactionResult::Failed;
        }
    }
}

std::string AgentLoop::build_consolidation_prompt(
    const std::vector<MemoryEntry>& messages,
    const std::string& current_summary) const {
    
    std::ostringstream ss;
    
    ss << "Process this conversation and call the save_memory tool with your consolidation.\n\n";
    
    // Add identifier preservation instructions
    ss << get_identifier_preservation_prompt(agent_config_.compaction.identifier_policy);
    
    ss << "\n## Current Long-term Memory\n";
    ss << (current_summary.empty() ? "(empty)" : current_summary) << "\n\n";
    
    ss << "## Conversation to Process\n";
    for (const auto& msg : messages) {
        std::string tools_used;
        if (msg.metadata.contains("tools_used")) {
            auto tools = msg.metadata["tools_used"];
            for (const auto& t : tools) {
                if (!tools_used.empty()) tools_used += ", ";
                tools_used += t.get<std::string>();
            }
            if (!tools_used.empty()) {
                tools_used = " [tools: " + tools_used + "]";
            }
        }
        
        std::string timestamp = msg.timestamp;
        if (timestamp.length() >= 16) {
            timestamp = timestamp.substr(0, 16);
        }
        
        ss << "[" << timestamp << "] " << msg.role << tools_used << ": " 
           << msg.content << "\n";
    }
    
    return ss.str();
}

void AgentLoop::prune_context_tool_results(std::vector<Message>& messages, int max_chars) {
    const int total_tool_result_messages = count_tool_result_messages(messages);
    if (total_tool_result_messages <= static_cast<int>(CONTEXT_DETAILED_TOOL_RESULT_COUNT)) {
        return;
    }

    int tool_result_index = 0;
    size_t total_before = 0;
    size_t total_after = 0;

    for (auto& msg : messages) {
        if (msg.role != "tool") {
            continue;
        }
        for (auto& block : msg.content) {
            if (block.type != "tool_result") {
                continue;
            }
            tool_result_index++;
            const bool keep_detailed =
                    (total_tool_result_messages - tool_result_index) < static_cast<int>(CONTEXT_DETAILED_TOOL_RESULT_COUNT);
            const int limit = keep_detailed ? CONTEXT_RECENT_TOOL_RESULT_MAX_CHARS : max_chars;
            total_before += block.content.size();
            block.content = prune_tool_result(block.content, limit);
            total_after += block.content.size();
        }
    }

    if (total_after < total_before) {
        ICRAW_LOG_INFO(
                "[AgentLoop][context_tool_results_pruned] tool_result_count={} original_chars={} pruned_chars={}",
                total_tool_result_messages,
                total_before,
                total_after);
    }
}

} // namespace icraw
