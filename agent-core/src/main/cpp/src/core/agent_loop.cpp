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
    reduced = remove_markdown_section(reduced, "Available Tools");
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
constexpr size_t OBSERVATION_VISIBLE_TEXT_MAX_CHARS = 16000;
constexpr bool COMPACT_NAVIGATION_ESCALATION_ENABLED = false;

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

    // Stage 2: resolve route/navigation/readout intent from the user message
    // and selected skills. Known skills can resolve locally from execution_hints;
    // unknown or insufficient hints may use a lightweight route LLM request.
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

    // Stage 3: run the turn loop. Each pass first gives the local navigation
    // state machine a chance to advance without spending an LLM round.
    int iteration = 0;
    auto loop_start_time = std::chrono::steady_clock::now();
    ICRAW_LOG_INFO("[AgentLoop][loop_start] mode=non_stream max_iterations={}", max_iterations_);
    while (iteration < max_iterations_ && !stop_requested_) {
        bool deterministic_navigation_executed = false;
        if (execution_state.mode == "planned_fast_execute"
                && execution_state.route_ready
                && !execution_state.goal_reached) {
            // Happy path for structured skills: observe the current page, match
            // the pending NavigationStep, and tap locally when there is exactly
            // one safe high-confidence candidate. Ambiguous/no-progress cases
            // deliberately fall through to the LLM path below.
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
                // Observation is refreshed when no page snapshot exists yet, a
                // gesture happened after the last snapshot, a tap is awaiting
                // confirmation, or the pending target hint changed.
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
                // We already have a fresh observation for the current step, so
                // try a zero-LLM fast execute before asking the model again.
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
                // Once the stop condition is hit, discard navigation history
                // before readout so the final model call only reads target-page
                // content instead of old pages and gesture traces.
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

        // Stage 4: fallback/normal LLM step. The effective request is rebuilt
        // for the current phase, so route/navigation/readout can use different
        // tool sets, token budgets, and context compaction rules.
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
            // Tool-driven observations can also detect the target page. Reset
            // immediately so the next iteration becomes a clean readout call.
            reset_messages_for_readout(request.messages, execution_state);
            execution_state.context_reset = true;
            ICRAW_LOG_INFO("[AgentLoop][goal_reached_context_reset] mode=non_stream phase={} current_page={}",
                    execution_state.phase, execution_state.current_page);
        }

        if (!tool_results.empty()) {
            const std::string last_tool_name = tool_calls_for_execution.back().name;
            if (last_tool_name == "android_view_context_tool") {
                // LLM may request an observation first; when that observation
                // gives a unique candidate, execute the next tap locally instead
                // of spending another model round.
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

    // Stage 2 resolves the route before the main loop so navigation can often
    // proceed locally without waiting for a model decision on every step.
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

    // Stage 3 mirrors non-stream mode, with callbacks emitted around local tool
    // execution so observers see deterministic navigation progress too.
    int iteration = 0;
    auto loop_start_time = std::chrono::steady_clock::now();
    ICRAW_LOG_INFO("[AgentLoop][loop_start] mode=stream max_iterations={}", max_iterations_);
    while (iteration < max_iterations_ && !stop_requested_) {
        bool deterministic_navigation_executed = false;
        if (execution_state.mode == "planned_fast_execute"
                && execution_state.route_ready
                && !execution_state.goal_reached) {
            // Local navigation is attempted before any LLM call. It only fires
            // on a single safe candidate; otherwise latest_escalation records
            // why the model must take over.
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
                // Refresh the snapshot only when the current one cannot safely
                // confirm or advance the pending navigation step.
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
                // Fresh observation is available, so try to convert it directly
                // into a gesture tool call before spending another LLM round.
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
                // Target reached: collapse the transcript to a readout-focused
                // context and leave old navigation details behind.
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

        // Stage 4: model fallback or readout. Streaming mode still applies the
        // same phase-specific request profile and context trimming.
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
            // A tool result can satisfy the stop condition after the LLM phase;
            // reset immediately so any following model call is readout-only.
            reset_messages_for_readout(request.messages, execution_state);
            execution_state.context_reset = true;
            ICRAW_LOG_INFO("[AgentLoop][goal_reached_context_reset] mode=stream phase={} current_page={}",
                    execution_state.phase, execution_state.current_page);
        }

        if (!tool_results.empty()) {
            const std::string last_tool_name = tool_calls_for_execution.back().name;
            if (last_tool_name == "android_view_context_tool") {
                // Same optimization as non-stream mode: an observation result
                // can immediately produce a local tap when matching is unique.
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
