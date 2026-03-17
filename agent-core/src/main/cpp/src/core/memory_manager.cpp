#include "icraw/core/memory_manager.hpp"
#include "icraw/core/token_utils.hpp"
#include <fstream>
#include <sstream>
#include <chrono>
#include <iomanip>
#include <algorithm>
#include <regex>
#include <ctime>

// SQLite3 amalgamation header
#include "sqlite3.h"

namespace icraw {

// ============================================================================
// SQLiteDatabase Implementation
// ============================================================================

SQLiteDatabase::SQLiteDatabase(const std::filesystem::path& db_path)
    : db_path_(db_path) {
    open();
}

SQLiteDatabase::~SQLiteDatabase() {
    close();
}

SQLiteDatabase::SQLiteDatabase(SQLiteDatabase&& other) noexcept
    : db_(other.db_)
    , stmt_(other.stmt_)
    , db_path_(std::move(other.db_path_)) {
    other.db_ = nullptr;
    other.stmt_ = nullptr;
}

SQLiteDatabase& SQLiteDatabase::operator=(SQLiteDatabase&& other) noexcept {
    if (this != &other) {
        close();
        db_ = other.db_;
        stmt_ = other.stmt_;
        db_path_ = std::move(other.db_path_);
        other.db_ = nullptr;
        other.stmt_ = nullptr;
    }
    return *this;
}

bool SQLiteDatabase::open() {
    if (db_ != nullptr) {
        return true;
    }
    
    int result = sqlite3_open(db_path_.string().c_str(), &db_);
    if (result != SQLITE_OK) {
        sqlite3_close(db_);
        db_ = nullptr;
        return false;
    }
    
    // Enable WAL mode for better concurrency
    execute("PRAGMA journal_mode=WAL;");
    execute("PRAGMA synchronous=NORMAL;");
    
    return true;
}

void SQLiteDatabase::close() {
    if (stmt_ != nullptr) {
        sqlite3_finalize(stmt_);
        stmt_ = nullptr;
    }
    if (db_ != nullptr) {
        sqlite3_close(db_);
        db_ = nullptr;
    }
}

bool SQLiteDatabase::execute(const std::string& sql) {
    if (db_ == nullptr) {
        return false;
    }
    
    char* error_msg = nullptr;
    int result = sqlite3_exec(db_, sql.c_str(), nullptr, nullptr, &error_msg);
    
    if (error_msg != nullptr) {
        sqlite3_free(error_msg);
    }
    
    return result == SQLITE_OK;
}

std::optional<std::string> SQLiteDatabase::query_string(const std::string& sql) {
    if (db_ == nullptr) {
        return std::nullopt;
    }
    
    sqlite3_stmt* stmt = nullptr;
    int result = sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr);
    if (result != SQLITE_OK) {
        return std::nullopt;
    }
    
    std::optional<std::string> value;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        const char* text = reinterpret_cast<const char*>(sqlite3_column_text(stmt, 0));
        if (text != nullptr) {
            value = std::string(text);
        }
    }
    
    sqlite3_finalize(stmt);
    return value;
}

std::optional<int64_t> SQLiteDatabase::query_int(const std::string& sql) {
    if (db_ == nullptr) {
        return std::nullopt;
    }
    
    sqlite3_stmt* stmt = nullptr;
    int result = sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr);
    if (result != SQLITE_OK) {
        return std::nullopt;
    }
    
    std::optional<int64_t> value;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        value = sqlite3_column_int64(stmt, 0);
    }
    
    sqlite3_finalize(stmt);
    return value;
}

std::vector<std::vector<std::string>> SQLiteDatabase::query_rows(const std::string& sql) {
    std::vector<std::vector<std::string>> rows;
    
    if (db_ == nullptr) {
        return rows;
    }
    
    sqlite3_stmt* stmt = nullptr;
    int result = sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr);
    if (result != SQLITE_OK) {
        return rows;
    }
    
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        int col_count = sqlite3_column_count(stmt);
        std::vector<std::string> row;
        row.reserve(col_count);
        
        for (int i = 0; i < col_count; ++i) {
            const char* text = reinterpret_cast<const char*>(sqlite3_column_text(stmt, i));
            row.push_back(text != nullptr ? std::string(text) : "");
        }
        rows.push_back(std::move(row));
    }
    
    sqlite3_finalize(stmt);
    return rows;
}

bool SQLiteDatabase::prepare(const std::string& sql) {
    if (db_ == nullptr) {
        return false;
    }
    
    if (stmt_ != nullptr) {
        sqlite3_finalize(stmt_);
        stmt_ = nullptr;
    }
    
    int result = sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt_, nullptr);
    return result == SQLITE_OK;
}

bool SQLiteDatabase::bind(int index, const std::string& value) {
    if (stmt_ == nullptr) {
        return false;
    }
    return sqlite3_bind_text(stmt_, index, value.c_str(), -1, SQLITE_TRANSIENT) == SQLITE_OK;
}

bool SQLiteDatabase::bind(int index, int64_t value) {
    if (stmt_ == nullptr) {
        return false;
    }
    return sqlite3_bind_int64(stmt_, index, value) == SQLITE_OK;
}

