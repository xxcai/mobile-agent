#include "icraw/mobile_agent.hpp"
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
#include <sstream>
#include <unordered_set>

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
        u8"“", u8"”", u8"‘", u8"’", u8"。", u8"，", u8"：", u8"；",
        u8"！", u8"？", u8"（", u8"）", u8"、", u8"《", u8"》"
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
    static const std::array<std::string, 17> generic_phrases = {
        normalize_for_match("用户"),
        normalize_for_match("当前"),
        normalize_for_match("页面"),
        normalize_for_match("内容"),
        normalize_for_match("说明"),
        normalize_for_match("触发条件"),
        normalize_for_match("工作流程"),
        normalize_for_match("决策规则"),
        normalize_for_match("输出要求"),
        normalize_for_match("错误处理"),
        normalize_for_match("注意"),
        normalize_for_match("进入"),
        normalize_for_match("查看"),
        normalize_for_match("继续"),
        normalize_for_match("总结"),
        normalize_for_match("skill"),
        normalize_for_match("agent")
    };
    return std::find(generic_phrases.begin(), generic_phrases.end(), normalized_phrase) != generic_phrases.end();
}

std::vector<std::string> extract_quoted_phrases(const std::string& text) {
    static const std::array<std::pair<std::string, std::string>, 3> quote_pairs = {{
        {"\"", "\""},
        {u8"“", u8"”"},
        {u8"‘", u8"’"}
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
        "\n", u8"。", u8"，", u8"：", u8"；", u8"！", u8"？", u8"（", u8"）",
        u8"、", ",", ".", ":", ";", "!"
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

    return phrases;
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
    // 只展示最终结果：
    // 如果 assistant 后面紧跟 tool，说明它只是“准备调用工具”的过渡话术，
    // 应该只在流式过程中临时展示，不应该进入历史消息，否则会拆成两张卡片。
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

    // Build system prompt (passing skills config from user config)
    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=system_prompt");
    system_prompt_ = prompt_builder_->build_full(config_.skills);

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
    const auto selected_skills = select_relevant_skills_for_message(message);
    log_selected_skills(selected_skills);
    const std::string runtime_prompt = build_system_prompt_for_message();
    auto new_messages = agent_loop_->process_message(
        message, history_, runtime_prompt, selected_skills);
    
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
    const std::string runtime_prompt = build_system_prompt_for_message();

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
                // Skip thinking content - it's internal reasoning, not actual response
                if (block.type == "thinking") {
                    continue;
                }
                if (!block.text.empty()) {
                    content += block.text + " ";
                }
            }
            // 这里必须先 trim 再决定是否落库：
            // 某些 assistant 回合只有 think + 工具调用，中间夹带的 text block 可能只剩换行/空格。
            // 如果直接用 !content.empty() 判断，会把“看起来空白”的伪消息写进 messages 表，
            // 历史加载后就会出现一条空白消息卡片。
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
    agent_loop_->maybe_consolidate_memory(new_messages);
    ICRAW_LOG_INFO("[MobileAgent][chat_stream_complete] session_id={} history_count={}",
            effective_session_id, session_history.size());
}

std::string MobileAgent::build_system_prompt_for_message() const {
    return system_prompt_;
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
    };

    std::vector<ScoredSkill> scored_skills;
    scored_skills.reserve(available_skills_.size());

    for (const auto& skill : available_skills_) {
        const auto phrases = collect_skill_match_phrases(skill);
        int score = 0;
        for (const auto& phrase : phrases) {
            const std::string normalized_phrase = normalize_for_match(phrase);
            if (normalized_phrase.empty()) {
                continue;
            }
            if (normalized_message.find(normalized_phrase) == std::string::npos) {
                continue;
            }
            score += std::min<int>(static_cast<int>(normalized_phrase.size()), 48);
        }

        if (score >= PRELOADED_SKILL_MIN_SCORE) {
            scored_skills.push_back(ScoredSkill{&skill, score});
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
    for (size_t i = 0; i < scored_skills.size() && i < PRELOADED_SKILL_MAX_COUNT; ++i) {
        selected.push_back(*scored_skills[i].skill);
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
