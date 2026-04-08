#include "icraw/config.hpp"
#include "icraw/core/agent_loop.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/prompt_builder.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/tools/tool_registry.hpp"

#include <chrono>
#include <filesystem>
#include <functional>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace icraw {
namespace {

class FakeLLMProvider final : public LLMProvider {
public:
    ChatCompletionResponse response;
    int chat_completion_calls = 0;

    ChatCompletionResponse chat_completion(const ChatCompletionRequest& request) override {
        last_request = request;
        ++chat_completion_calls;
        return response;
    }

    void chat_completion_stream(
        const ChatCompletionRequest& request,
        std::function<void(const ChatCompletionResponse&)> callback) override {
        last_request = request;
        ++chat_completion_calls;
        callback(response);
    }

    std::string get_provider_name() const override {
        return "FakeLLMProvider";
    }

    std::vector<std::string> get_supported_models() const override {
        return {"fake-model"};
    }

    void set_http_client(std::unique_ptr<HttpClient> client) override {
        (void) client;
    }

    void cancel_active_request() override {}

    ChatCompletionRequest last_request;
};

class TestFailure : public std::runtime_error {
public:
    explicit TestFailure(const std::string& message)
        : std::runtime_error(message) {}
};

class TempWorkspace {
public:
    TempWorkspace() {
        const auto now = std::chrono::steady_clock::now().time_since_epoch().count();
        path_ = std::filesystem::temp_directory_path() /
                ("icraw-memory-tests-" + std::to_string(now));
        std::filesystem::create_directories(path_);
    }

    ~TempWorkspace() {
        std::error_code ec;
        std::filesystem::remove_all(path_, ec);
    }

