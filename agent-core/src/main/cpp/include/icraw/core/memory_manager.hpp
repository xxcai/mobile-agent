#pragma once

#include <string>
#include <vector>
#include <filesystem>
#include <memory>
#include <mutex>
#include <functional>
#include <optional>
#include "icraw/types.hpp"
#include "icraw/config.hpp"
#include "icraw/log/logger.hpp"

// Forward declaration
struct sqlite3;
struct sqlite3_stmt;

namespace icraw {

// --- Memory Entry Types ---

struct MemoryEntry {
    int64_t id = 0;
    std::string role;           // "user", "assistant", "system", "tool"
    std::string content;
    std::string timestamp;      // ISO 8601
    std::string session_id;     // For future multi-session support
    nlohmann::json metadata;    // Additional metadata (tokens, etc.)
    int token_count = 0;        // Estimated token count
    bool consolidated = false;  // Whether this message has been consolidated
};

struct ConversationSummary {
    int64_t id = 0;
    std::string session_id;
    std::string summary;
    std::string created_at;
    std::string updated_at;
    int message_count = 0;
};

// --- Compaction Record ---
// Tracks compaction operations for debugging and metrics

struct CompactionRecord {
    int64_t id = 0;
    std::string session_id;
    std::string summary;            // The summary generated
    int64_t first_kept_message_id;  // First message ID kept after compaction
    int tokens_before;              // Token count before compaction
    int tokens_after;               // Token count after compaction
    std::string created_at;
    std::string mode;               // "full", "partial", "fallback"
};

// --- Token Stats ---
// Cached token statistics for quick access

struct TokenStats {
    std::string session_id;
    int64_t total_tokens;
    std::string last_updated;
};

// --- SQLite Database Wrapper ---

class SQLiteDatabase {
public:
    explicit SQLiteDatabase(const std::filesystem::path& db_path);
    ~SQLiteDatabase();
    
    // No copy
    SQLiteDatabase(const SQLiteDatabase&) = delete;
    SQLiteDatabase& operator=(const SQLiteDatabase&) = delete;
    
    // Move allowed
    SQLiteDatabase(SQLiteDatabase&& other) noexcept;
    SQLiteDatabase& operator=(SQLiteDatabase&& other) noexcept;
    
    // Execute SQL with no results
    bool execute(const std::string& sql);
    
    // Execute SQL and get single value
    std::optional<std::string> query_string(const std::string& sql);
    std::optional<int64_t> query_int(const std::string& sql);
    
    // Execute SQL and get multiple rows
    std::vector<std::vector<std::string>> query_rows(const std::string& sql);
    
    // Prepare statement
    bool prepare(const std::string& sql);
    bool bind(int index, const std::string& value);
    bool bind(int index, int64_t value);
    bool bind(int index, double value);
    bool bind_null(int index);
    
    // Step through results
    bool step();  // Returns true if there's a row
    
    // Execute prepared statement (for INSERT/UPDATE/DELETE)
    bool step_exec();  // Returns true on success
    
    // Get column values from current row
    std::string get_column_string(int index) const;
    int64_t get_column_int(int index) const;
    double get_column_double(int index) const;
    
    void reset();
    
    // Get database path
    const std::filesystem::path& path() const { return db_path_; }
    
    // Check if database is open
    bool is_open() const { return db_ != nullptr; }
    
    // Get last error message
    std::string get_error() const;
    
    // Get last insert row ID
    int64_t last_insert_rowid() const;
    
    // Close the database explicitly
    void close();

private:
    sqlite3* db_ = nullptr;
    sqlite3_stmt* stmt_ = nullptr;
    std::filesystem::path db_path_;
    
    bool open();
};

// --- Memory Manager ---

class MemoryManager {
public:
    explicit MemoryManager(const std::filesystem::path& workspace_path);
    ~MemoryManager();

    // Load all workspace files into memory
    void load_workspace_files();

    // Read identity files (SOUL.md, USER.md)
    std::string read_identity_file(const std::string& filename) const;

    // Read AGENTS.md (behavior instructions)
    std::string read_agents_file() const;

    // Read TOOLS.md (tool usage guide)
    std::string read_tools_file() const;

    // --- Conversation History ---
    
    // Add message to history
    int64_t add_message(const std::string& role, const std::string& content,
                       const std::string& session_id = "default",
                       const nlohmann::json& metadata = {});
    
    // Get recent messages
    std::vector<MemoryEntry> get_recent_messages(int limit = 50,
                                                   const std::string& session_id = "default") const;

