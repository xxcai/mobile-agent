#pragma once

#include <string>
#include <vector>
#include "icraw/types.hpp"
#include "icraw/config.hpp"

namespace icraw {

// Forward declaration
struct MemoryEntry;

// ============================================================================
// Token Estimation Utilities
// Based on OpenClaw best practices with 20% safety margin
// ============================================================================

// Safety margin for token estimation (20% buffer)
constexpr double TOKEN_SAFETY_MARGIN = 1.2;

// Average characters per token for mixed Chinese/English content
// Chinese: ~1.5 chars/token, English: ~4 chars/token
// Mixed content: ~2.5 chars/token as average
constexpr double CHARS_PER_TOKEN = 2.5;

// Overhead tokens per message (role, formatting, etc.)
constexpr int MESSAGE_OVERHEAD_TOKENS = 4;

// Overhead tokens per tool call
constexpr int TOOL_CALL_OVERHEAD_TOKENS = 10;

// Overhead tokens for the entire request
constexpr int REQUEST_OVERHEAD_TOKENS = 3;

/// Estimate token count for a single text string
/// Uses character-based estimation with safety margin
/// @param text The text to estimate
/// @return Estimated token count with 20% safety margin
int estimate_tokens(const std::string& text);

/// Estimate token count for a single message
/// Includes content, tool calls, and overhead
/// @param msg The message to estimate
/// @return Estimated token count with safety margin
int estimate_message_tokens(const Message& msg);

/// Estimate token count for a vector of messages
/// @param messages The messages to estimate
/// @return Total estimated token count with safety margin
int estimate_messages_tokens(const std::vector<Message>& messages);

/// Estimate token count for MemoryEntry (from database)
/// @param entry The memory entry to estimate
/// @return Estimated token count
int estimate_memory_entry_tokens(const MemoryEntry& entry);

/// Estimate token count for a vector of MemoryEntry
/// @param entries The memory entries to estimate
/// @return Total estimated token count
int estimate_memory_entries_tokens(const std::vector<MemoryEntry>& entries);

// ============================================================================
// Context Budget Utilities
// ============================================================================

/// Check if memory flush should be triggered
/// @param current_tokens Current token count in context
/// @param config Compaction configuration
/// @return true if memory flush should run
bool should_trigger_memory_flush(int current_tokens, const CompactionConfig& config);

/// Check if compaction should be triggered
/// @param current_tokens Current token count in context
/// @param config Compaction configuration
/// @return true if compaction should run
bool should_trigger_compaction(int current_tokens, const CompactionConfig& config);

/// Calculate available context space
/// @param current_tokens Current token count
/// @param config Compaction configuration
/// @return Available tokens for new content
int calculate_available_context(int current_tokens, const CompactionConfig& config);

// ============================================================================
// Tool Result Pruning
// ============================================================================

/// Prune tool result to fit within max characters
/// Keeps front 2/3 and back 1/3 with truncation marker
/// @param result The tool result string
/// @param max_chars Maximum characters to keep
/// @return Pruned result with truncation marker if needed
std::string prune_tool_result(const std::string& result, int max_chars = 40000);

// ============================================================================
// Message Chunking
// ============================================================================

/// Chunk messages by token count for compaction
/// @param messages Messages to chunk
/// @param max_tokens_per_chunk Maximum tokens per chunk
/// @return Vector of message chunks
std::vector<std::vector<MemoryEntry>> chunk_messages_by_tokens(
    const std::vector<MemoryEntry>& messages,
    int max_tokens_per_chunk
);

// ============================================================================
// Identifier Preservation
// ============================================================================

/// Get the identifier preservation prompt addition
/// @param policy The identifier policy
/// @return Prompt string to add to consolidation prompts
std::string get_identifier_preservation_prompt(CompactionConfig::IdentifierPolicy policy);

/// Check if text contains important identifiers
/// @param text Text to check
/// @return true if text contains UUIDs, paths, URLs, etc.
bool contains_important_identifiers(const std::string& text);

// ============================================================================
// Compaction Result Types
// ============================================================================

enum class CompactionResult {
    Success,           // Full compression succeeded
    PartialSuccess,    // Partial compression (excluded oversized messages)
    Fallback,          // Degraded: only recorded metadata
    Failed             // Complete failure
};

/// Convert CompactionResult to string
std::string compaction_result_to_string(CompactionResult result);

// ============================================================================
// Memory Metrics
// ============================================================================

struct MemoryMetrics {
    // Storage metrics
    int64_t total_messages = 0;
    int64_t consolidated_messages = 0;
    
    // Token metrics
    int context_tokens = 0;
    int summary_tokens = 0;
    int recent_messages_tokens = 0;
    
    // Compaction metrics
    int compaction_count = 0;
    int flush_count = 0;
    double compression_ratio = 0.0;
    
    nlohmann::json to_json() const;
};

} // namespace icraw