bool SQLiteDatabase::bind(int index, double value) {
    if (stmt_ == nullptr) {
        return false;
    }
    return sqlite3_bind_double(stmt_, index, value) == SQLITE_OK;
}

bool SQLiteDatabase::bind_null(int index) {
    if (stmt_ == nullptr) {
        return false;
    }
    return sqlite3_bind_null(stmt_, index) == SQLITE_OK;
}

bool SQLiteDatabase::step() {
    if (stmt_ == nullptr) {
        return false;
    }
    return sqlite3_step(stmt_) == SQLITE_ROW;
}

bool SQLiteDatabase::step_exec() {
    if (stmt_ == nullptr) {
        return false;
    }
    int result = sqlite3_step(stmt_);
    return result == SQLITE_DONE || result == SQLITE_ROW;
}

std::string SQLiteDatabase::get_column_string(int index) const {
    if (stmt_ == nullptr) {
        return "";
    }
    const char* text = reinterpret_cast<const char*>(sqlite3_column_text(stmt_, index));
    return text != nullptr ? std::string(text) : "";
}

int64_t SQLiteDatabase::get_column_int(int index) const {
    if (stmt_ == nullptr) {
        return 0;
    }
    return sqlite3_column_int64(stmt_, index);
}

double SQLiteDatabase::get_column_double(int index) const {
    if (stmt_ == nullptr) {
        return 0.0;
    }
    return sqlite3_column_double(stmt_, index);
}

void SQLiteDatabase::reset() {
    if (stmt_ != nullptr) {
        sqlite3_reset(stmt_);
        sqlite3_clear_bindings(stmt_);
    }
}

std::string SQLiteDatabase::get_error() const {
    if (db_ == nullptr) {
        return "Database not open";
    }
    return sqlite3_errmsg(db_);
}

int64_t SQLiteDatabase::last_insert_rowid() const {
    if (db_ == nullptr) {
        return 0;
    }
    return sqlite3_last_insert_rowid(db_);
}

// ============================================================================
// MemoryManager Implementation
// ============================================================================

MemoryManager::MemoryManager(const std::filesystem::path& workspace_path)
    : workspace_path_(workspace_path)
    , db_(std::make_unique<SQLiteDatabase>(workspace_path / "memory.db")) {
    
    // Create database schema
    create_schema();
    
    // Load workspace files into cache
    load_workspace_files();
}

MemoryManager::~MemoryManager() = default;

bool MemoryManager::create_schema() {
    if (!db_ || !db_->is_open()) {
        return false;
    }
    
    // Create messages table with new columns
    const char* create_messages_sql = R"(
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp TEXT NOT NULL,
            session_id TEXT NOT NULL DEFAULT 'default',
            metadata TEXT,
            token_count INTEGER DEFAULT 0,
            consolidated INTEGER DEFAULT 0
        );
        
        CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id);
        CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp);
        CREATE INDEX IF NOT EXISTS idx_messages_consolidated ON messages(consolidated);
    )";
    
    if (!db_->execute(create_messages_sql)) {
        return false;
    }
    
    // Create summaries table
    const char* create_summaries_sql = R"(
        CREATE TABLE IF NOT EXISTS summaries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            summary TEXT NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            message_count INTEGER DEFAULT 0
        );
        
        CREATE INDEX IF NOT EXISTS idx_summaries_session ON summaries(session_id);
    )";
    
    if (!db_->execute(create_summaries_sql)) {
        return false;
    }
    
    // Create daily_memory table
    const char* create_daily_sql = R"(
        CREATE TABLE IF NOT EXISTS daily_memory (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            content TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        
        CREATE INDEX IF NOT EXISTS idx_daily_memory_date ON daily_memory(date);
    )";
    
    if (!db_->execute(create_daily_sql)) {
        return false;
    }
    
    // Create compactions table (for tracking compaction history)
    const char* create_compactions_sql = R"(
        CREATE TABLE IF NOT EXISTS compactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            summary TEXT NOT NULL,
            first_kept_message_id INTEGER,
            tokens_before INTEGER,
            tokens_after INTEGER,
            created_at TEXT NOT NULL,
            mode TEXT DEFAULT 'full'
        );
        
        CREATE INDEX IF NOT EXISTS idx_compactions_session ON compactions(session_id);
    )";
    
    if (!db_->execute(create_compactions_sql)) {
        return false;
    }
    
    // Create token_stats table (for quick token count queries)
    const char* create_token_stats_sql = R"(
        CREATE TABLE IF NOT EXISTS token_stats (
            session_id TEXT PRIMARY KEY,
            total_tokens INTEGER DEFAULT 0,
            last_updated TEXT NOT NULL
        );
    )";
    
    if (!db_->execute(create_token_stats_sql)) {
        return false;
    }
    
    // Create memory_flush_log table (for tracking flush operations)
    const char* create_flush_log_sql = R"(
        CREATE TABLE IF NOT EXISTS memory_flush_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            executed_at TEXT NOT NULL
        );
        
        CREATE INDEX IF NOT EXISTS idx_flush_log_session ON memory_flush_log(session_id);
    )";
    
    if (!db_->execute(create_flush_log_sql)) {
        return false;
    }
    
    // Create FTS5 virtual table for full-text search
    // Note: FTS5 is included in SQLite amalgamation by default
    const char* create_fts_sql = R"(
        CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
            content,
            role,
            timestamp,
            content='messages',
            content_rowid='id',
            tokenize='unicode61'
        );
    )";
    
    // FTS5 might not be available on all SQLite builds, so don't fail if it errors
    db_->execute(create_fts_sql);
    
    // Create FTS triggers for automatic sync (only if FTS table exists)
    const char* create_fts_triggers_sql = R"(
        -- Trigger to update FTS on INSERT
        CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
            INSERT INTO messages_fts(rowid, content, role, timestamp)
            VALUES (new.id, new.content, new.role, new.timestamp);
        END;
        
        -- Trigger to update FTS on DELETE
        CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
            INSERT INTO messages_fts(messages_fts, rowid, content, role, timestamp)
            VALUES ('delete', old.id, old.content, old.role, old.timestamp);
        END;
        
        -- Trigger to update FTS on UPDATE
        CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
            INSERT INTO messages_fts(messages_fts, rowid, content, role, timestamp)
            VALUES ('delete', old.id, old.content, old.role, old.timestamp);
            INSERT INTO messages_fts(rowid, content, role, timestamp)
            VALUES (new.id, new.content, new.role, new.timestamp);
        END;
    )";
    
    // Don't fail if triggers can't be created (FTS might not be available)
    db_->execute(create_fts_triggers_sql);
    
    // Migration: Add new columns to existing tables if they don't exist
    // SQLite doesn't support IF NOT EXISTS for ALTER TABLE, so we try and ignore errors
    db_->execute("ALTER TABLE messages ADD COLUMN token_count INTEGER DEFAULT 0;");
    db_->execute("ALTER TABLE messages ADD COLUMN consolidated INTEGER DEFAULT 0;");
    
    return true;
}