    // Get recent messages filtered by roles
    std::vector<MemoryEntry> get_recent_messages_by_roles(
        int limit,
        const std::vector<std::string>& roles,
        const std::string& session_id = "default") const;

    // Get all messages
    std::vector<MemoryEntry> get_all_messages(const std::string& session_id = "default") const;
    
    // Clear conversation history
    bool clear_history(const std::string& session_id = "default");

    // Clear persisted long-term memory for a session.
    bool clear_long_term_memory(const std::string& session_id = "default");

    // Clear persisted daily memory log entries globally.
    bool clear_daily_memory();
    
    // Get message count
    int64_t get_message_count(const std::string& session_id = "default") const;
    
    // --- Search ---
    
    // Search memory for content (LIKE-based, legacy)
    std::vector<MemoryEntry> search_memory(const std::string& query,
                                           int limit = 10) const;
    
    // FTS5 full-text search (faster, more accurate)
    std::vector<MemoryEntry> search_memory_fts(const std::string& query,
                                                int limit = 10,
                                                const std::string& session_id = "default") const;
    
    // --- Daily Memory ---
    
    // Save daily memory entry
    void save_daily_memory(const std::string& content);
    
    // Get daily memory entries
    std::vector<MemoryEntry> get_daily_memory(const std::string& date = "") const;
    
    // --- Summary ---
    
    // Create conversation summary
    int64_t create_summary(const std::string& session_id,
                          const std::string& summary,
                          int message_count);
    
    // Get latest summary
    std::optional<ConversationSummary> get_latest_summary(
        const std::string& session_id = "default") const;
    
    // Get messages for consolidation (older messages)
    std::vector<MemoryEntry> get_messages_for_consolidation(
        int keep_count,
        const std::string& session_id = "default") const;
    
    // Mark messages as consolidated
    void mark_consolidated(int count, const std::string& session_id = "default");
    
    // Get total message count
    int64_t get_total_message_count(const std::string& session_id = "default") const;
    
    // --- Token-aware Methods ---
    
    // Get messages within a token budget
    std::vector<MemoryEntry> get_messages_within_token_budget(
        int max_tokens,
        const std::string& session_id = "default") const;
    
    // Get total token count for a session
    int64_t get_total_tokens(const std::string& session_id = "default") const;
    
    // Update token stats cache
    void update_token_stats(const std::string& session_id = "default");
    
    // Get token stats
    std::optional<TokenStats> get_token_stats(const std::string& session_id = "default") const;
    
    // --- Compaction Tracking ---
    
    // Create compaction record
    int64_t create_compaction_record(const std::string& session_id,
                                      const std::string& summary,
                                      int64_t first_kept_message_id,
                                      int tokens_before,
                                      int tokens_after,
                                      const std::string& mode);
    
    // Get latest compaction record
    std::optional<CompactionRecord> get_latest_compaction(
        const std::string& session_id = "default") const;
    
    // Get compaction count
    int64_t get_compaction_count(const std::string& session_id = "default") const;
    
    // --- Memory Flush Tracking ---
    
    // Check if memory flush is needed
    bool needs_memory_flush(const CompactionConfig& config) const;
    
    // Record memory flush execution
    void record_memory_flush(const std::string& session_id = "default");
    
    // Get last flush timestamp
    std::optional<std::string> get_last_flush_timestamp(
        const std::string& session_id = "default") const;
    
    // --- Delete Consolidated Messages ---
    
    // Delete messages that have been consolidated (cleanup)
    int64_t delete_consolidated_messages(const std::string& session_id = "default");
    
    // --- File I/O ---
    
    std::string read_file(const std::filesystem::path& filepath) const;
    void write_file(const std::filesystem::path& filepath, const std::string& content) const;
    bool file_exists(const std::filesystem::path& filepath) const;
    
    // --- Accessors ---
    
    const std::filesystem::path& get_workspace_path() const { return workspace_path_; }
    SQLiteDatabase& database() { return *db_; }
    const SQLiteDatabase& database() const { return *db_; }
    
    // Close database explicitly (useful for testing cleanup)
    void close();

private:
    // Create database schema
    bool create_schema();

    // Get current timestamp as ISO 8601
    static std::string get_timestamp();

    // Recalculate token totals directly from messages without reading token_stats cache.
    int64_t calculate_total_tokens_uncached(const std::string& session_id) const;
    
    std::filesystem::path workspace_path_;
    std::unique_ptr<SQLiteDatabase> db_;
    mutable std::recursive_mutex db_mutex_;
    
    // Cache for identity files
    std::string soul_content_;
    std::string user_content_;
    std::string agents_content_;
    std::string tools_content_;
};

} // namespace icraw
