#include "icraw/mobile_agent.hpp"
#include "icraw/platform/android/android_tools.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/tools/tool_registry.hpp"
#include "icraw/core/prompt_builder.hpp"
#include "icraw/core/agent_loop.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/core/llm_provider_factory.hpp"
#include "icraw/core/http_client.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"
#include <algorithm>
#include <array>
#include <cctype>
#include <functional>
#include <initializer_list>
#include <sstream>
#include <unordered_set>
#include <utility>

namespace icraw {

static std::string trim_whitespace(const std::string& text) {
    const size_t start = text.find_first_not_of(" \t\n\r");
    if (start == std::string::npos) {
        return "";
    }
    const size_t end = text.find_last_not_of(" \t\n\r");
    return text.substr(start, end - start + 1);
}

namespace {

constexpr size_t PRELOADED_SKILL_MAX_COUNT = 2;
constexpr size_t PRELOADED_SKILL_MAX_TOTAL_CHARS = 20000;
constexpr int PRELOADED_SKILL_MIN_SCORE = 16;
constexpr int ROUTING_STRONG_MARKER_BONUS = 160;
constexpr int ROUTING_WEAK_MARKER_BONUS = 12;
constexpr int ROUTING_NEGATIVE_MARKER_PENALTY = 220;
constexpr int ROUTING_COMPANION_SCORE = 80;

void replace_all(std::string& text, const std::string& needle, const std::string& replacement) {
    if (needle.empty()) {
        return;
    }
    size_t pos = 0;
    while ((pos = text.find(needle, pos)) != std::string::npos) {
        text.replace(pos, needle.size(), replacement);
        pos += replacement.size();
    }
}

std::string normalize_for_match(std::string text) {
    static const std::array<std::string, 15> unicode_punctuation = {
        "\xE2\x80\x9C", "\xE2\x80\x9D", "\xE2\x80\x98", "\xE2\x80\x99",
        "\xE3\x80\x82", "\xEF\xBC\x8C", "\xEF\xBC\x9A", "\xEF\xBC\x9B",
        "\xEF\xBC\x81", "\xEF\xBC\x9F", "\xEF\xBC\x88", "\xEF\xBC\x89",
        "\xE3\x80\x81", "\xE3\x80\x8A", "\xE3\x80\x8B"
    };
    for (const auto& mark : unicode_punctuation) {
        replace_all(text, mark, " ");
    }

    std::string normalized;
    normalized.reserve(text.size());
    for (unsigned char ch : text) {
        if (std::isspace(ch) || std::ispunct(ch)) {
            continue;
        }
        normalized.push_back(static_cast<char>(std::tolower(ch)));
    }
    return normalized;
}

bool is_generic_skill_phrase(const std::string& normalized_phrase) {
    static const std::array<std::string, 13> generic_phrases = {
        normalize_for_match("\xE7\x94\xA8\xE6\x88\xB7"),
        normalize_for_match("\xE5\xBD\x93\xE5\x89\x8D"),
        normalize_for_match("\xE9\xA1\xB5\xE9\x9D\xA2"),
        normalize_for_match("\xE5\x86\x85\xE5\xAE\xB9"),
        normalize_for_match("\xE8\xAF\xB4\xE6\x98\x8E"),
        normalize_for_match("\xE8\xA7\xA6\xE5\x8F\x91\xE6\x9D\xA1\xE4\xBB\xB6"),
        normalize_for_match("\xE5\xB7\xA5\xE4\xBD\x9C\xE6\xB5\x81\xE7\xA8\x8B"),
        normalize_for_match("\xE5\x86\xB3\xE7\xAD\x96\xE8\xA7\x84\xE5\x88\x99"),
        normalize_for_match("\xE8\xBE\x93\xE5\x87\xBA\xE8\xA6\x81\xE6\xB1\x82"),
        normalize_for_match("\xE9\x94\x99\xE8\xAF\xAF\xE5\xA4\x84\xE7\x90\x86"),
        normalize_for_match("\xE6\xB3\xA8\xE6\x84\x8F"),
        normalize_for_match("skill"),
        normalize_for_match("agent")
    };
    return std::find(generic_phrases.begin(), generic_phrases.end(), normalized_phrase) != generic_phrases.end();
}

bool contains_normalized(const std::string& normalized_text, const std::string& normalized_term) {
    return !normalized_term.empty()
            && normalized_text.find(normalized_term) != std::string::npos;
}

bool contains_ordered_pair(const std::string& normalized_text,
                           const std::string& first,
                           const std::string& second,
                           size_t max_gap_bytes = 72) {
    const size_t first_pos = normalized_text.find(first);
    if (first_pos == std::string::npos) {
        return false;
    }
    const size_t second_pos = normalized_text.find(second, first_pos + first.size());
    if (second_pos == std::string::npos) {
        return false;
    }
    return second_pos >= first_pos && second_pos - first_pos <= max_gap_bytes;
}

int skill_phrase_match_score(const std::string& normalized_phrase);

const nlohmann::json* find_json_value(const nlohmann::json& object,
                                      std::initializer_list<const char*> keys) {
    if (!object.is_object()) {
        return nullptr;
    }
    for (const char* key : keys) {
        auto it = object.find(key);
        if (it != object.end()) {
            return &(*it);
        }
    }
    return nullptr;
}

std::vector<std::string> json_string_list(const nlohmann::json& object,
                                          std::initializer_list<const char*> keys) {
    std::vector<std::string> values;
    const nlohmann::json* value = find_json_value(object, keys);
    if (value == nullptr) {
        return values;
    }
    if (value->is_string()) {
        values.push_back(value->get<std::string>());
        return values;
    }
    if (!value->is_array()) {
        return values;
    }
    for (const auto& item : *value) {
        if (item.is_string()) {
            values.push_back(item.get<std::string>());
        }
    }
    return values;
}

std::vector<std::pair<std::string, std::string>> json_ordered_pairs(
        const nlohmann::json& object,
        std::initializer_list<const char*> keys) {
    std::vector<std::pair<std::string, std::string>> pairs;
    const nlohmann::json* value = find_json_value(object, keys);
    if (value == nullptr || !value->is_array()) {
        return pairs;
    }
    for (const auto& item : *value) {
        if (item.is_array() && item.size() >= 2 && item[0].is_string() && item[1].is_string()) {
            pairs.emplace_back(item[0].get<std::string>(), item[1].get<std::string>());
            continue;
        }
        if (item.is_object()) {
            const nlohmann::json* first = find_json_value(item, {"first", "before", "from"});
            const nlohmann::json* second = find_json_value(item, {"second", "after", "to"});
            if (first != nullptr && second != nullptr && first->is_string() && second->is_string()) {
                pairs.emplace_back(first->get<std::string>(), second->get<std::string>());
            }
        }
    }
    return pairs;
}

bool json_bool_value(const nlohmann::json& object,
                     std::initializer_list<const char*> keys,
                     bool default_value) {
    const nlohmann::json* value = find_json_value(object, keys);
    if (value == nullptr || !value->is_boolean()) {
        return default_value;
    }
    return value->get<bool>();
}

struct RoutingScore {
    bool has_hints = false;
    bool requires_primary_marker = false;
    bool primary_matched = false;
    int score = 0;
    int strong_score = 0;
    int weak_score = 0;
    int negative_score = 0;
    std::vector<std::string> companion_skills;
};

int score_routing_markers(const std::string& normalized_message,
                          const std::vector<std::string>& markers,
                          int base_score,
                          bool phrase_weighted,
                          bool* matched = nullptr) {
    int score = 0;
    for (const auto& marker : markers) {
        const std::string normalized_marker = normalize_for_match(marker);
        if (!contains_normalized(normalized_message, normalized_marker)) {
            continue;
        }
        if (matched != nullptr) {
            *matched = true;
        }
        score += base_score;
        if (phrase_weighted) {
            score += skill_phrase_match_score(normalized_marker);
        }
    }
    return score;
}

RoutingScore score_skill_routing(const SkillMetadata& skill,
                                 const std::string& normalized_message) {
    RoutingScore routing;
    if (!skill.routing_hints.is_object()) {
        return routing;
    }

    routing.has_hints = true;
    routing.requires_primary_marker = json_bool_value(
            skill.routing_hints, {"requires_primary_marker", "requiresPrimaryMarker"}, false);
    routing.companion_skills = json_string_list(
            skill.routing_hints, {"companion_skills", "companionSkills"});

    bool primary_matched = false;
    const auto strong_markers = json_string_list(
            skill.routing_hints, {"strong_markers", "strongMarkers", "primary_markers", "primaryMarkers"});
    routing.strong_score += score_routing_markers(
            normalized_message,
            strong_markers,
            ROUTING_STRONG_MARKER_BONUS,
            true,
            &primary_matched);

    const auto strong_pairs = json_ordered_pairs(
            skill.routing_hints, {"strong_ordered_pairs", "strongOrderedPairs", "ordered_pairs", "orderedPairs"});
    for (const auto& [first, second] : strong_pairs) {
        const std::string normalized_first = normalize_for_match(first);
        const std::string normalized_second = normalize_for_match(second);
        if (!contains_ordered_pair(normalized_message, normalized_first, normalized_second, 96)) {
            continue;
        }
        primary_matched = true;
        routing.strong_score += ROUTING_STRONG_MARKER_BONUS
                + skill_phrase_match_score(normalized_first + normalized_second);
    }

    const auto weak_markers = json_string_list(
            skill.routing_hints, {"weak_markers", "weakMarkers", "secondary_markers", "secondaryMarkers"});
    routing.weak_score += score_routing_markers(
            normalized_message,
            weak_markers,
            ROUTING_WEAK_MARKER_BONUS,
            false);

    const auto negative_markers = json_string_list(
            skill.routing_hints, {"negative_markers", "negativeMarkers"});
    routing.negative_score += score_routing_markers(
            normalized_message,
            negative_markers,
            ROUTING_NEGATIVE_MARKER_PENALTY,
            false);

    routing.primary_matched = primary_matched;
    routing.score = routing.strong_score + routing.weak_score - routing.negative_score;
    return routing;
}

bool has_selected_skill(const std::vector<SkillMetadata>& selected, const std::string& name) {
    return std::any_of(selected.begin(), selected.end(), [&](const SkillMetadata& skill) {
        return skill.name == name;
    });
}

const SkillMetadata* find_available_skill(const std::vector<SkillMetadata>& skills, const std::string& name) {
    auto it = std::find_if(skills.begin(), skills.end(), [&](const SkillMetadata& skill) {
        return skill.name == name;
    });
    return it == skills.end() ? nullptr : &(*it);
}

std::vector<std::string> extract_quoted_phrases(const std::string& text) {
    static const std::array<std::pair<std::string, std::string>, 3> quote_pairs = {{
        {"\"", "\""},
        {"\xE2\x80\x9C", "\xE2\x80\x9D"},
        {"\xE2\x80\x98", "\xE2\x80\x99"}
    }};

    std::vector<std::string> phrases;
    for (const auto& [open_quote, close_quote] : quote_pairs) {
        size_t start = 0;
        while (start < text.size()) {
            const size_t open_pos = text.find(open_quote, start);
            if (open_pos == std::string::npos) {
                break;
            }
            const size_t content_start = open_pos + open_quote.size();
            const size_t close_pos = text.find(close_quote, content_start);
            if (close_pos == std::string::npos) {
                break;
            }
            const std::string phrase = trim_whitespace(text.substr(content_start, close_pos - content_start));
            if (!phrase.empty()) {
                phrases.push_back(phrase);
            }
            start = close_pos + close_quote.size();
        }
    }
    return phrases;
}

std::vector<std::string> split_fragments_for_matching(std::string text) {
    static const std::array<std::string, 15> delimiters = {
        "\n", "\xE3\x80\x82", "\xEF\xBC\x8C", "\xEF\xBC\x9A", "\xEF\xBC\x9B",
        "\xEF\xBC\x81", "\xEF\xBC\x9F", "\xEF\xBC\x88", "\xEF\xBC\x89",
        "\xE3\x80\x81", ",", ".", ":", ";", "!"
    };
    for (const auto& delimiter : delimiters) {
        replace_all(text, delimiter, "\n");
    }

    std::vector<std::string> fragments;
    std::istringstream stream(text);
    std::string line;
    while (std::getline(stream, line)) {
        std::string candidate = trim_whitespace(line);
        if (candidate.rfind("##", 0) == 0) {
            candidate = trim_whitespace(candidate.substr(2));
        } else if (!candidate.empty() && (candidate[0] == '-' || candidate[0] == '*')) {
            candidate = trim_whitespace(candidate.substr(1));
        }

        if (candidate.size() < 6 || candidate.size() > 96) {
            continue;
        }
        fragments.push_back(candidate);
    }
    return fragments;
}

std::vector<std::string> collect_skill_match_phrases(const SkillMetadata& skill) {
    std::vector<std::string> phrases;
    std::unordered_set<std::string> seen;

    auto append_phrase = [&](const std::string& raw_phrase) {
        const std::string phrase = trim_whitespace(raw_phrase);
        if (phrase.empty()) {
            return;
        }
        const std::string normalized = normalize_for_match(phrase);
        if (normalized.size() < 4 || is_generic_skill_phrase(normalized)) {
            return;
        }
        if (seen.insert(normalized).second) {
            phrases.push_back(phrase);
        }
    };

    append_phrase(skill.name);
    append_phrase(skill.description);

    for (const auto& phrase : extract_quoted_phrases(skill.description)) {
        append_phrase(phrase);
    }
    for (const auto& phrase : extract_quoted_phrases(skill.content)) {
        append_phrase(phrase);
    }
    for (const auto& fragment : split_fragments_for_matching(skill.description)) {
        append_phrase(fragment);
    }
    for (const auto& fragment : split_fragments_for_matching(skill.content)) {
        append_phrase(fragment);
    }
    if (skill.execution_hints.is_object()) {
        std::function<void(const nlohmann::json&)> append_json_strings =
                [&](const nlohmann::json& value) {
                    if (value.is_string()) {
                        append_phrase(value.get<std::string>());
                    } else if (value.is_array()) {
                        for (const auto& item : value) {
                            append_json_strings(item);
                        }
                    } else if (value.is_object()) {
                        for (const auto& item : value.items()) {
                            append_json_strings(item.value());
                        }
                    }
                };
        append_json_strings(skill.execution_hints);
    }

    return phrases;
}

int skill_phrase_match_score(const std::string& normalized_phrase) {
    return std::max(
            PRELOADED_SKILL_MIN_SCORE,
            std::min<int>(static_cast<int>(normalized_phrase.size()), 48));
}

std::string join_skill_names(const std::vector<SkillMetadata>& skills) {
    std::ostringstream stream;
    for (size_t i = 0; i < skills.size(); ++i) {
        if (i > 0) {
            stream << ",";
        }
        stream << skills[i].name;
    }
    return stream.str();
}

}  // namespace

static bool should_persist_message(const std::vector<Message>& messages, size_t index) {
    const Message& current = messages[index];
    if (current.role != "assistant") {
        return true;
    }

    if (index + 1 >= messages.size()) {
        return true;
    }

    const Message& next = messages[index + 1];
    // Only persist final user-visible assistant messages. If the next message is a
    // tool result, this assistant turn was just transitional tool-call prose.
    return next.role != "tool";
}
// MobileAgent implementation
MobileAgent::MobileAgent(const IcrawConfig& config)
    : config_(config) {

    // Initialize logger if configured
    if (config_.logging.enabled && !config_.logging.directory.empty()) {
        Logger::get_instance().init(config_.logging.directory, config_.logging.level);
    }

    ICRAW_LOG_INFO("[MobileAgent][initialize_start] workspace={}", config_.workspace_path.string());

    // Ensure workspace exists
    try {
        std::filesystem::create_directories(config_.workspace_path);
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[MobileAgent][workspace_prepare_failed] message={}", e.what());
        throw;
    }

    // Initialize components
    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=memory_manager");
    memory_manager_ = std::make_shared<MemoryManager>(config_.workspace_path);

    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=skill_loader");
    skill_loader_ = std::make_shared<SkillLoader>();
    available_skills_ = skill_loader_->load_skills(config_.skills, config_.workspace_path);
    ICRAW_LOG_INFO("[MobileAgent][skills_loaded] count={}", available_skills_.size());

    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=tool_registry");
    tool_registry_ = std::make_shared<ToolRegistry>();
    tool_registry_->set_base_path(config_.workspace_path.string());
    tool_registry_->set_memory_manager(memory_manager_.get());
    tool_registry_->register_builtin_tools();

    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=prompt_builder");
    prompt_builder_ = std::make_shared<PromptBuilder>(
        memory_manager_, skill_loader_, tool_registry_);

    // Create provider via factory so vendor-specific behavior lives in concrete classes.
    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=llm_provider model={}", config_.agent.model);
    auto provider = LLMProviderFactory::create_openai_compatible_provider(
        config_.provider.api_key,
        config_.provider.base_url,
        config_.agent.model);
    ICRAW_LOG_INFO("[MobileAgent][provider_created] provider={} base_url={} model={}",
            provider->get_provider_name(), config_.provider.base_url, config_.agent.model);

    // Create and set HTTP client
    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=http_client");
    auto http_client = std::make_unique<CurlHttpClient>();
    provider->set_http_client(std::move(http_client));

    llm_provider_ = provider;

    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=agent_loop");
    agent_loop_ = std::make_unique<AgentLoop>(
        memory_manager_, skill_loader_, tool_registry_,
        llm_provider_, config_.agent);

    // Load conversation history from database
    ICRAW_LOG_INFO("[MobileAgent][history_load_start] memory_window={}", config_.agent.memory_window);
    load_history_from_memory();

    ICRAW_LOG_INFO("[MobileAgent][initialize_complete] history_count={}", history_.size());
}

void MobileAgent::load_history_from_memory() {
    // Load recent messages from database into history
    auto memory_entries = memory_manager_->get_recent_messages(
        config_.agent.memory_window, "default");
    
    history_.clear();
    history_.reserve(memory_entries.size());
    
    for (const auto& entry : memory_entries) {
        // Skip tool messages - they require corresponding tool_calls in assistant messages
        // which we don't store in the database. Loading only user/assistant messages
        // preserves the conversation context without breaking API requirements.
        if (entry.role == "tool") {
            continue;
        }
        
        Message msg;
        msg.role = entry.role;
        msg.content.push_back(ContentBlock::make_text(entry.content));
        history_.push_back(std::move(msg));
    }
    
    ICRAW_LOG_INFO("[MobileAgent][history_load_complete] message_count={}", history_.size());
}

MobileAgent::~MobileAgent() = default;

std::string MobileAgent::chat(const std::string& message) {
    icraw::g_android_tools.set_current_session_id("default");
    const auto selected_skills = select_relevant_skills_for_message(message);
    log_selected_skills(selected_skills);
    const std::string runtime_prompt = build_system_prompt_for_message("default");
    auto new_messages = agent_loop_->process_message(
        message, history_, runtime_prompt, selected_skills);
    icraw::g_android_tools.clear_current_session_id();
    
    // Add new messages to history
    for (const auto& msg : new_messages) {
        history_.push_back(msg);
    }
    
    // Return the last assistant message text
    for (auto it = new_messages.rbegin(); it != new_messages.rend(); ++it) {
        if (it->role == "assistant") {
            return it->text();
        }
    }
    
    return "";
}

void MobileAgent::chat_stream(const std::string& message, AgentEventCallback callback) {
    chat_stream("default", message, std::move(callback));
}

void MobileAgent::chat_stream(const std::string& session_id,
                              const std::string& message,
                              AgentEventCallback callback) {
    const std::string effective_session_id = session_id.empty() ? "default" : session_id;
    icraw::g_android_tools.set_current_session_id(effective_session_id);
    auto session_entries = memory_manager_->get_recent_messages(
        config_.agent.memory_window, effective_session_id);
    std::vector<Message> session_history;
    session_history.reserve(session_entries.size());

    for (const auto& entry : session_entries) {
        if (entry.role == "tool") {
            continue;
        }

        Message msg;
        msg.role = entry.role;
        msg.content.push_back(ContentBlock::make_text(entry.content));
        session_history.push_back(std::move(msg));
    }

    const auto selected_skills = select_relevant_skills_for_message(message);
    log_selected_skills(selected_skills);
    const std::string runtime_prompt = build_system_prompt_for_message(effective_session_id);

    // Save user message to SQLite first
    ICRAW_LOG_INFO("[MobileAgent][chat_stream_start] session_id={} input_length={}",
            effective_session_id, message.size());
    ICRAW_LOG_DEBUG("[MobileAgent][chat_stream_debug] session_id={} input={}",
            effective_session_id, log_utils::truncate_for_debug(message));
    if (!message.empty()) {
        auto result = memory_manager_->add_message("user", message, effective_session_id, nlohmann::json{});
        ICRAW_LOG_DEBUG("[MobileAgent][message_persist_debug] session_id={} role=user success={}",
                effective_session_id, result);
    }

    ICRAW_LOG_DEBUG("[MobileAgent][chat_stream_debug] state=process_message_stream_start");
    auto new_messages = agent_loop_->process_message_stream(
        message, session_history, runtime_prompt, callback, selected_skills);
    ICRAW_LOG_INFO("[MobileAgent][chat_stream_loop_complete] session_id={} new_message_count={}",
            effective_session_id, new_messages.size());
    
    // Add new messages to history and memory
    for (size_t i = 0; i < new_messages.size(); ++i) {
        const auto& msg = new_messages[i];
        session_history.push_back(msg);

        if (!should_persist_message(new_messages, i)) {
            ICRAW_LOG_DEBUG("[MobileAgent][message_persist_skipped] session_id={} index={} reason=intermediate_assistant",
                    effective_session_id, i);
            continue;
        }
        
        // Save to memory manager
        if (!msg.content.empty()) {
            std::string content;
            for (const auto& block : msg.content) {
                // Skip thinking content; it is internal reasoning, not user-visible text.
                if (block.type == "thinking") {
                    continue;
                }
                if (!block.text.empty()) {
                    content += block.text + " ";
                }
            }
            // Trim before persisting so whitespace-only assistant/tool-call turns
            // do not become empty chat bubbles after history reload.
            content = trim_whitespace(content);
            if (!content.empty()) {
                nlohmann::json metadata;
                // Store tool calls if present
                if (!msg.content.empty() && msg.content[0].type == "tool_use") {
                    metadata["is_tool_call"] = true;
                }
                auto result = memory_manager_->add_message(msg.role, content, effective_session_id, metadata);
                ICRAW_LOG_DEBUG("[MobileAgent][message_persist_debug] session_id={} role={} success={} content_length={}",
                        effective_session_id, msg.role, result, content.size());
            }
        }
    }
    if (effective_session_id == "default") {
        history_ = session_history;
    }
    
    ICRAW_LOG_DEBUG("[MobileAgent][chat_stream_debug] state=maybe_consolidate_memory");
    // Trigger memory consolidation if needed
    // Note: This is now non-blocking - consolidation runs asynchronously
    // to avoid delaying the user experience after message_end event
    agent_loop_->maybe_consolidate_memory(effective_session_id, new_messages);
    ICRAW_LOG_INFO("[MobileAgent][chat_stream_complete] session_id={} history_count={}",
            effective_session_id, session_history.size());
    icraw::g_android_tools.clear_current_session_id();
}

std::string MobileAgent::build_system_prompt_for_message(const std::string& session_id) const {
    const std::string effective_session_id = session_id.empty() ? "default" : session_id;
    return prompt_builder_->build_full(config_.skills, effective_session_id);
}

void MobileAgent::log_selected_skills(const std::vector<SkillMetadata>& selected_skills) const {
    if (selected_skills.empty()) {
        return;
    }

    size_t total_chars = 0;
    size_t injected_count = 0;
    for (const auto& skill : selected_skills) {
        const size_t skill_chars = skill.content.size() + skill.description.size();
        if (injected_count >= PRELOADED_SKILL_MAX_COUNT) {
            break;
        }
        if (injected_count > 0 && total_chars + skill_chars > PRELOADED_SKILL_MAX_TOTAL_CHARS) {
            break;
        }
        total_chars += skill_chars;
        injected_count++;
    }

    if (injected_count == 0) {
        return;
    }

    std::vector<SkillMetadata> injected_skills(
            selected_skills.begin(),
            selected_skills.begin() + static_cast<long long>(injected_count));
    ICRAW_LOG_INFO(
            "[MobileAgent][skill_preload] selected_count={} total_chars={} skills={}",
            injected_count,
            total_chars,
            join_skill_names(injected_skills));
}

std::vector<SkillMetadata> MobileAgent::select_relevant_skills_for_message(const std::string& message) const {
    const std::string normalized_message = normalize_for_match(message);
    if (normalized_message.empty() || available_skills_.empty()) {
        return {};
    }

    struct ScoredSkill {
        const SkillMetadata* skill = nullptr;
        int score = 0;
        int phrase_score = 0;
        RoutingScore routing;
    };

    std::vector<ScoredSkill> scored_skills;
    scored_skills.reserve(available_skills_.size());

    for (const auto& skill : available_skills_) {
        const auto phrases = collect_skill_match_phrases(skill);
        int phrase_score = 0;
        for (const auto& phrase : phrases) {
            const std::string normalized_phrase = normalize_for_match(phrase);
            if (normalized_phrase.empty()) {
                continue;
            }
            if (normalized_message.find(normalized_phrase) == std::string::npos) {
                continue;
            }
            phrase_score += skill_phrase_match_score(normalized_phrase);
        }

        RoutingScore routing = score_skill_routing(skill, normalized_message);
        if (routing.has_hints && routing.requires_primary_marker && !routing.primary_matched) {
            if (phrase_score > 0 || routing.weak_score > 0) {
                ICRAW_LOG_INFO(
                        "[MobileAgent][skill_routing_filtered] skill={} reason=requires_primary_marker "
                        "phrase_score={} weak_score={} negative_score={}",
                        skill.name,
                        phrase_score,
                        routing.weak_score,
                        routing.negative_score);
            }
            continue;
        }

        const int score = phrase_score + routing.score;

        if (score >= PRELOADED_SKILL_MIN_SCORE) {
            scored_skills.push_back(ScoredSkill{&skill, score, phrase_score, std::move(routing)});
        }
    }

    std::sort(scored_skills.begin(), scored_skills.end(), [](const ScoredSkill& lhs, const ScoredSkill& rhs) {
        if (lhs.score != rhs.score) {
            return lhs.score > rhs.score;
        }
        return lhs.skill->name < rhs.skill->name;
    });

    std::vector<SkillMetadata> selected;
    selected.reserve(std::min(scored_skills.size(), PRELOADED_SKILL_MAX_COUNT));
    for (size_t i = 0; i < scored_skills.size() && selected.size() < PRELOADED_SKILL_MAX_COUNT; ++i) {
        selected.push_back(*scored_skills[i].skill);
    }

    // Companion skills are declared by the primary skill itself, so code stays generic while
    // workflows like "send message + resolve contact" can still preload both skills.
    const size_t selected_before_companions = selected.size();
    for (size_t i = 0; i < selected_before_companions && selected.size() < PRELOADED_SKILL_MAX_COUNT; ++i) {
        const auto companion_skills = json_string_list(
                selected[i].routing_hints, {"companion_skills", "companionSkills"});
        for (const auto& companion_name : companion_skills) {
            if (selected.size() >= PRELOADED_SKILL_MAX_COUNT) {
                break;
            }
            if (has_selected_skill(selected, companion_name)) {
                continue;
            }
            const SkillMetadata* companion = find_available_skill(available_skills_, companion_name);
            if (companion == nullptr) {
                ICRAW_LOG_INFO(
                        "[MobileAgent][skill_routing_companion_missing] source_skill={} companion_skill={}",
                        selected[i].name,
                        companion_name);
                continue;
            }
            selected.push_back(*companion);
            ICRAW_LOG_INFO(
                    "[MobileAgent][skill_routing_companion_added] source_skill={} companion_skill={} score={}",
                    selected[i].name,
                    companion_name,
                    ROUTING_COMPANION_SCORE);
        }
    }

    if (!selected.empty()) {
        ICRAW_LOG_INFO(
                "[MobileAgent][skill_routing_selected] selected_count={} selected_skills={}",
                selected.size(),
                join_skill_names(selected));
    }
    return selected;
}

void MobileAgent::clear_history() {
    history_.clear();
}

void MobileAgent::stop() {
    agent_loop_->stop();
}

std::unique_ptr<MobileAgent> MobileAgent::create(const std::string& workspace_path) {
    IcrawConfig config = IcrawConfig::load_default();
    
    if (!workspace_path.empty()) {
        config.workspace_path = std::filesystem::path(workspace_path);
    }
    
    return std::make_unique<MobileAgent>(config);
}

std::unique_ptr<MobileAgent> MobileAgent::create_with_config(const IcrawConfig& config) {
    return std::make_unique<MobileAgent>(config);
}

} // namespace icraw