std::string MemoryManager::get_timestamp() {
    auto now = std::chrono::system_clock::now();
    auto now_time = std::chrono::system_clock::to_time_t(now);
    
    // Get milliseconds
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()) % 1000;
    
    std::tm tm = *std::gmtime(&now_time);
    
    std::ostringstream ss;
    ss << std::put_time(&tm, "%Y-%m-%dT%H:%M:%S");
    ss << '.' << std::setfill('0') << std::setw(3) << ms.count() << 'Z';
    
    return ss.str();
}

void MemoryManager::close() {
    db_.reset();
}

void MemoryManager::load_workspace_files() {
    soul_content_ = read_identity_file("SOUL.md");
    user_content_ = read_identity_file("USER.md");
    agents_content_ = read_agents_file();
    tools_content_ = read_tools_file();
}

std::string MemoryManager::read_identity_file(const std::string& filename) const {
    auto filepath = workspace_path_ / filename;
    if (std::filesystem::exists(filepath)) {
        return read_file(filepath);
    }
    return "";
}

std::string MemoryManager::read_agents_file() const {
    auto filepath = workspace_path_ / "AGENTS.md";
    if (std::filesystem::exists(filepath)) {
        return read_file(filepath);
    }
    return "";
}

std::string MemoryManager::read_tools_file() const {
    auto filepath = workspace_path_ / "TOOLS.md";
    if (std::filesystem::exists(filepath)) {
        return read_file(filepath);
    }
    return "";
}

// --- Conversation History ---

int64_t MemoryManager::add_message(const std::string& role,
                                    const std::string& content,
                                    const std::string& session_id,
                                    const nlohmann::json& metadata) {
    if (!db_) {
        ICRAW_LOG_ERROR("add_message: db_ is null");
        return -1;
    }
    if (!db_->is_open()) {
        ICRAW_LOG_ERROR("add_message: db is not open");
        return -1;
    }
    
    std::string timestamp = get_timestamp();
    std::string metadata_str = metadata.dump();
    
    // Estimate token count for this message
    int token_count = estimate_tokens(content) + 4;  // +4 for role and formatting overhead
    
    // Use parameterized query to prevent SQL injection
    std::string sql = "INSERT INTO messages (role, content, timestamp, session_id, metadata, token_count) VALUES (?, ?, ?, ?, ?, ?);";

    ICRAW_LOG_DEBUG("add_message: preparing SQL: {}", sql);

    if (!db_->prepare(sql)) {
        ICRAW_LOG_ERROR("add_message: prepare failed, error: {}", db_->get_error());
        return -1;
    }

    db_->bind(1, role);
    db_->bind(2, content);
    db_->bind(3, timestamp);
    db_->bind(4, session_id);
    db_->bind(5, metadata_str);
    db_->bind(6, static_cast<int64_t>(token_count));

    // Execute
    db_->step_exec();

    int64_t id = db_->last_insert_rowid();
    ICRAW_LOG_DEBUG("add_message: inserted id={}", id);
    
    // Update token stats (async-friendly: just invalidate cache)
    if (id > 0) {
        // Update total tokens in cache
        auto stats = get_token_stats(session_id);
        if (stats) {
            std::string update_sql = "UPDATE token_stats SET total_tokens = total_tokens + ?, last_updated = ? WHERE session_id = ?;";
            if (db_->prepare(update_sql)) {
                db_->bind(1, static_cast<int64_t>(token_count));
                db_->bind(2, timestamp);
                db_->bind(3, session_id);
                db_->step_exec();
                db_->reset();
            }
        }
    }
    
    return id;
}

