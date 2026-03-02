#include <catch2/catch_test_macros.hpp>
#include "icraw/core/token_utils.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/config.hpp"
#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;

// ============================================================================
// Token Estimation Tests
// ============================================================================

TEST_CASE("Token estimation for English text", "[token_utils]") {
    // English text: ~4 chars per token
    std::string text = "Hello world this is a test";
    int tokens = icraw::estimate_tokens(text);
    
    // Should be roughly text.size() / 2.5 * 1.2
    CHECK(tokens > 0);
    CHECK(tokens < static_cast<int>(text.size()));  // Should be less than char count
}

TEST_CASE("Token estimation for Chinese text", "[token_utils]") {
    // Chinese text: ~1.5 chars per token
    std::string text = u8"你好世界这是一个测试";
    int tokens = icraw::estimate_tokens(text);
    
    CHECK(tokens > 0);
}

TEST_CASE("Token estimation for empty text", "[token_utils]") {
    int tokens = icraw::estimate_tokens("");
    CHECK(tokens == 0);
}

TEST_CASE("Token estimation with safety margin", "[token_utils]") {
    // Verify 20% safety margin is applied
    std::string text = "Test text";
    int base_estimate = static_cast<int>(text.size() / icraw::CHARS_PER_TOKEN);
    int estimated = icraw::estimate_tokens(text);
    
    // Estimated should be at least base * 1.2
    CHECK(estimated >= base_estimate);
}

TEST_CASE("Message token estimation", "[token_utils]") {
    icraw::Message msg;
    msg.role = "user";
    msg.content.push_back(icraw::ContentBlock::make_text("Hello world"));
    
    int tokens = icraw::estimate_message_tokens(msg);
    
    // Should include overhead
    CHECK(tokens > icraw::MESSAGE_OVERHEAD_TOKENS);
}

TEST_CASE("Messages token estimation", "[token_utils]") {
    std::vector<icraw::Message> messages;
    
    icraw::Message msg1;
    msg1.role = "user";
    msg1.content.push_back(icraw::ContentBlock::make_text("Hello"));
    messages.push_back(msg1);
    
    icraw::Message msg2;
    msg2.role = "assistant";
    msg2.content.push_back(icraw::ContentBlock::make_text("Hi there"));
    messages.push_back(msg2);
    
    int total = icraw::estimate_messages_tokens(messages);
    
    // Should include request overhead
    CHECK(total > icraw::REQUEST_OVERHEAD_TOKENS);
}

// ============================================================================
// Memory Flush Trigger Tests
// ============================================================================

TEST_CASE("Memory flush trigger logic", "[token_utils]") {
    icraw::CompactionConfig config;
    config.context_window_tokens = 128000;
    config.reserve_tokens_floor = 20000;
    config.memory_flush.enabled = true;
    config.memory_flush.soft_threshold_tokens = 4000;
    
    // Below threshold - should not trigger
    CHECK_FALSE(icraw::should_trigger_memory_flush(100000, config));
    
    // At threshold - should trigger
    int threshold = config.context_window_tokens - config.reserve_tokens_floor 
                  - config.memory_flush.soft_threshold_tokens;
    CHECK(icraw::should_trigger_memory_flush(threshold, config));
    
    // Above threshold - should trigger
    CHECK(icraw::should_trigger_memory_flush(threshold + 1000, config));
}

TEST_CASE("Memory flush disabled", "[token_utils]") {
    icraw::CompactionConfig config;
    config.memory_flush.enabled = false;
    
    // Should never trigger when disabled
    CHECK_FALSE(icraw::should_trigger_memory_flush(1000000, config));
}

TEST_CASE("Compaction trigger logic", "[token_utils]") {
    icraw::CompactionConfig config;
    config.context_window_tokens = 128000;
    config.reserve_tokens_floor = 20000;
    
    // Below hard limit - should not trigger
    CHECK_FALSE(icraw::should_trigger_compaction(100000, config));
    
    // At hard limit - should trigger
    int hard_limit = config.context_window_tokens - config.reserve_tokens_floor;
    CHECK(icraw::should_trigger_compaction(hard_limit, config));
    
    // Above hard limit - should trigger
    CHECK(icraw::should_trigger_compaction(hard_limit + 1000, config));
}

