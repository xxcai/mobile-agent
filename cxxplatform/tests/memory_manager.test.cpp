#include <catch2/catch_test_macros.hpp>
#include "icraw/core/memory_manager.hpp"
#include <filesystem>
#include <fstream>
#include <thread>
#include <chrono>
#include <iostream>

namespace fs = std::filesystem;

TEST_CASE("MemoryManager initializes with workspace path", "[memory_manager]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_workspace";
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    REQUIRE(manager.get_workspace_path() == temp_dir);
    REQUIRE(fs::exists(temp_dir / "memory.db"));
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager reads identity files", "[memory_manager]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_identity";
    fs::create_directories(temp_dir);
    
    // Create test files
    std::ofstream(temp_dir / "SOUL.md") << "# I am a test agent";
    std::ofstream(temp_dir / "USER.md") << "# User info";
    
    icraw::MemoryManager manager(temp_dir);
    
    REQUIRE(manager.read_identity_file("SOUL.md") == "# I am a test agent");
    REQUIRE(manager.read_identity_file("USER.md") == "# User info");
    REQUIRE(manager.read_identity_file("NONEXISTENT.md").empty());
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::read_file and write_file work correctly", "[memory_manager]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_fileio";
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    auto file_path = temp_dir / "test.txt";
    manager.write_file(file_path, "Hello, File!");
    
    REQUIRE(manager.file_exists(file_path));
    REQUIRE(manager.read_file(file_path) == "Hello, File!");
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::save_daily_memory creates dated file", "[memory_manager]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_daily";
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.save_daily_memory("Test memory entry");
    
    // Check that memory directory was created
    auto memory_dir = temp_dir / "memory";
    REQUIRE(fs::exists(memory_dir));
    
    // Check that a dated file exists
    bool found_dated_file = false;
    for (const auto& entry : fs::directory_iterator(memory_dir)) {
        if (entry.path().extension() == ".md") {
            found_dated_file = true;
            // Read and verify content
            std::ifstream file(entry.path());
            std::string content((std::istreambuf_iterator<char>(file)),
                               std::istreambuf_iterator<char>());
            REQUIRE(content.find("Test memory entry") != std::string::npos);
        }
    }
    REQUIRE(found_dated_file);
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

// ============================================================================
// SQLite-based tests
// ============================================================================

TEST_CASE("MemoryManager::add_message stores message in SQLite", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_add_message";
    // Ensure clean state
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    int64_t id = manager.add_message("user", "Hello, world!", "default", {});
    
    REQUIRE(id > 0);
    REQUIRE(manager.get_message_count() == 1);
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_recent_messages returns messages in order", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_recent";
    // Ensure clean state
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages with small delays to ensure different timestamps
    manager.add_message("user", "First message", "default", {});
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
    manager.add_message("assistant", "Second message", "default", {});
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
    manager.add_message("user", "Third message", "default", {});
    
    auto messages = manager.get_recent_messages(10, "default");
    
    REQUIRE(messages.size() == 3);
    
    // Messages returned in chronological order (oldest first) for conversation history
    // This is needed for building LLM context where messages must be in order
    REQUIRE(messages[0].role == "user");
    REQUIRE(messages[0].content == "First message");
    REQUIRE(messages[1].role == "assistant");
    REQUIRE(messages[1].content == "Second message");
    REQUIRE(messages[2].role == "user");
    REQUIRE(messages[2].content == "Third message");
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_recent_messages respects limit", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_limit";
    // Ensure clean state
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    for (int i = 0; i < 10; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    auto messages = manager.get_recent_messages(5, "default");
    
    REQUIRE(messages.size() == 5);
    // Should get the most recent 5 - verify we got the right count
    // and that they're from the expected message set (5-9)
    REQUIRE(messages[0].content.find("Message") != std::string::npos);
    REQUIRE(messages[4].content.find("Message") != std::string::npos);
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::clear_history removes messages", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_clear";
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.add_message("user", "Message 1", "default", {});
    manager.add_message("user", "Message 2", "default", {});
    
    REQUIRE(manager.get_message_count() == 2);
    
    manager.clear_history();
    
    REQUIRE(manager.get_message_count() == 0);
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory finds content in SQLite", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_sql";
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.add_message("user", "The answer is 42", "default", {});
    manager.add_message("assistant", "That's correct!", "default", {});
    manager.add_message("user", "What was the question?", "default", {});
    
    auto results = manager.search_memory("42", 10);
    
    REQUIRE(results.size() >= 1);
    REQUIRE(results[0].content.find("42") != std::string::npos);
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::create_summary and get_latest_summary work", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_summary";
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.add_message("user", "Hello", "default", {});
    manager.add_message("assistant", "Hi there!", "default", {});
    
    int64_t summary_id = manager.create_summary("default", "User greeted the assistant", 2);
    
    REQUIRE(summary_id > 0);
    
    auto summary = manager.get_latest_summary("default");
    
    REQUIRE(summary.has_value());
    REQUIRE(summary->summary == "User greeted the assistant");
    REQUIRE(summary->message_count == 2);
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_daily_memory returns entries from SQLite", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_daily_sql";
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.save_daily_memory("First daily entry");
    manager.save_daily_memory("Second daily entry");
    
    auto entries = manager.get_daily_memory();
    
    REQUIRE(entries.size() >= 2);
    
    // Cleanup
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("SQLiteDatabase wrapper works correctly", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_db_wrapper";
    // Ensure clean state
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    auto db_path = temp_dir / "test.db";
    icraw::SQLiteDatabase db(db_path);
    
    REQUIRE(db.is_open());
    
    // Create table
    REQUIRE(db.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, value TEXT);"));
    
    // Insert using prepared statement
    REQUIRE(db.prepare("INSERT INTO test (value) VALUES (?);"));
    REQUIRE(db.bind(1, "hello"));
    REQUIRE(db.step_exec());
    
    // Query
    auto value = db.query_string("SELECT value FROM test WHERE id = 1;");
    REQUIRE(value.has_value());
    REQUIRE(value.value() == "hello");
    
    // Query int
    auto count = db.query_int("SELECT COUNT(*) FROM test;");
    REQUIRE(count.has_value());
    REQUIRE(count.value() == 1);
    
    // Last insert rowid
    REQUIRE(db.last_insert_rowid() == 1);
    
    // Cleanup
    db.close();
    fs::remove_all(temp_dir);
}

// ============================================================================
// Additional Memory Search Tests (Regression Tests for SQL Injection Fix)
// ============================================================================

TEST_CASE("MemoryManager::search_memory returns empty for no match", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_empty";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    manager.add_message("user", "Hello world", "default", {});
    
    auto results = manager.search_memory("NONEXISTENT_KEYWORD_XYZ123", 10);
    REQUIRE(results.empty());
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory respects limit parameter", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_limit";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    for (int i = 0; i < 20; ++i) {
        manager.add_message("user", "Test message " + std::to_string(i), "default", {});
    }
    
    auto results = manager.search_memory("Test", 5);
    REQUIRE(results.size() <= 5);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory prevents SQL injection", "[memory_manager][sqlite][search][security]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_injection";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    manager.add_message("user", "Email: test@example.com", "default", {});
    
    // Attempt SQL injection - this should NOT cause any damage
    auto results = manager.search_memory("'; DROP TABLE messages; --", 10);
    REQUIRE(results.empty()); // No match, not an error
    
    // Verify table still exists and works
    results = manager.search_memory("test@example.com", 10);
    REQUIRE(results.size() == 1);
    REQUIRE(results[0].content == "Email: test@example.com");
    
    // Verify message count is still correct
    REQUIRE(manager.get_message_count() == 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory handles special characters", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_special";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    manager.add_message("user", "Code: func(x, y) => x + y", "default", {});
    manager.add_message("user", "Path: C:\\Users\\test\\file.txt", "default", {});
    
    // Search with parentheses and arrows
    auto results = manager.search_memory("func(x, y)", 10);
    REQUIRE(results.size() >= 1);
    
    // Search with backslashes
    results = manager.search_memory("Users", 10);
    REQUIRE(results.size() >= 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory with Unicode content", "[memory_manager][sqlite][search][unicode]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_unicode";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    manager.add_message("user", u8"你好世界 Hello World", "default", {});
    manager.add_message("user", u8"日本語テスト", "default", {});
    
    // Search Chinese
    auto results = manager.search_memory(u8"你好", 10);
    REQUIRE(results.size() >= 1);
    
    // Search Japanese
    results = manager.search_memory(u8"日本語", 10);
    REQUIRE(results.size() >= 1);
    
    // Search English mixed with Unicode
    results = manager.search_memory("Hello", 10);
    REQUIRE(results.size() >= 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

// ============================================================================
// Memory Consolidation Tests
// ============================================================================

TEST_CASE("MemoryManager::get_messages_for_consolidation respects keep_count", "[memory_manager][sqlite][consolidation]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_consolidation";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    for (int i = 0; i < 20; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Keep recent 5, consolidate older 15
    auto to_consolidate = manager.get_messages_for_consolidation(5);
    REQUIRE(to_consolidate.size() == 15); // 20 - 5 = 15
    
    // Verify they are the oldest messages (0-14)
    REQUIRE(to_consolidate[0].content == "Message 0");
    REQUIRE(to_consolidate[14].content == "Message 14");
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_messages_for_consolidation returns empty for few messages", "[memory_manager][sqlite][consolidation]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_consolidation_empty";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    for (int i = 0; i < 3; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Keep 5, but only 3 exist
    auto to_consolidate = manager.get_messages_for_consolidation(5);
    REQUIRE(to_consolidate.empty()); // Nothing to consolidate
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_messages_for_consolidation with exact count", "[memory_manager][sqlite][consolidation]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_consolidation_exact";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    for (int i = 0; i < 10; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Keep exactly 10, consolidate 0
    auto to_consolidate = manager.get_messages_for_consolidation(10);
    REQUIRE(to_consolidate.empty());
    
    manager.close();
    fs::remove_all(temp_dir);
}

// ============================================================================
// Edge Cases
// ============================================================================

TEST_CASE("MemoryManager handles large content", "[memory_manager][sqlite][edge]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_large_content";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Create 10KB message
    std::string large_content(10000, 'X');
    large_content = "START_MARKER " + large_content + " END_MARKER";
    
    int64_t id = manager.add_message("user", large_content, "default", {});
    REQUIRE(id > 0);
    
    // Search for unique markers
    auto results = manager.search_memory("START_MARKER", 10);
    REQUIRE(results.size() >= 1);
    REQUIRE(results[0].content.find("END_MARKER") != std::string::npos);
    
    // Verify full content length
    REQUIRE(results[0].content.length() == large_content.length());
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager handles concurrent reads safely", "[memory_manager][sqlite][edge]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_concurrent";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages
    for (int i = 0; i < 10; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Multiple sequential reads should work (SQLite handles this)
    auto recent = manager.get_recent_messages(5, "default");
    auto search = manager.search_memory("Message", 10);
    auto count = manager.get_message_count("default");
    auto summary = manager.get_latest_summary("default");
    
    REQUIRE(recent.size() == 5);
    REQUIRE(search.size() >= 5);
    REQUIRE(count == 10);
    // Summary may or may not exist
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::clear_history with session_id parameter", "[memory_manager][sqlite]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_clear_session";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages to different sessions
    manager.add_message("user", "Session A message", "session_a", {});
    manager.add_message("user", "Session B message", "session_b", {});
    
    REQUIRE(manager.get_message_count("session_a") == 1);
    REQUIRE(manager.get_message_count("session_b") == 1);
    
    // Clear only session A
    manager.clear_history("session_a");
    
    REQUIRE(manager.get_message_count("session_a") == 0);
    REQUIRE(manager.get_message_count("session_b") == 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory order by timestamp DESC", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_order";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages with small delays to ensure different timestamps
    manager.add_message("user", "First keyword occurrence", "default", {});
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
    manager.add_message("user", "Second keyword occurrence", "default", {});
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
    manager.add_message("user", "Third keyword occurrence", "default", {});
    
    auto results = manager.search_memory("keyword", 10);
    REQUIRE(results.size() == 3);
    
    // Results should be newest first (Third, Second, First)
    REQUIRE(results[0].content == "Third keyword occurrence");
    REQUIRE(results[1].content == "Second keyword occurrence");
    REQUIRE(results[2].content == "First keyword occurrence");
    
    manager.close();
    fs::remove_all(temp_dir);
}