std::vector<MemoryEntry> MemoryManager::get_recent_messages_by_roles(
        int limit,
        const std::vector<std::string>& roles,
        const std::string& session_id) const {
    std::vector<MemoryEntry> messages;

    if (!db_ || !db_->is_open()) {
        return messages;
    }

    // Build role filter: role IN ('user', 'assistant')
    std::string role_filter;
    for (size_t i = 0; i < roles.size(); ++i) {
        if (i > 0) role_filter += ", ";
        role_filter += "'" + roles[i] + "'";
    }

    std::string sql = "SELECT id, role, content, timestamp, session_id, metadata, token_count, consolidated FROM messages "
                      "WHERE session_id = ? AND role IN (" + role_filter + ") "
                      "ORDER BY timestamp DESC LIMIT ?;";

    if (!db_->prepare(sql)) {
        return messages;
    }

    db_->bind(1, session_id);
    db_->bind(2, static_cast<int64_t>(limit));

    while (db_->step()) {
        MemoryEntry entry;
        entry.id = db_->get_column_int(0);
        entry.role = db_->get_column_string(1);
        entry.content = db_->get_column_string(2);
        entry.timestamp = db_->get_column_string(3);
        entry.session_id = db_->get_column_string(4);
        std::string metadata_str = db_->get_column_string(5);
        if (!metadata_str.empty()) {
            try {
                entry.metadata = nlohmann::json::parse(metadata_str);
            } catch (...) {}
        }
        entry.token_count = static_cast<int>(db_->get_column_int(6));
        entry.consolidated = db_->get_column_int(7) != 0;
        messages.push_back(std::move(entry));
    }

    db_->reset();

    // Reverse to get chronological order (oldest first)
    std::reverse(messages.begin(), messages.end());
    return messages;
}

std::vector<MemoryEntry> MemoryManager::get_recent_messages(int limit,
                                                             const std::string& session_id) const {
    std::vector<MemoryEntry> messages;
    
    if (!db_ || !db_->is_open()) {
        return messages;
    }
    
    // Use parameterized query to prevent SQL injection
    std::string sql = "SELECT id, role, content, timestamp, session_id, metadata, token_count, consolidated FROM messages "
                      "WHERE session_id = ? "
                      "ORDER BY timestamp DESC LIMIT ?;";
    
    if (!db_->prepare(sql)) {
        return messages;
    }
    
    db_->bind(1, session_id);
    db_->bind(2, static_cast<int64_t>(limit));
    
    while (db_->step()) {
        MemoryEntry entry;
        entry.id = db_->get_column_int(0);
        entry.role = db_->get_column_string(1);
        entry.content = db_->get_column_string(2);
        entry.timestamp = db_->get_column_string(3);
        entry.session_id = db_->get_column_string(4);
        std::string metadata_str = db_->get_column_string(5);
        if (!metadata_str.empty()) {
            try {
                entry.metadata = nlohmann::json::parse(metadata_str);
            } catch (...) {}
        }
        entry.token_count = static_cast<int>(db_->get_column_int(6));
        entry.consolidated = db_->get_column_int(7) != 0;
        messages.push_back(std::move(entry));
    }
    
    db_->reset();
    
    // Reverse to get chronological order
    std::reverse(messages.begin(), messages.end());
    return messages;
}

std::vector<MemoryEntry> MemoryManager::get_all_messages(const std::string& session_id) const {
    return get_recent_messages(1000000, session_id);  // Large limit for "all"
}

void MemoryManager::clear_history(const std::string& session_id) {
    if (!db_ || !db_->is_open()) {
        return;
    }
    
    // Use parameterized query to prevent SQL injection
    std::string sql = "DELETE FROM messages WHERE session_id = ?;";
    
    if (!db_->prepare(sql)) {
        return;
    }
    
    db_->bind(1, session_id);
    db_->step_exec();
}

int64_t MemoryManager::get_message_count(const std::string& session_id) const {
    if (!db_ || !db_->is_open()) {
        return 0;
    }
    
    // Use parameterized query to prevent SQL injection
    std::string sql = "SELECT COUNT(*) FROM messages WHERE session_id = ?;";
    
    if (!db_->prepare(sql)) {
        return 0;
    }
    
    db_->bind(1, session_id);
    
    int64_t count = 0;
    if (db_->step()) {
        count = db_->get_column_int(0);
    }
    
    db_->reset();
    return count;
}

// --- Search ---