    const std::filesystem::path& path() const {
        return path_;
    }

private:
    std::filesystem::path path_;
};

void expect(bool condition, const std::string& message) {
    if (!condition) {
        throw TestFailure(message);
    }
}

void expect_equal(int64_t actual, int64_t expected, const std::string& message) {
    if (actual != expected) {
        throw TestFailure(message + " expected=" + std::to_string(expected) +
                          " actual=" + std::to_string(actual));
    }
}

void expect_equal(size_t actual, size_t expected, const std::string& message) {
    if (actual != expected) {
        throw TestFailure(message + " expected=" + std::to_string(expected) +
                          " actual=" + std::to_string(actual));
    }
}

bool wait_until(const std::function<bool()>& predicate, int timeout_ms) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms);
    while (std::chrono::steady_clock::now() < deadline) {
        if (predicate()) {
            return true;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    return predicate();
}

ToolCall make_save_memory_tool_call(const std::string& history_entry,
                                    const std::string& memory_update) {
    ToolCall call;
    call.id = "call_save_memory";
    call.name = "save_memory";
    call.arguments = nlohmann::json{
        {"history_entry", history_entry},
        {"memory_update", memory_update}
    };
    return call;
}

AgentConfig make_agent_config() {
    AgentConfig config;
    config.model = "fake-model";
    config.max_iterations = 2;
    config.temperature = 0.0;
    config.max_tokens = 512;
    config.memory_window = 4;
    config.consolidation_threshold = 1;
    return config;
}

void seed_messages(MemoryManager& memory_manager,
                   const std::string& session_id,
                   int count,
                   const std::string& prefix) {
    for (int i = 0; i < count; ++i) {
        memory_manager.add_message("user",
                                   prefix + " user " + std::to_string(i),
                                   session_id,
                                   nlohmann::json{});
        memory_manager.add_message("assistant",
                                   prefix + " assistant " + std::to_string(i),
                                   session_id,
                                   nlohmann::json{});
    }
}

void test_clear_long_term_memory_keeps_daily_memory_for_other_sessions() {
    TempWorkspace workspace;
    MemoryManager memory_manager(workspace.path());

    memory_manager.create_summary("session-a", "summary-a", 2);
    memory_manager.create_summary("session-b", "summary-b", 2);
    memory_manager.save_daily_memory("[2026-04-08 15:30] session-b history");

    const auto before_daily = memory_manager.get_daily_memory();
    expect(!before_daily.empty(), "daily memory should be seeded before clear");

    const bool cleared = memory_manager.clear_long_term_memory("session-a");
    expect(cleared, "clear_long_term_memory should report success");

    const auto summary_b = memory_manager.get_latest_summary("session-b");
    expect(summary_b.has_value(), "session-b summary should remain after clearing session-a");

    const auto after_daily = memory_manager.get_daily_memory();
    expect(!after_daily.empty(),
           "session-scoped long-term clear should not delete global daily memory entries");
}

void test_clear_daily_memory_clears_daily_entries_only() {
    TempWorkspace workspace;
    MemoryManager memory_manager(workspace.path());

    memory_manager.create_summary("session-a", "summary-a", 2);
    memory_manager.save_daily_memory("[2026-04-08 16:10] daily log");

    const bool success = memory_manager.clear_daily_memory();
    expect(success, "clear_daily_memory should report success");

    const auto summary = memory_manager.get_latest_summary("session-a");
    expect(summary.has_value(), "clearing daily memory should not delete summaries");
    expect(memory_manager.get_daily_memory().empty(), "daily memory entries should be removed");
}

void test_search_memory_fallback_preserves_session_scope() {
    TempWorkspace workspace;
    MemoryManager memory_manager(workspace.path());

    memory_manager.add_message("user", "apple shared query", "session-a", nlohmann::json{});
    memory_manager.add_message("user", "apple shared query", "session-b", nlohmann::json{});

    // Force search_memory_fts() to take the fallback path.
    expect(memory_manager.database().execute("DROP TABLE IF EXISTS messages_fts;"),
           "should be able to drop FTS table for fallback test");

    const auto session_a_results = memory_manager.search_memory_fts("apple", 10, "session-a");
    const auto session_b_results = memory_manager.search_memory_fts("apple", 10, "session-b");

    expect_equal(session_a_results.size(), static_cast<size_t>(1),
                 "fallback search should only return results from session-a");
    expect_equal(session_b_results.size(), static_cast<size_t>(1),
                 "fallback search should only return results from session-b");
    expect(session_a_results[0].session_id == "session-a",
           "session-a fallback result should preserve session scope");
    expect(session_b_results[0].session_id == "session-b",
           "session-b fallback result should preserve session scope");
}

void test_prompt_builder_injects_only_active_session_summary() {
    TempWorkspace workspace;
    auto memory_manager = std::make_shared<MemoryManager>(workspace.path());
    auto skill_loader = std::make_shared<SkillLoader>();
    auto tool_registry = std::make_shared<ToolRegistry>();
    PromptBuilder prompt_builder(memory_manager, skill_loader, tool_registry);

    memory_manager->create_summary("session-a", "session-a summary", 3);
    memory_manager->create_summary("session-b", "session-b summary", 2);

    const std::string prompt_a = prompt_builder.build_full(SkillsConfig{}, "session-a");
    const std::string prompt_b = prompt_builder.build_full(SkillsConfig{}, "session-b");

    expect(prompt_a.find("session-a summary") != std::string::npos,
           "prompt for session-a should include session-a summary");
    expect(prompt_a.find("session-b summary") == std::string::npos,
           "prompt for session-a should not include session-b summary");
    expect(prompt_b.find("session-b summary") != std::string::npos,
           "prompt for session-b should include session-b summary");
    expect(prompt_b.find("session-a summary") == std::string::npos,
           "prompt for session-b should not include session-a summary");
}

void test_prompt_builder_excludes_daily_memory_log() {
    TempWorkspace workspace;
    auto memory_manager = std::make_shared<MemoryManager>(workspace.path());
    auto skill_loader = std::make_shared<SkillLoader>();
    auto tool_registry = std::make_shared<ToolRegistry>();
    PromptBuilder prompt_builder(memory_manager, skill_loader, tool_registry);

    memory_manager->create_summary("session-a", "summary visible to prompt", 2);
    memory_manager->save_daily_memory("[2026-04-08 16:30] daily memory should stay out of prompt");

    const std::string prompt = prompt_builder.build_full(SkillsConfig{}, "session-a");
    expect(prompt.find("summary visible to prompt") != std::string::npos,
           "prompt should include session summary");
    expect(prompt.find("daily memory should stay out of prompt") == std::string::npos,
           "prompt should not include daily memory log entries");
}

void test_delete_consolidated_messages_refreshes_token_stats() {
    TempWorkspace workspace;
    MemoryManager memory_manager(workspace.path());
    const std::string session_id = "session-token-stats";

    memory_manager.add_message("user", "one two three four", session_id, nlohmann::json{});
    memory_manager.add_message("assistant", "five six seven eight nine", session_id, nlohmann::json{});

    memory_manager.update_token_stats(session_id);
    const int64_t cached_before = memory_manager.get_total_tokens(session_id);
    expect(cached_before > 0, "token stats should be initialized");

    memory_manager.mark_consolidated(1, session_id);
    const int64_t deleted = memory_manager.delete_consolidated_messages(session_id);
    expect_equal(deleted, static_cast<int64_t>(1),
                 "exactly one consolidated message should be deleted");

    const auto remaining = memory_manager.get_recent_messages(10, session_id);
    expect_equal(remaining.size(), static_cast<size_t>(1),
                 "one message should remain after deleting consolidated entries");

    int64_t expected_total = 0;
    for (const auto& entry : remaining) {
        expected_total += entry.token_count;
    }

    const int64_t actual_total = memory_manager.get_total_tokens(session_id);
    expect_equal(actual_total, expected_total,
                 "token stats should match remaining messages after delete_consolidated_messages");
}

void test_non_default_session_consolidation_writes_summary_to_that_session() {
    TempWorkspace workspace;
    auto memory_manager = std::make_shared<MemoryManager>(workspace.path());
    auto skill_loader = std::make_shared<SkillLoader>();
    auto tool_registry = std::make_shared<ToolRegistry>();
    auto llm_provider = std::make_shared<FakeLLMProvider>();
    llm_provider->response.tool_calls.push_back(
        make_save_memory_tool_call("[2026-04-08 15:31] remembered",
                                   "## Facts\n- session-a prefers tea"));

    seed_messages(*memory_manager, "session-a", 2, "session-a");

    AgentLoop agent_loop(memory_manager, skill_loader, tool_registry, llm_provider, make_agent_config());
    agent_loop.maybe_consolidate_memory("session-a", {});

    const bool summary_written = wait_until(
        [&]() {
            return memory_manager->get_latest_summary("session-a").has_value() &&
                   memory_manager->get_message_count("session-a") == 2;
        },
        300);

    expect(summary_written,
           "consolidation should produce long-term summary for the non-default session");
    expect_equal(memory_manager->get_message_count("session-a"),
                 static_cast<int64_t>(2),
                 "compaction should delete consolidated messages and keep only the recent half");
}

struct TestCase {
    const char* name;
    void (*fn)();
};

}  // namespace
}  // namespace icraw

