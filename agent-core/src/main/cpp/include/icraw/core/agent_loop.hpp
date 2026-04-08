#pragma once

#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <atomic>
#include <future>
#include "icraw/types.hpp"
#include "icraw/config.hpp"
#include "icraw/core/token_utils.hpp"

namespace icraw {

class LLMProvider;
class MemoryManager;
class SkillLoader;
class ToolRegistry;

class AgentLoop {
public:
    AgentLoop(std::shared_ptr<MemoryManager> memory_manager,
              std::shared_ptr<SkillLoader> skill_loader,
              std::shared_ptr<ToolRegistry> tool_registry,
              std::shared_ptr<LLMProvider> llm_provider,
              const AgentConfig& agent_config);

    // Process a message with externally-provided history and system prompt
    // Returns all new messages generated during the turn
    std::vector<Message> process_message(const std::string& message,
                                         const std::vector<Message>& history,
                                         const std::string& system_prompt);

    // Streaming version
    std::vector<Message> process_message_stream(const std::string& message,
                                                 const std::vector<Message>& history,
                                                 const std::string& system_prompt,
                                                 AgentEventCallback callback);

    // Stop the current agent turn
    void stop();

    // Set max iterations
    void set_max_iterations(int max) { max_iterations_ = max; }

    // Update agent config
    void set_config(const AgentConfig& config);

    // Get current config
    const AgentConfig& get_config() const { return agent_config_; }

    // Trigger memory consolidation if needed (now async)
    void maybe_consolidate_memory(const std::string& session_id,
                                  const std::vector<Message>& messages);
    
    // --- New Memory Management Methods ---
    
    // Check if memory flush should be triggered
    bool should_flush_memory() const;
    
    // Execute memory flush (silent agent turn to save important info)
    bool execute_memory_flush();
    
    // Check if compaction should be triggered (token-based)
    bool should_compact() const;
    
    // Get current context token count
    int get_current_token_count() const;
    
    // Get memory metrics
    MemoryMetrics get_memory_metrics() const;

private:
    std::vector<ContentBlock> handle_tool_calls(
        const std::vector<ToolCall>& tool_calls);
    
    // Perform memory consolidation via LLM
    bool perform_consolidation(const std::string& session_id,
                               const std::vector<Message>& messages);
    
    // Perform compaction with chunking and fallback
    CompactionResult perform_compaction_with_fallback(
        const std::vector<MemoryEntry>& messages);
    
    // Build consolidation prompt with identifier preservation
    std::string build_consolidation_prompt(
        const std::vector<MemoryEntry>& messages,
        const std::string& current_summary) const;
    
    // Prune tool results in context
    void prune_context_tool_results(std::vector<Message>& messages, int max_chars);

    std::shared_ptr<MemoryManager> memory_manager_;
    std::shared_ptr<SkillLoader> skill_loader_;
    std::shared_ptr<ToolRegistry> tool_registry_;
    std::shared_ptr<LLMProvider> llm_provider_;
    AgentConfig agent_config_;
    std::atomic<bool> stop_requested_{false};
    int max_iterations_ = 15;
    std::string last_finish_reason_;
    
    // Async consolidation tracking
    std::future<bool> consolidation_future_;
    
    // Memory flush tracking
    int last_flush_compaction_count_ = -1;  // Prevent duplicate flushes
};

} // namespace icraw