std::vector<MemoryEntry> MemoryManager::search_memory(const std::string& query,
                                                       int limit) const {
    std::vector<MemoryEntry> results;
    
    if (!db_ || !db_->is_open()) {
        return results;
    }
    
    // Use parameterized query to prevent SQL injection
    // The LIKE pattern with wildcards needs to be constructed safely
    std::string like_pattern = "%" + query + "%";
    std::string sql = "SELECT id, role, content, timestamp, session_id, metadata FROM messages "
                      "WHERE content LIKE ? "
                      "ORDER BY timestamp DESC LIMIT ?;";
    
    if (!db_->prepare(sql)) {
        return results;
    }
    
    db_->bind(1, like_pattern);
    db_->bind(2, static_cast<int64_t>(limit));
    
    while (db_->step()) {
        MemoryEntry entry;
        entry.id = db_->get_column_int(0);
        entry.role = db_->get_column_string(1);
        entry.content = db_->get_column_string(2);
        entry.timestamp = db_->get_column_string(3);
        entry.session_id = db_->get_column_string(4);
        std::string metadata_str = db_->get_column_string(5);
        if (!metadata_str.empty()) {
            entry.metadata = nlohmann::json::parse(metadata_str);
        }
        results.push_back(std::move(entry));
    }
    
    db_->reset();
    
    return results;
}

// --- Daily Memory ---

void MemoryManager::save_daily_memory(const std::string& content) {
    // Save to SQLite
    if (db_ && db_->is_open()) {
        auto now = std::chrono::system_clock::now();
        auto now_time = std::chrono::system_clock::to_time_t(now);
        std::tm tm = *std::gmtime(&now_time);
        
        std::ostringstream date_ss;
        date_ss << std::put_time(&tm, "%Y-%m-%d");
        std::string date_str = date_ss.str();
        std::string timestamp = get_timestamp();
        
        std::string sql = "INSERT INTO daily_memory (date, content, created_at) VALUES (?, ?, ?);";
        
        if (db_->prepare(sql)) {
            db_->bind(1, date_str);
            db_->bind(2, content);
            db_->bind(3, timestamp);
            db_->step_exec();
        }
    }
    
    // Also save to file for compatibility
    auto now = std::chrono::system_clock::now();
    auto now_time = std::chrono::system_clock::to_time_t(now);
    std::tm tm = *std::localtime(&now_time);
    
    std::ostringstream date_ss;
    date_ss << std::put_time(&tm, "%Y-%m-%d");
    std::string date_str = date_ss.str();
    
    auto memory_dir = workspace_path_ / "memory";
    std::filesystem::create_directories(memory_dir);
    
    auto filepath = memory_dir / (date_str + ".md");
    std::ofstream file(filepath, std::ios::app);
    if (file.is_open()) {
        file << "\n---\n" << content << "\n";
        file.close();
    }
}

std::vector<MemoryEntry> MemoryManager::get_daily_memory(const std::string& date) const {
    std::vector<MemoryEntry> entries;
    
    if (!db_ || !db_->is_open()) {
        return entries;
    }
    
    std::string date_filter = date;
    if (date_filter.empty()) {
        // Get today's date
        auto now = std::chrono::system_clock::now();
        auto now_time = std::chrono::system_clock::to_time_t(now);
        std::tm tm = *std::gmtime(&now_time);
        
        std::ostringstream date_ss;
        date_ss << std::put_time(&tm, "%Y-%m-%d");
        date_filter = date_ss.str();
    }
    
    // Use parameterized query to prevent SQL injection
    std::string sql = "SELECT id, date, content, created_at FROM daily_memory "
                      "WHERE date = ? "
                      "ORDER BY created_at ASC;";
    
    if (!db_->prepare(sql)) {
        return entries;
    }
    
    db_->bind(1, date_filter);
    
    while (db_->step()) {
        MemoryEntry entry;
        entry.id = db_->get_column_int(0);
        entry.timestamp = db_->get_column_string(1);  // date
        entry.content = db_->get_column_string(2);
        entry.role = "daily_memory";
        entries.push_back(std::move(entry));
    }
    
    db_->reset();
    
    return entries;
}

// --- Summary ---

int64_t MemoryManager::create_summary(const std::string& session_id,
                                       const std::string& summary,
                                       int message_count) {
    if (!db_ || !db_->is_open()) {
        return -1;
    }
    
    std::string timestamp = get_timestamp();
    
    std::string sql = "INSERT INTO summaries (session_id, summary, created_at, updated_at, message_count) "
                      "VALUES (?, ?, ?, ?, ?);";
    
    if (!db_->prepare(sql)) {
        return -1;
    }
    
    db_->bind(1, session_id);
    db_->bind(2, summary);
    db_->bind(3, timestamp);
    db_->bind(4, timestamp);
    db_->bind(5, static_cast<int64_t>(message_count));
    
    db_->step_exec();
    
    return db_->last_insert_rowid();
}

