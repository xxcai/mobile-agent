#include "icraw/mobile_agent.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/tools/tool_registry.hpp"
#include "icraw/core/prompt_builder.hpp"
#include "icraw/core/agent_loop.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/core/http_client.hpp"
#include "icraw/core/logger.hpp"

namespace icraw {

// MobileAgent implementation
MobileAgent::MobileAgent(const IcrawConfig& config)
    : config_(config) {

    // Initialize logger if configured
    if (config_.logging.enabled && !config_.logging.directory.empty()) {
        Logger::get_instance().init(config_.logging.directory, config_.logging.level);
    }

    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating workspace at {}", config_.workspace_path.string());

    // Ensure workspace exists
    try {
        std::filesystem::create_directories(config_.workspace_path);
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[AGENT] Failed to create workspace directory: {}", e.what());
        throw;
    }

    // Initialize components
    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating MemoryManager");
    memory_manager_ = std::make_shared<MemoryManager>(config_.workspace_path);

    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating SkillLoader");
    skill_loader_ = std::make_shared<SkillLoader>();

    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating ToolRegistry");
    tool_registry_ = std::make_shared<ToolRegistry>();
    tool_registry_->set_base_path(config_.workspace_path.string());
    tool_registry_->set_memory_manager(memory_manager_.get());
    tool_registry_->register_builtin_tools();

    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating PromptBuilder");
    prompt_builder_ = std::make_shared<PromptBuilder>(
        memory_manager_, skill_loader_, tool_registry_);

    // Create OpenAI-compatible provider with CurlHttpClient
    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating OpenAICompatibleProvider (model: {})", config_.agent.model);
    auto provider = std::make_shared<OpenAICompatibleProvider>(
        config_.provider.api_key,
        config_.provider.base_url,
        config_.agent.model);

    // Create and set HTTP client
    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating CurlHttpClient");
    auto http_client = std::make_unique<CurlHttpClient>();
    provider->set_http_client(std::move(http_client));

    llm_provider_ = provider;

    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Creating AgentLoop");
    agent_loop_ = std::make_unique<AgentLoop>(
        memory_manager_, skill_loader_, tool_registry_,
        llm_provider_, config_.agent);

    // Load conversation history from database
    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Loading history from memory");
    load_history_from_memory();

    // Build system prompt (passing skills config from user config)
    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Building system prompt");
    system_prompt_ = prompt_builder_->build_full(config_.skills);

    ICRAW_LOG_DEBUG("[AGENT] MobileAgent: Initialization complete");
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
    
    ICRAW_LOG_DEBUG("[AGENT] Loaded {} messages from memory into history", history_.size());
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
    ICRAW_LOG_DEBUG("[CHAT_STREAM] Starting process_message_stream");
    auto new_messages = agent_loop_->process_message_stream(
        message, history_, system_prompt_, callback);
    ICRAW_LOG_DEBUG("[CHAT_STREAM] process_message_stream returned, messages={}", new_messages.size());
    
    // Add new messages to history and memory
    for (const auto& msg : new_messages) {
        history_.push_back(msg);
        
        // Save to memory manager
        if (!msg.content.empty()) {
            std::string content;
            for (const auto& block : msg.content) {
                if (!block.text.empty()) {
                    content += block.text + " ";
                }
            }
            if (!content.empty()) {
                nlohmann::json metadata;
                // Store tool calls if present
                if (!msg.content.empty() && msg.content[0].type == "tool_use") {
                    metadata["is_tool_call"] = true;
                }
                memory_manager_->add_message(msg.role, content, "default", metadata);
            }
        }
    }
    
    ICRAW_LOG_DEBUG("[CHAT_STREAM] Starting maybe_consolidate_memory");
    // Trigger memory consolidation if needed
    // Note: This is now non-blocking - consolidation runs asynchronously
    // to avoid delaying the user experience after message_end event
    agent_loop_->maybe_consolidate_memory(new_messages);
    ICRAW_LOG_DEBUG("[CHAT_STREAM] chat_stream completed");
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