int main() {
    using icraw::TestCase;

    const std::vector<TestCase> tests = {
        {"clear_long_term_memory_keeps_daily_memory_for_other_sessions",
         icraw::test_clear_long_term_memory_keeps_daily_memory_for_other_sessions},
        {"clear_daily_memory_clears_daily_entries_only",
         icraw::test_clear_daily_memory_clears_daily_entries_only},
        {"search_memory_fallback_preserves_session_scope",
         icraw::test_search_memory_fallback_preserves_session_scope},
        {"prompt_builder_injects_only_active_session_summary",
         icraw::test_prompt_builder_injects_only_active_session_summary},
        {"prompt_builder_excludes_daily_memory_log",
         icraw::test_prompt_builder_excludes_daily_memory_log},
        {"delete_consolidated_messages_refreshes_token_stats",
         icraw::test_delete_consolidated_messages_refreshes_token_stats},
        {"non_default_session_consolidation_writes_summary_to_that_session",
         icraw::test_non_default_session_consolidation_writes_summary_to_that_session},
    };

    int failed = 0;
    for (const auto& test : tests) {
        try {
            test.fn();
            std::cout << "[PASS] " << test.name << '\n';
        } catch (const std::exception& e) {
            ++failed;
            std::cerr << "[FAIL] " << test.name << ": " << e.what() << '\n';
        }
    }

    if (failed > 0) {
        std::cerr << failed << " test(s) failed\n";
        return 1;
    }

    std::cout << "All memory system tests passed\n";
    return 0;
}