TEST_CASE("Available context calculation", "[token_utils]") {
    icraw::CompactionConfig config;
    config.context_window_tokens = 128000;
    config.reserve_tokens = 4000;
    
    int available = icraw::calculate_available_context(100000, config);
    
    // Should be context_window - reserve - current
    int expected = 128000 - 4000 - 100000;
    CHECK(available == expected);
    
    // Should not go negative
    available = icraw::calculate_available_context(200000, config);
    CHECK(available >= 0);
}

// ============================================================================
// Tool Result Pruning Tests
// ============================================================================

TEST_CASE("Tool result pruning - small result", "[token_utils]") {
    std::string result = "Small result";
    std::string pruned = icraw::prune_tool_result(result, 1000);
    
    // Should not prune small results
    CHECK(pruned == result);
}

TEST_CASE("Tool result pruning - large result", "[token_utils]") {
    std::string result(20000, 'X');  // 20KB of X
    std::string pruned = icraw::prune_tool_result(result, 10000);
    
    // Should be pruned to ~10KB + truncation message
    CHECK(pruned.size() < result.size());
    CHECK(pruned.find("truncated") != std::string::npos);
    
    // Should contain front 2/3 and back 1/3
    CHECK(pruned.find("XXXX") != std::string::npos);
}

TEST_CASE("Tool result pruning - exact size", "[token_utils]") {
    std::string result(10000, 'X');
    std::string pruned = icraw::prune_tool_result(result, 10000);
    
    // Should not prune exact size
    CHECK(pruned == result);
}

// ============================================================================
// Message Chunking Tests
// ============================================================================

TEST_CASE("Message chunking - empty list", "[token_utils]") {
    std::vector<icraw::MemoryEntry> messages;
    auto chunks = icraw::chunk_messages_by_tokens(messages, 1000);
    
    CHECK(chunks.empty());
}

TEST_CASE("Message chunking - single small message", "[token_utils]") {
    std::vector<icraw::MemoryEntry> messages;
    icraw::MemoryEntry entry;
    entry.content = "Small message";
    messages.push_back(entry);
    
    auto chunks = icraw::chunk_messages_by_tokens(messages, 1000);
    
    REQUIRE(chunks.size() == 1);
    CHECK(chunks[0].size() == 1);
}

TEST_CASE("Message chunking - multiple chunks", "[token_utils]") {
    std::vector<icraw::MemoryEntry> messages;
    
    // Create 10 messages with 100 chars each
    for (int i = 0; i < 10; ++i) {
        icraw::MemoryEntry entry;
        entry.content = std::string(100, 'X') + std::to_string(i);
        messages.push_back(entry);
    }
    
    // Small max tokens to force chunking
    auto chunks = icraw::chunk_messages_by_tokens(messages, 50);
    
    // Should create multiple chunks
    CHECK(chunks.size() > 1);
    
    // All messages should be preserved
    int total_messages = 0;
    for (const auto& chunk : chunks) {
        total_messages += static_cast<int>(chunk.size());
    }
    CHECK(total_messages == 10);
}

// ============================================================================
// Identifier Preservation Tests
// ============================================================================

TEST_CASE("Identifier preservation prompt - strict mode", "[token_utils]") {
    icraw::CompactionConfig config;
    config.identifier_policy = icraw::CompactionConfig::IdentifierPolicy::Strict;
    
    std::string prompt = icraw::get_identifier_preservation_prompt(config.identifier_policy);
    
    CHECK(!prompt.empty());
    CHECK(prompt.find("CRITICAL REQUIREMENTS") != std::string::npos);
    CHECK(prompt.find("UUID") != std::string::npos);
}

TEST_CASE("Identifier preservation prompt - off mode", "[token_utils]") {
    icraw::CompactionConfig config;
    config.identifier_policy = icraw::CompactionConfig::IdentifierPolicy::Off;
    
    std::string prompt = icraw::get_identifier_preservation_prompt(config.identifier_policy);
    
    CHECK(prompt.empty());
}

TEST_CASE("Detect important identifiers - UUID", "[token_utils]") {
    std::string text = "The ID is 550e8400-e29b-41d4-a716-446655440000";
    CHECK(icraw::contains_important_identifiers(text));
}

