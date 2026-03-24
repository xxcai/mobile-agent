#include "icraw/mobile_agent.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/tools/tool_registry.hpp"
#include "icraw/core/prompt_builder.hpp"
#include "icraw/core/agent_loop.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/core/http_client.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"

namespace icraw {

static std::string trim_whitespace(const std::string& text) {
    const size_t start = text.find_first_not_of(" \t\n\r");
    if (start == std::string::npos) {
        return "";
    }
    const size_t end = text.find_last_not_of(" \t\n\r");
    return text.substr(start, end - start + 1);
}

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

    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=tool_registry");
    tool_registry_ = std::make_shared<ToolRegistry>();
    tool_registry_->set_base_path(config_.workspace_path.string());
    tool_registry_->set_memory_manager(memory_manager_.get());
    tool_registry_->register_builtin_tools();

    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=prompt_builder");
    prompt_builder_ = std::make_shared<PromptBuilder>(
        memory_manager_, skill_loader_, tool_registry_);

    // Create OpenAI-compatible provider with CurlHttpClient
    ICRAW_LOG_DEBUG("[MobileAgent][initialize_debug] component=llm_provider model={}", config_.agent.model);
    auto provider = std::make_shared<OpenAICompatibleProvider>(
        config_.provider.api_key,
        config_.provider.base_url,
        config_.agent.model);

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
    auto new_messages = agent_loop_->process_message(
        message, history_, system_prompt_);
    
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
        message, session_history, system_prompt_, callback);
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
