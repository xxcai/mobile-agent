#pragma once

#include <string>
#include <vector>
#include <memory>
#include <functional>
#include "icraw/types.hpp"
#include "icraw/config.hpp"

namespace icraw {

class MemoryManager;
class SkillLoader;
class ToolRegistry;
class PromptBuilder;
class AgentLoop;
class LLMProvider;

// Mobile Agent - Simplified facade for mobile platforms
// Single session, no gateway, no shell commands
class MobileAgent {
public:
    explicit MobileAgent(const IcrawConfig& config);
    ~MobileAgent();

    // Send a message and get response
    std::string chat(const std::string& message);

    // Send a message with streaming callback
    void chat_stream(const std::string& message, AgentEventCallback callback);

    // Get conversation history
    const std::vector<Message>& get_history() const { return history_; }

    // Clear conversation history
    void clear_history();

    // Stop current operation
    void stop();

    // Get underlying components (for advanced usage)
    std::shared_ptr<MemoryManager> get_memory_manager() const { return memory_manager_; }
    std::shared_ptr<ToolRegistry> get_tool_registry() const { return tool_registry_; }
    std::shared_ptr<LLMProvider> get_llm_provider() const { return llm_provider_; }

    // Factory method with default configuration
    static std::unique_ptr<MobileAgent> create(const std::string& workspace_path = "");
    
    // Factory method with custom configuration
    static std::unique_ptr<MobileAgent> create_with_config(const IcrawConfig& config);

private:
    // Load conversation history from database
    void load_history_from_memory();
    
    IcrawConfig config_;
    std::shared_ptr<MemoryManager> memory_manager_;
    std::shared_ptr<SkillLoader> skill_loader_;
    std::shared_ptr<ToolRegistry> tool_registry_;
    std::shared_ptr<PromptBuilder> prompt_builder_;
    std::shared_ptr<LLMProvider> llm_provider_;
    std::unique_ptr<AgentLoop> agent_loop_;

    std::vector<Message> history_;
    std::string system_prompt_;
};

} // namespace icraw