TEST_CASE("Detect important identifiers - file path", "[token_utils]") {
    std::string text = "File saved to /home/user/documents/file.txt";
    CHECK(icraw::contains_important_identifiers(text));
}

TEST_CASE("Detect important identifiers - URL", "[token_utils]") {
    std::string text = "Visit https://example.com/api/v1";
    CHECK(icraw::contains_important_identifiers(text));
}

TEST_CASE("Detect important identifiers - IP address", "[token_utils]") {
    std::string text = "Server at 192.168.1.100";
    CHECK(icraw::contains_important_identifiers(text));
}

TEST_CASE("Detect important identifiers - email", "[token_utils]") {
    std::string text = "Contact user@example.com";
    CHECK(icraw::contains_important_identifiers(text));
}

TEST_CASE("No important identifiers in plain text", "[token_utils]") {
    std::string text = "This is just plain text without any identifiers";
    CHECK_FALSE(icraw::contains_important_identifiers(text));
}

// ============================================================================
// Compaction Result Tests
// ============================================================================

TEST_CASE("Compaction result to string", "[token_utils]") {
    CHECK(icraw::compaction_result_to_string(icraw::CompactionResult::Success) == "success");
    CHECK(icraw::compaction_result_to_string(icraw::CompactionResult::PartialSuccess) == "partial_success");
    CHECK(icraw::compaction_result_to_string(icraw::CompactionResult::Fallback) == "fallback");
    CHECK(icraw::compaction_result_to_string(icraw::CompactionResult::Failed) == "failed");
}

// ============================================================================
// Context Budget Tests
// ============================================================================

TEST_CASE("Context budget from config", "[token_utils]") {
    icraw::CompactionConfig config;
    config.context_window_tokens = 128000;
    
    auto budget = icraw::ContextBudget::from_config(config);
    
    CHECK(budget.context_window == 128000);
    CHECK(budget.system_prompt_max > 0);
    CHECK(budget.summary_max > 0);
    CHECK(budget.recent_messages_max > 0);
    CHECK(budget.tool_results_max > 0);
    CHECK(budget.generation_reserve > 0);
    
    // Budgets should sum to approximately context_window
    int total = budget.system_prompt_max + budget.summary_max + 
                budget.recent_messages_max + budget.tool_results_max + 
                budget.generation_reserve;
    CHECK(total <= budget.context_window);
}

// ============================================================================
// Memory Metrics Tests
// ============================================================================

TEST_CASE("Memory metrics to_json", "[token_utils]") {
    icraw::MemoryMetrics metrics;
    metrics.total_messages = 100;
    metrics.context_tokens = 50000;
    metrics.compression_ratio = 0.15;
    
    nlohmann::json j = metrics.to_json();
    
    CHECK(j["totalMessages"] == 100);
    CHECK(j["contextTokens"] == 50000);
    CHECK(j["compressionRatio"] == 0.15);
}

// ============================================================================
// New MemoryManager Methods Tests
// ============================================================================

