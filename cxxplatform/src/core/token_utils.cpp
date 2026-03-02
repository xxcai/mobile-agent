#include "icraw/core/token_utils.hpp"
#include "icraw/core/memory_manager.hpp"
#include <algorithm>
#include <sstream>
#include <regex>

namespace icraw {

// ============================================================================
// Token Estimation
// ============================================================================

int estimate_tokens(const std::string& text) {
    if (text.empty()) {
        return 0;
    }
    
    // Simple estimation: characters / average chars per token
    // With safety margin for robustness
    int base_estimate = static_cast<int>(text.size() / CHARS_PER_TOKEN);
    
    // Apply 20% safety margin
    return static_cast<int>(base_estimate * TOKEN_SAFETY_MARGIN);
}

int estimate_message_tokens(const Message& msg) {
    int total = MESSAGE_OVERHEAD_TOKENS;
    
    // Add role tokens
    total += estimate_tokens(msg.role);
    
    // Add content tokens
    for (const auto& block : msg.content) {
        if (block.type == "text" || block.type == "thinking") {
            total += estimate_tokens(block.text);
        } else if (block.type == "tool_use") {
            total += TOOL_CALL_OVERHEAD_TOKENS;
            total += estimate_tokens(block.name);
            total += estimate_tokens(block.input.dump());
        } else if (block.type == "tool_result") {
            total += estimate_tokens(block.content);
        } else if (block.type == "image_url") {
            // Images typically cost ~85-170 tokens depending on detail
            total += (block.image_detail == "high") ? 170 : 85;
        }
    }
    
    // Add tool_calls tokens (OpenAI format)
    for (const auto& tc : msg.tool_calls) {
        total += TOOL_CALL_OVERHEAD_TOKENS;
        total += estimate_tokens(tc.function_name);
        total += estimate_tokens(tc.function_arguments);
    }
    
    // Add tool_call_id tokens
    if (!msg.tool_call_id.empty()) {
        total += estimate_tokens(msg.tool_call_id);
    }
    
    return static_cast<int>(total * TOKEN_SAFETY_MARGIN);
}

int estimate_messages_tokens(const std::vector<Message>& messages) {
    int total = REQUEST_OVERHEAD_TOKENS;
    
    for (const auto& msg : messages) {
        total += estimate_message_tokens(msg);
    }
    
    return total;
}

int estimate_memory_entry_tokens(const MemoryEntry& entry) {
    int total = MESSAGE_OVERHEAD_TOKENS;
    total += estimate_tokens(entry.role);
    total += estimate_tokens(entry.content);
    total += estimate_tokens(entry.metadata.dump());
    return static_cast<int>(total * TOKEN_SAFETY_MARGIN);
}

int estimate_memory_entries_tokens(const std::vector<MemoryEntry>& entries) {
    int total = 0;
    for (const auto& entry : entries) {
        total += estimate_memory_entry_tokens(entry);
    }
    return total;
}

// ============================================================================
// Context Budget Utilities
// ============================================================================

bool should_trigger_memory_flush(int current_tokens, const CompactionConfig& config) {
    if (!config.memory_flush.enabled) {
        return false;
    }
    
    // Calculate threshold: context_window - reserve_floor - soft_threshold
    int threshold = config.context_window_tokens - config.reserve_tokens_floor 
                  - config.memory_flush.soft_threshold_tokens;
    
    return current_tokens >= threshold;
}

bool should_trigger_compaction(int current_tokens, const CompactionConfig& config) {
    // Calculate hard limit: context_window - reserve_tokens_floor
    int hard_limit = config.context_window_tokens - config.reserve_tokens_floor;
    
    return current_tokens >= hard_limit;
}

int calculate_available_context(int current_tokens, const CompactionConfig& config) {
    int available = config.context_window_tokens - config.reserve_tokens - current_tokens;
    return std::max(0, available);
}

// ============================================================================
// Tool Result Pruning
// ============================================================================

std::string prune_tool_result(const std::string& result, int max_chars) {
    if (static_cast<int>(result.size()) <= max_chars) {
        return result;
    }
    
    // Keep front 2/3 and back 1/3
    int keep_front = max_chars * 2 / 3;
    int keep_back = max_chars / 3;
    
    std::string pruned = result.substr(0, keep_front);
    pruned += "\n\n... [truncated " + std::to_string(result.size() - max_chars) 
            + " characters] ...\n\n";
    pruned += result.substr(result.size() - keep_back);
    
    return pruned;
}

// ============================================================================
// Message Chunking
// ============================================================================

std::vector<std::vector<MemoryEntry>> chunk_messages_by_tokens(
    const std::vector<MemoryEntry>& messages,
    int max_tokens_per_chunk
) {
    std::vector<std::vector<MemoryEntry>> chunks;
    std::vector<MemoryEntry> current_chunk;
    int current_tokens = 0;
    
    // Apply safety margin to max tokens
    int effective_max = static_cast<int>(max_tokens_per_chunk / TOKEN_SAFETY_MARGIN);
    
    for (const auto& msg : messages) {
        int msg_tokens = estimate_memory_entry_tokens(msg);
        
        // If adding this message would exceed the limit, start a new chunk
        if (!current_chunk.empty() && current_tokens + msg_tokens > effective_max) {
            chunks.push_back(std::move(current_chunk));
            current_chunk.clear();
            current_tokens = 0;
        }
        
        current_chunk.push_back(msg);
        current_tokens += msg_tokens;
    }
    
    // Don't forget the last chunk
    if (!current_chunk.empty()) {
        chunks.push_back(std::move(current_chunk));
    }
    
    return chunks;
}

// ============================================================================
// Identifier Preservation
// ============================================================================

std::string get_identifier_preservation_prompt(CompactionConfig::IdentifierPolicy policy) {
    if (policy == CompactionConfig::IdentifierPolicy::Off) {
        return "";
    }
    
    if (policy == CompactionConfig::IdentifierPolicy::Custom) {
        // Custom instructions should be provided via config
        return "";
    }
    
    // Strict policy (default)
    return R"(
CRITICAL REQUIREMENTS:
1. Preserve ALL opaque identifiers EXACTLY as written:
   - UUIDs, hashes, file paths, URLs
   - API keys, tokens, session IDs
   - Hostnames, IP addresses, ports
   - Error codes and exact error messages

2. Preserve ALL decisions and their rationale:
   - What was decided and why
   - Who made the decision
   - Any constraints or trade-offs

3. Preserve ALL pending tasks:
   - TODOs, reminders, follow-ups
   - Dependencies and blockers

4. Use [YYYY-MM-DD HH:MM] timestamps for all entries
)";
}