std::optional<ConversationSummary> MemoryManager::get_latest_summary(
    const std::string& session_id) const {
    
    if (!db_ || !db_->is_open()) {
        return std::nullopt;
    }
    
    // Use parameterized query to prevent SQL injection
    std::string sql = "SELECT id, session_id, summary, created_at, updated_at, message_count "
                      "FROM summaries WHERE session_id = ? "
                      "ORDER BY created_at DESC LIMIT 1;";
    
    if (!db_->prepare(sql)) {
        return std::nullopt;
    }
    
    db_->bind(1, session_id);
    
    std::optional<ConversationSummary> result;
    if (db_->step()) {
        ConversationSummary sum;
        sum.id = db_->get_column_int(0);
        sum.session_id = db_->get_column_string(1);
        sum.summary = db_->get_column_string(2);
        sum.created_at = db_->get_column_string(3);
        sum.updated_at = db_->get_column_string(4);
        sum.message_count = static_cast<int>(db_->get_column_int(5));
        result = std::move(sum);
    }
    
    db_->reset();
    return result;
}

std::vector<MemoryEntry> MemoryManager::get_messages_for_consolidation(
    int keep_count,
    const std::string& session_id) const {
    
    std::vector<MemoryEntry> messages;
    
    if (!db_ || !db_->is_open()) {
        return messages;
    }
    
    // Get total count first
    int64_t total = get_message_count(session_id);
    if (total <= keep_count) {
        return messages;  // Nothing to consolidate
    }
    
    // Get oldest messages (excluding the most recent 'keep_count' ones)
    int skip_count = total - keep_count;
    
    // Use parameterized query to prevent SQL injection
    std::string sql = "SELECT id, role, content, timestamp, session_id, metadata FROM messages "
                      "WHERE session_id = ? "
                      "ORDER BY timestamp ASC LIMIT ?;";
    
    if (!db_->prepare(sql)) {
        return messages;
    }
    
    db_->bind(1, session_id);
    db_->bind(2, static_cast<int64_t>(skip_count));
    
    while (db_->step()) {
        MemoryEntry entry;
        entry.id = db_->get_column_int(0);
        entry.role = db_->get_column_string(1);
        entry.content = db_->get_column_string(2);
        entry.timestamp = db_->get_column_string(3);
        entry.session_id = db_->get_column_string(4);
        std::string metadata_str = db_->get_column_string(5);
        if (!metadata_str.empty()) {
            entry.metadata = nlohmann::json::parse(metadata_str);
        }
        messages.push_back(std::move(entry));
    }
    
    db_->reset();
    
    return messages;
}

void MemoryManager::mark_consolidated(int count, const std::string& session_id) {
    if (!db_ || !db_->is_open()) {
        return;
    }
    
    // Mark the oldest 'count' messages as consolidated
    std::string sql = R"(
        UPDATE messages SET consolidated = 1 
        WHERE id IN (
            SELECT id FROM messages 
            WHERE session_id = ? AND consolidated = 0
            ORDER BY timestamp ASC 
            LIMIT ?
        );
    )";
    
    if (!db_->prepare(sql)) {
        return;
    }
    
    db_->bind(1, session_id);
    db_->bind(2, static_cast<int64_t>(count));
    db_->step_exec();
    db_->reset();
    
    // Update token stats after consolidation
    update_token_stats(session_id);
}

int64_t MemoryManager::get_total_message_count(const std::string& session_id) const {
    return get_message_count(session_id);
}

// --- FTS5 Full-Text Search ---

std::vector<MemoryEntry> MemoryManager::search_memory_fts(const std::string& query,
                                                           int limit,
                                                           const std::string& session_id) const {
    std::vector<MemoryEntry> results;
    
    if (!db_ || !db_->is_open()) {
        return results;
    }
    
    // Try FTS5 search first
    // FTS5 uses MATCH operator with query string
    std::string fts_sql = R"(
        SELECT m.id, m.role, m.content, m.timestamp, m.session_id, m.metadata
        FROM messages m
        JOIN messages_fts fts ON m.id = fts.rowid
        WHERE messages_fts MATCH ? AND m.session_id = ?
        ORDER BY rank
        LIMIT ?;
    )";
    
    if (db_->prepare(fts_sql)) {
        db_->bind(1, query);
        db_->bind(2, session_id);
        db_->bind(3, static_cast<int64_t>(limit));
        
        while (db_->step()) {
            MemoryEntry entry;
            entry.id = db_->get_column_int(0);
            entry.role = db_->get_column_string(1);
            entry.content = db_->get_column_string(2);
            entry.timestamp = db_->get_column_string(3);
            entry.session_id = db_->get_column_string(4);
            std::string metadata_str = db_->get_column_string(5);
            if (!metadata_str.empty()) {
                try {
                    entry.metadata = nlohmann::json::parse(metadata_str);
                } catch (...) {}
            }
            results.push_back(std::move(entry));
        }
        
        db_->reset();
        
        if (!results.empty()) {
            return results;  // FTS5 worked, return results
        }
    }
    
    // Fallback to LIKE search if FTS5 not available or no results
    return search_memory(query, limit);
}

// --- Token-aware Methods ---

