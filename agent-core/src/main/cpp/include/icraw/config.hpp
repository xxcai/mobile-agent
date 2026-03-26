#pragma once

#include <string>
#include <memory>
#include <optional>
#include <filesystem>
#include "icraw/types.hpp"

namespace icraw {

// --- Compaction Configuration ---
// Based on OpenClaw best practices for memory optimization

struct MemoryFlushConfig {
    bool enabled = true;
    int soft_threshold_tokens = 4000;  // Buffer before hitting hard limit
    std::string system_prompt;
    std::string user_prompt;
    
    static MemoryFlushConfig from_json(const nlohmann::json& json);
    nlohmann::json to_json() const;
};

struct CompactionConfig {
    // Mode selection
    enum class Mode { Default, Safeguard };
    Mode mode = Mode::Safeguard;
    
    // Token budget
    int context_window_tokens = 128000;    // Model context window size
    int reserve_tokens = 4000;              // Reserve for response generation
    int reserve_tokens_floor = 20000;       // Minimum tokens reserved for new messages
    int keep_recent_tokens = 8000;          // Keep this many tokens of recent messages
    double max_history_share = 0.5;         // History can occupy at most 50% of context
    
    // Identifier preservation policy
    enum class IdentifierPolicy { Strict, Off, Custom };
    IdentifierPolicy identifier_policy = IdentifierPolicy::Strict;
    std::string identifier_instructions;
    
    // Memory Flush configuration
    MemoryFlushConfig memory_flush;
    
    // Compaction strategy
    int max_chunk_tokens = 32000;           // Max tokens per compaction chunk
    int min_messages_for_split = 4;         // Minimum messages before splitting into chunks
    int chunk_parts = 2;                    // Number of chunks to split into
    
    // Compression ratio target
    double target_compression_ratio = 0.15; // Target: 15% of original tokens
    
    static CompactionConfig from_json(const nlohmann::json& json);
    nlohmann::json to_json() const;
};

// --- Context Budget Configuration ---
// Controls how context window is allocated

struct ContextBudget {
    int context_window;          // Model context window (128k)
    int system_prompt_max;       // System prompt upper limit (8k)
    int summary_max;             // History summary upper limit (20k)
    int recent_messages_max;     // Recent messages upper limit (40k)
    int tool_results_max;        // Tool results upper limit (20k)
    int generation_reserve;      // Reserve for generation (40k)
    
    static ContextBudget from_config(const CompactionConfig& config);
    nlohmann::json to_json() const;
};

// --- Agent Configuration ---

struct AgentConfig {
    std::string model = "qwen-max";
    int max_iterations = 15;
    double temperature = 0.7;
    int max_tokens = 4096;
    std::optional<bool> enable_thinking;
    
    // Memory consolidation settings (legacy, use compaction instead)
    int memory_window = 50;        // Keep this many messages in memory
    int consolidation_threshold = 30; // Trigger consolidation when messages exceed this
    
    // New compaction configuration
    CompactionConfig compaction;
    
    static AgentConfig from_json(const nlohmann::json& json);
    nlohmann::json to_json() const;
};

// --- Provider Configuration ---

struct ProviderConfig {
    std::string api_key;
    std::string base_url = "https://api.openai.com/v1";
    int timeout = 30;

    static ProviderConfig from_json(const nlohmann::json& json);
    nlohmann::json to_json() const;
};

// --- Tool Permission Configuration ---

struct ToolPermissionConfig {
    std::vector<std::string> allow;
    std::vector<std::string> deny;

    static ToolPermissionConfig from_json(const nlohmann::json& json);
    nlohmann::json to_json() const;
};

// --- Skills Configuration ---

struct SkillsConfig {
    std::string path;
    std::vector<std::string> auto_approve;
    std::vector<std::string> extra_dirs;

    static SkillsConfig from_json(const nlohmann::json& json);
    nlohmann::json to_json() const;
};

// --- Logging Configuration ---

struct LoggingConfig {
    std::string directory;
    std::string level = "info";
    bool enabled = false;

    static LoggingConfig from_json(const nlohmann::json& json);
    nlohmann::json to_json() const;
};

// --- Main Configuration ---

struct IcrawConfig {
    AgentConfig agent;
    ProviderConfig provider;
    ToolPermissionConfig tools;
    SkillsConfig skills;
    LoggingConfig logging;
    std::filesystem::path workspace_path;

    static IcrawConfig from_json(const nlohmann::json& json);
    static IcrawConfig load_from_file(const std::string& filepath);
    static IcrawConfig load_default();
    
    nlohmann::json to_json() const;
    
    static std::string expand_home(const std::string& path);
    static std::filesystem::path default_workspace_path();
};

} // namespace icraw