bool contains_important_identifiers(const std::string& text) {
    // Regex patterns for common identifiers
    static const std::vector<std::regex> patterns = {
        // UUID
        std::regex(R"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"),
        // File paths (Unix and Windows)
        std::regex(R"((?:/[\w.-]+)+|(?:[A-Za-z]:\\[\w.-]+(?:\\[\w.-]+)*))"),
        // URLs
        std::regex(R"(https?://[^\s]+)"),
        // IP addresses
        std::regex(R"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})"),
        // Email addresses
        std::regex(R"([\w.-]+@[\w.-]+\.\w+)"),
        // API keys (common patterns)
        std::regex(R"((?:api[_-]?key|token|secret|password)[\s:=]+['"]?[\w-]+['"]?)", 
                   std::regex_constants::icase),
        // SHA hashes
        std::regex(R"(\b[0-9a-fA-F]{40,}\b)"),
        // Session IDs
        std::regex(R"(sess_[a-zA-Z0-9]+|session[_-]?id[\s:=]+['"]?[\w-]+)", 
                   std::regex_constants::icase)
    };
    
    for (const auto& pattern : patterns) {
        if (std::regex_search(text, pattern)) {
            return true;
        }
    }
    
    return false;
}

// ============================================================================
// Compaction Result
// ============================================================================

std::string compaction_result_to_string(CompactionResult result) {
    switch (result) {
        case CompactionResult::Success: return "success";
        case CompactionResult::PartialSuccess: return "partial_success";
        case CompactionResult::Fallback: return "fallback";
        case CompactionResult::Failed: return "failed";
        default: return "unknown";
    }
}

// ============================================================================
// Memory Metrics
// ============================================================================

nlohmann::json MemoryMetrics::to_json() const {
    return {
        {"totalMessages", total_messages},
        {"consolidatedMessages", consolidated_messages},
        {"contextTokens", context_tokens},
        {"summaryTokens", summary_tokens},
        {"recentMessagesTokens", recent_messages_tokens},
        {"compactionCount", compaction_count},
        {"flushCount", flush_count},
        {"compressionRatio", compression_ratio}
    };
}

} // namespace icraw