std::vector<MemoryEntry> MemoryManager::get_messages_within_token_budget(
    int max_tokens,
    const std::string& session_id) const {
    
    std::vector<MemoryEntry> messages;
    
    if (!db_ || !db_->is_open()) {
        return messages;
    }
    
    // Get recent messages in reverse order (newest first)
    std::string sql = "SELECT id, role, content, timestamp, session_id, metadata, token_count "
                      "FROM messages WHERE session_id = ? "
                      "ORDER BY timestamp DESC;";
    
    if (!db_->prepare(sql)) {
        return messages;
    }
    
    db_->bind(1, session_id);
    
    int total_tokens = 0;
    std::vector<MemoryEntry> temp_messages;
    
    while (db_->step()) {
        MemoryEntry entry;
        entry.id = db_->get_column_int(0);
        entry.role = db_->get_column_string(1);
        entry.content = db_->get_column_string(2);
        entry.timestamp = db_->get_column_string(3);
        entry.session_id = db_->get_column_string(4);
        std::string metadata_str = db_->get_column_string(5);
        if (!metadata_str.empty()) {
            try {
                entry.metadata = nlohmann::json::parse(metadata_str);
            } catch (...) {}
        }
        entry.token_count = static_cast<int>(db_->get_column_int(6));
        
        // If token_count is 0, estimate it
        if (entry.token_count == 0) {
            // Simple estimation: ~4 chars per token for English, ~1.5 for Chinese
            entry.token_count = static_cast<int>(entry.content.size() / 3.0 * 1.2);
        }
        
        int entry_tokens = entry.token_count > 0 ? entry.token_count : 10;  // Minimum 10 tokens
        
        if (total_tokens + entry_tokens > max_tokens) {
            break;  // Budget exceeded
        }
        
        total_tokens += entry_tokens;
        temp_messages.push_back(std::move(entry));
    }
    
    db_->reset();
    
    // Reverse to get chronological order
    std::reverse(temp_messages.begin(), temp_messages.end());
    messages = std::move(temp_messages);
    
    return messages;
}

int64_t MemoryManager::get_total_tokens(const std::string& session_id) const {
    // First try cached value
    auto stats = get_token_stats(session_id);
    if (stats && stats->total_tokens > 0) {
        return stats->total_tokens;
    }
    
    // Calculate from messages
    if (!db_ || !db_->is_open()) {
        return 0;
    }
    
    std::string sql = "SELECT COALESCE(SUM(token_count), 0) FROM messages WHERE session_id = ?;";
    
    if (!db_->prepare(sql)) {
        return 0;
    }
    
    db_->bind(1, session_id);
    
    int64_t total = 0;
    if (db_->step()) {
        total = db_->get_column_int(0);
    }
    
    db_->reset();
    
    // If token_count column is empty, estimate from content
    if (total == 0) {
        sql = "SELECT COALESCE(SUM(LENGTH(content) / 3), 0) FROM messages WHERE session_id = ?;";
        if (db_->prepare(sql)) {
            db_->bind(1, session_id);
            if (db_->step()) {
                total = static_cast<int64_t>(db_->get_column_int(0) * 1.2);  // 20% margin
            }
            db_->reset();
        }
    }
    
    return total;
}

void MemoryManager::update_token_stats(const std::string& session_id) {
    if (!db_ || !db_->is_open()) {
        return;
    }
    
    int64_t total = get_total_tokens(session_id);
    std::string timestamp = get_timestamp();
    
    // Use INSERT OR REPLACE
    std::string sql = "INSERT OR REPLACE INTO token_stats (session_id, total_tokens, last_updated) "
                      "VALUES (?, ?, ?);";
    
    if (db_->prepare(sql)) {
        db_->bind(1, session_id);
        db_->bind(2, total);
        db_->bind(3, timestamp);
        db_->step_exec();
        db_->reset();
    }
}

std::optional<TokenStats> MemoryManager::get_token_stats(const std::string& session_id) const {
    if (!db_ || !db_->is_open()) {
        return std::nullopt;
    }
    
    std::string sql = "SELECT session_id, total_tokens, last_updated FROM token_stats WHERE session_id = ?;";
    
    if (!db_->prepare(sql)) {
        return std::nullopt;
    }
    
    db_->bind(1, session_id);
    
    std::optional<TokenStats> result;
    if (db_->step()) {
        TokenStats stats;
        stats.session_id = db_->get_column_string(0);
        stats.total_tokens = db_->get_column_int(1);
        stats.last_updated = db_->get_column_string(2);
        result = std::move(stats);
    }
    
    db_->reset();
    return result;
}

// --- Compaction Tracking ---

int64_t MemoryManager::create_compaction_record(const std::string& session_id,
                                                 const std::string& summary,
                                                 int64_t first_kept_message_id,
                                                 int tokens_before,
                                                 int tokens_after,
                                                 const std::string& mode) {
    if (!db_ || !db_->is_open()) {
        return -1;
    }
    
    std::string timestamp = get_timestamp();
    
    std::string sql = "INSERT INTO compactions (session_id, summary, first_kept_message_id, "
                      "tokens_before, tokens_after, created_at, mode) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?);";
    
    if (!db_->prepare(sql)) {
        return -1;
    }
    
    db_->bind(1, session_id);
    db_->bind(2, summary);
    db_->bind(3, first_kept_message_id);
    db_->bind(4, static_cast<int64_t>(tokens_before));
    db_->bind(5, static_cast<int64_t>(tokens_after));
    db_->bind(6, timestamp);
    db_->bind(7, mode);
    
    db_->step_exec();
    
    return db_->last_insert_rowid();
}