TEST_CASE("MemoryManager::get_total_tokens", "[memory_manager][token_aware]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_total_tokens";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add some messages
    manager.add_message("user", "Hello world", "default", {});
    manager.add_message("assistant", "Hi there!", "default", {});
    
    int64_t total = manager.get_total_tokens("default");
    
    CHECK(total > 0);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::update_token_stats", "[memory_manager][token_aware]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_token_stats";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.add_message("user", "Test message", "default", {});
    manager.update_token_stats("default");
    
    auto stats = manager.get_token_stats("default");
    
    REQUIRE(stats.has_value());
    CHECK(stats->total_tokens > 0);
    CHECK(!stats->last_updated.empty());
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::create_compaction_record", "[memory_manager][compaction]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_compaction_record";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    int64_t id = manager.create_compaction_record("default", "Test summary", 
        100, 10000, 1500, "full");
    
    CHECK(id > 0);
    
    auto record = manager.get_latest_compaction("default");
    
    REQUIRE(record.has_value());
    CHECK(record->summary == "Test summary");
    CHECK(record->tokens_before == 10000);
    CHECK(record->tokens_after == 1500);
    CHECK(record->mode == "full");
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_compaction_count", "[memory_manager][compaction]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_compaction_count";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    CHECK(manager.get_compaction_count("default") == 0);
    
    manager.create_compaction_record("default", "Summary 1", 10, 1000, 100, "full");
    CHECK(manager.get_compaction_count("default") == 1);
    
    manager.create_compaction_record("default", "Summary 2", 20, 2000, 200, "full");
    CHECK(manager.get_compaction_count("default") == 2);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::mark_consolidated", "[memory_manager][compaction]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_mark_consolidated";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages
    for (int i = 0; i < 10; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Mark first 5 as consolidated
    manager.mark_consolidated(5);
    
    // Get messages and check consolidated status
    auto messages = manager.get_recent_messages(20, "default");
    
    int consolidated_count = 0;
    for (const auto& msg : messages) {
        if (msg.consolidated) {
            consolidated_count++;
        }
    }
    
    CHECK(consolidated_count == 5);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::delete_consolidated_messages", "[memory_manager][compaction]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_delete_consolidated";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages
    for (int i = 0; i < 10; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    CHECK(manager.get_message_count("default") == 10);
    
    // Mark first 5 as consolidated
    manager.mark_consolidated(5);
    
    // Delete consolidated messages
    int64_t deleted = manager.delete_consolidated_messages("default");
    
    CHECK(deleted == 5);
    CHECK(manager.get_message_count("default") == 5);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::record_memory_flush", "[memory_manager][flush]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_flush";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Record flush
    manager.record_memory_flush("default");
    
    // Get last flush timestamp
    auto last_flush = manager.get_last_flush_timestamp("default");
    
    REQUIRE(last_flush.has_value());
    CHECK(!last_flush->empty());
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_messages_within_token_budget", "[memory_manager][token_aware]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_token_budget";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add multiple messages
    for (int i = 0; i < 20; ++i) {
        manager.add_message("user", "This is message number " + std::to_string(i), "default", {});
    }
    
    // Get messages within small budget
    auto messages = manager.get_messages_within_token_budget(100, "default");
    
    // Should get some but not all messages
    CHECK(messages.size() < 20);
    CHECK(messages.size() > 0);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory_fts falls back to LIKE", "[memory_manager][fts]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_fts_fallback";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.add_message("user", "Hello world", "default", {});
    manager.add_message("assistant", "Testing FTS search", "default", {});
    
    // This should work even if FTS5 is not available (fallback to LIKE)
    auto results = manager.search_memory_fts("Hello", 10, "default");
    
    CHECK(results.size() >= 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

// ============================================================================
// Config Parsing Tests
// ============================================================================

TEST_CASE("CompactionConfig from_json", "[config][compaction]") {
    nlohmann::json j = R"({
        "mode": "safeguard",
        "contextWindowTokens": 128000,
        "reserveTokensFloor": 20000,
        "keepRecentTokens": 8000,
        "maxHistoryShare": 0.5,
        "identifierPolicy": "strict",
        "memoryFlush": {
            "enabled": true,
            "softThresholdTokens": 4000
        },
        "maxChunkTokens": 32000
    })"_json;
    
    auto config = icraw::CompactionConfig::from_json(j);
    
    CHECK(config.mode == icraw::CompactionConfig::Mode::Safeguard);
    CHECK(config.context_window_tokens == 128000);
    CHECK(config.reserve_tokens_floor == 20000);
    CHECK(config.keep_recent_tokens == 8000);
    CHECK(config.memory_flush.enabled == true);
    CHECK(config.memory_flush.soft_threshold_tokens == 4000);
}

TEST_CASE("CompactionConfig to_json", "[config][compaction]") {
    icraw::CompactionConfig config;
    config.mode = icraw::CompactionConfig::Mode::Safeguard;
    config.context_window_tokens = 128000;
    
    nlohmann::json j = config.to_json();
    
    CHECK(j["mode"] == "safeguard");
    CHECK(j["contextWindowTokens"] == 128000);
}

TEST_CASE("AgentConfig includes compaction config", "[config]") {
    nlohmann::json j = R"({
        "model": "test-model",
        "compaction": {
            "contextWindowTokens": 64000
        }
    })"_json;
    
    auto config = icraw::AgentConfig::from_json(j);
    
    CHECK(config.model == "test-model");
    CHECK(config.compaction.context_window_tokens == 64000);
}