std::optional<CompactionRecord> MemoryManager::get_latest_compaction(
    const std::string& session_id) const {
    
    if (!db_ || !db_->is_open()) {
        return std::nullopt;
    }
    
    std::string sql = "SELECT id, session_id, summary, first_kept_message_id, "
                      "tokens_before, tokens_after, created_at, mode "
                      "FROM compactions WHERE session_id = ? "
                      "ORDER BY created_at DESC LIMIT 1;";
    
    if (!db_->prepare(sql)) {
        return std::nullopt;
    }
    
    db_->bind(1, session_id);
    
    std::optional<CompactionRecord> result;
    if (db_->step()) {
        CompactionRecord record;
        record.id = db_->get_column_int(0);
        record.session_id = db_->get_column_string(1);
        record.summary = db_->get_column_string(2);
        record.first_kept_message_id = db_->get_column_int(3);
        record.tokens_before = static_cast<int>(db_->get_column_int(4));
        record.tokens_after = static_cast<int>(db_->get_column_int(5));
        record.created_at = db_->get_column_string(6);
        record.mode = db_->get_column_string(7);
        result = std::move(record);
    }
    
    db_->reset();
    return result;
}

int64_t MemoryManager::get_compaction_count(const std::string& session_id) const {
    if (!db_ || !db_->is_open()) {
        return 0;
    }
    
    std::string sql = "SELECT COUNT(*) FROM compactions WHERE session_id = ?;";
    
    if (!db_->prepare(sql)) {
        return 0;
    }
    
    db_->bind(1, session_id);
    
    int64_t count = 0;
    if (db_->step()) {
        count = db_->get_column_int(0);
    }
    
    db_->reset();
    return count;
}

// --- Memory Flush Tracking ---

bool MemoryManager::needs_memory_flush(const CompactionConfig& config) const {
    if (!config.memory_flush.enabled) {
        return false;
    }
    
    int64_t total_tokens = get_total_tokens();
    
    // Calculate threshold
    int threshold = config.context_window_tokens - config.reserve_tokens_floor 
                  - config.memory_flush.soft_threshold_tokens;
    
    return total_tokens >= threshold;
}

void MemoryManager::record_memory_flush(const std::string& session_id) {
    if (!db_ || !db_->is_open()) {
        return;
    }
    
    std::string timestamp = get_timestamp();
    
    std::string sql = "INSERT INTO memory_flush_log (session_id, executed_at) VALUES (?, ?);";
    
    if (db_->prepare(sql)) {
        db_->bind(1, session_id);
        db_->bind(2, timestamp);
        db_->step_exec();
        db_->reset();
    }
}

std::optional<std::string> MemoryManager::get_last_flush_timestamp(
    const std::string& session_id) const {
    
    if (!db_ || !db_->is_open()) {
        return std::nullopt;
    }
    
    std::string sql = "SELECT executed_at FROM memory_flush_log WHERE session_id = ? "
                      "ORDER BY executed_at DESC LIMIT 1;";
    
    if (!db_->prepare(sql)) {
        return std::nullopt;
    }
    
    db_->bind(1, session_id);
    
    std::optional<std::string> result;
    if (db_->step()) {
        result = db_->get_column_string(0);
    }
    
    db_->reset();
    return result;
}

// --- Delete Consolidated Messages ---

int64_t MemoryManager::delete_consolidated_messages(const std::string& session_id) {
    if (!db_ || !db_->is_open()) {
        return 0;
    }
    
    // First count how many will be deleted
    std::string count_sql = "SELECT COUNT(*) FROM messages WHERE session_id = ? AND consolidated = 1;";
    int64_t count = 0;
    
    if (db_->prepare(count_sql)) {
        db_->bind(1, session_id);
        if (db_->step()) {
            count = db_->get_column_int(0);
        }
        db_->reset();
    }
    
    if (count == 0) {
        return 0;
    }
    
    // Delete consolidated messages
    std::string delete_sql = "DELETE FROM messages WHERE session_id = ? AND consolidated = 1;";
    
    if (db_->prepare(delete_sql)) {
        db_->bind(1, session_id);
        db_->step_exec();
        db_->reset();
    }
    
    // Update token stats after deletion
    update_token_stats(session_id);
    
    return count;
}

// --- File I/O ---

std::string MemoryManager::read_file(const std::filesystem::path& filepath) const {
    std::ifstream file(filepath, std::ios::binary);
    if (!file.is_open()) {
        return "";
    }
    
    std::ostringstream ss;
    ss << file.rdbuf();
    return ss.str();
}

void MemoryManager::write_file(const std::filesystem::path& filepath, const std::string& content) const {
    std::filesystem::create_directories(filepath.parent_path());
    
    std::ofstream file(filepath, std::ios::binary);
    if (file.is_open()) {
        file << content;
        file.close();
    }
}

bool MemoryManager::file_exists(const std::filesystem::path& filepath) const {
    return std::filesystem::exists(filepath);
}

} // namespace icraw
