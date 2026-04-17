#include "icraw/core/agent_loop.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/core/token_utils.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"

#include <algorithm>
#include <chrono>
#include <ctime>
#include <future>
#include <iomanip>
#include <sstream>

namespace icraw {
namespace {

constexpr int CONTEXT_RECENT_TOOL_RESULT_MAX_CHARS = 32000;
constexpr size_t CONTEXT_DETAILED_TOOL_RESULT_COUNT = 3;

int count_memory_tool_result_messages(const std::vector<Message>& messages) {
    int count = 0;
    for (const auto& message : messages) {
        if (message.role != "tool") {
            continue;
        }
        for (const auto& block : message.content) {
            if (block.type == "tool_result") {
                count++;
            }
        }
    }
    return count;
}

} // namespace
void AgentLoop::maybe_consolidate_memory(const std::string& session_id,
                                         const std::vector<Message>& messages) {
    const std::string effective_session_id = session_id.empty() ? "default" : session_id;
    (void)messages;
    // Check if consolidation is needed
    int message_count = memory_manager_ ? memory_manager_->get_message_count(effective_session_id) : 0;
    int threshold = agent_config_.consolidation_threshold;

    ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] stage=check session_id={} message_count={} threshold={}",
            effective_session_id, message_count, threshold);

    if (message_count > threshold) {
        // Check if previous consolidation is still running
        if (consolidation_future_.valid() &&
            consolidation_future_.wait_for(std::chrono::seconds(0)) != std::future_status::ready) {
            ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] stage=skip session_id={} reason=previous_task_running",
                    effective_session_id);
            return;
        }

        ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_start] mode=async session_id={} message_count={} threshold={}",
            effective_session_id, message_count, threshold);

        // Launch async compaction using the session-scoped message set.
        auto target_session_id = effective_session_id;
        auto* self = this;

        consolidation_future_ = std::async(std::launch::async,
            [self, target_session_id]() {
                if (!self || !self->memory_manager_ || !self->llm_provider_) {
                    return false;
                }

                const int keep_count = self->agent_config_.memory_window / 2;
                auto old_messages = self->memory_manager_->get_messages_for_consolidation(keep_count, target_session_id);

                if (old_messages.empty()) {
                    ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=async stage=no_messages session_id={} keep_count={}",
                            target_session_id, keep_count);
                    return true;
                }

                ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_dispatch] mode=async session_id={} old_message_count={} keep_count={}",
                        target_session_id, old_messages.size(), keep_count);

                const CompactionResult result =
                    self->execute_memory_compaction(target_session_id, old_messages);
                const bool success = result == CompactionResult::Success ||
                                     result == CompactionResult::PartialSuccess;

                ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_complete] mode=async session_id={} result={}",
                        target_session_id, compaction_result_to_string(result));

                if (!success && result != CompactionResult::Fallback) {
                    ICRAW_LOG_WARN("[AgentLoop][memory_consolidation_failed] mode=async session_id={} result={}",
                            target_session_id, compaction_result_to_string(result));
                } else if (result == CompactionResult::Fallback) {
                    ICRAW_LOG_WARN("[AgentLoop][memory_consolidation_fallback] mode=async session_id={}",
                            target_session_id);
                }

                return success || result == CompactionResult::Fallback;
            });
    }
}

bool AgentLoop::perform_consolidation(const std::string& session_id,
                                      const std::vector<Message>& messages) {
    (void)messages; // Suppress unused parameter warning
    const std::string effective_session_id = session_id.empty() ? "default" : session_id;

    if (!memory_manager_ || !llm_provider_) {
        ICRAW_LOG_WARN("[AgentLoop][memory_consolidation_failed] mode=sync session_id={} reason=dependencies_unavailable",
                effective_session_id);
        return false;
    }

    // Get messages to consolidate
    int keep_count = agent_config_.memory_window / 2;
    auto old_messages = memory_manager_->get_messages_for_consolidation(keep_count, effective_session_id);

    if (old_messages.empty()) {
        ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=no_messages session_id={}",
                effective_session_id);
        return true;
    }

    ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_start] mode=sync session_id={} message_count={}",
            effective_session_id, old_messages.size());

    // Format messages for LLM - use simple string concatenation
    std::string conversation;
    for (const auto& msg : old_messages) {
        std::string tools_used;
        if (msg.metadata.contains("tools_used")) {
            auto tools = msg.metadata["tools_used"];
            for (const auto& t : tools) {
                if (!tools_used.empty()) tools_used += ", ";
                tools_used += t.get<std::string>();
            }
            if (!tools_used.empty()) {
                tools_used = " [tools: " + tools_used + "]";
            }
        }
        conversation += "[" + msg.timestamp.substr(0, 16) + "] "
                    + msg.role + tools_used + ": "
                    + msg.content + "\n";
    }

    // Get current long-term memory
    auto latest_summary = memory_manager_->get_latest_summary(effective_session_id);
    std::string current_memory = latest_summary ? latest_summary->summary : "(empty)";

    // Build prompt for LLM
    std::string system_prompt = "You are a memory consolidation agent. Call the save_memory tool to save important information from the conversation.";
    std::string user_prompt = "Process this conversation and call the save_memory tool with your consolidation.\n\n"
                           "## Current Long-term Memory\n" + current_memory + "\n\n"
                           "## Conversation to Process\n" + conversation;

    // Create tool definition for save_memory
    nlohmann::json save_memory_tool;
    save_memory_tool["type"] = "function";
    save_memory_tool["function"]["name"] = "save_memory";
    save_memory_tool["function"]["description"] = "Save the memory consolidation result to persistent storage.";
    nlohmann::json params_obj;
    params_obj["type"] = "object";
    params_obj["properties"]["history_entry"] = nlohmann::json{{"type", "string"}, {"description", "A paragraph (2-5 sentences) summarizing key events/decisions/topics. Start with [YYYY-MM-DD HH:MM]."}};
    params_obj["properties"]["memory_update"] = nlohmann::json{{"type", "string"}, {"description", "Full updated long-term memory as markdown. Include all existing facts plus new ones."}};
    params_obj["required"] = nlohmann::json{"history_entry", "memory_update"};
    save_memory_tool["function"]["parameters"] = params_obj;

    // Create chat request
    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = agent_config_.temperature;
    request.max_tokens = agent_config_.max_tokens;
    request.enable_thinking = agent_config_.enable_thinking;
    request.messages.emplace_back("system", system_prompt);
    request.messages.emplace_back("user", user_prompt);
    request.tools.push_back(save_memory_tool);
    request.tool_choice_auto = true;

    // Log the consolidation request
    ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=request_llm session_id={}",
            effective_session_id);

    // Call LLM (non-streaming for consolidation)
    auto response = llm_provider_->chat_completion(request);

    // Check if LLM called the save_memory tool
    if (response.tool_calls.empty()) {
        ICRAW_LOG_WARN("[AgentLoop][memory_consolidation_failed] mode=sync session_id={} reason=missing_save_memory_tool",
                effective_session_id);
        return false;
    }

    // Parse and save the result
    try {
        auto args = response.tool_calls[0].arguments;

        // Handle both string and object arguments
        std::string history_entry;
        std::string memory_update;

        if (args.is_string()) {
            auto parsed = nlohmann::json::parse(args.get<std::string>());
            history_entry = parsed.value("history_entry", "");
            memory_update = parsed.value("memory_update", "");
        } else if (args.is_object()) {
            history_entry = args.value("history_entry", "");
            memory_update = args.value("memory_update", "");
        }

        if (!history_entry.empty()) {
            // Save to daily_memory (like mobile-agent's HISTORY.md)
            memory_manager_->save_daily_memory(history_entry);
            ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=save_history_entry session_id={} content_length={} preview={}",
                    effective_session_id, history_entry.size(), log_utils::truncate_for_debug(history_entry));
        }

        if (!memory_update.empty() && memory_update != current_memory) {
            // Save summary to summaries table
            memory_manager_->create_summary(effective_session_id, memory_update, static_cast<int>(old_messages.size()));
            ICRAW_LOG_DEBUG("[AgentLoop][memory_consolidation_debug] mode=sync stage=save_memory_update session_id={} content_length={} preview={}",
                    effective_session_id, memory_update.size(), log_utils::truncate_for_debug(memory_update));
        }

        ICRAW_LOG_INFO("[AgentLoop][memory_consolidation_complete] mode=sync session_id={}",
                effective_session_id);
        return true;

    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[AgentLoop][memory_consolidation_failed] mode=sync session_id={} message={}",
                effective_session_id, e.what());
        return false;
    }
}

// --- New Memory Management Methods ---

bool AgentLoop::should_flush_memory() const {
    if (!memory_manager_) {
        return false;
    }

    // Check if memory flush is enabled
    if (!agent_config_.compaction.memory_flush.enabled) {
        return false;
    }

    // Don't flush if we already flushed since last compaction
    int compaction_count = memory_manager_->get_compaction_count();
    if (last_flush_compaction_count_ == compaction_count) {
        return false;
    }

    return memory_manager_->needs_memory_flush(agent_config_.compaction);
}

bool AgentLoop::execute_memory_flush() {
    if (!memory_manager_ || !llm_provider_) {
        return false;
    }

    ICRAW_LOG_INFO("[AgentLoop][memory_flush_start]");

    // Get current time for prompt
    auto now = std::chrono::system_clock::now();
    auto now_time = std::chrono::system_clock::to_time_t(now);
    std::tm tm = *std::gmtime(&now_time);
    std::ostringstream time_ss;
    time_ss << std::put_time(&tm, "%Y-%m-%d %H:%M:%S UTC");
    std::string current_time = time_ss.str();

    // Build prompts
    std::string system_prompt = agent_config_.compaction.memory_flush.system_prompt;
    std::string user_prompt = agent_config_.compaction.memory_flush.user_prompt;

    // Replace placeholder
    size_t pos = user_prompt.find("{current_datetime}");
    if (pos != std::string::npos) {
        user_prompt.replace(pos, 17, current_time);
    }

    // Create save_memory tool
    nlohmann::json save_memory_tool;
    save_memory_tool["type"] = "function";
    save_memory_tool["function"]["name"] = "save_memory";
    save_memory_tool["function"]["description"] = "Save important information to long-term memory.";
    nlohmann::json params;
    params["type"] = "object";
    params["properties"]["content"] = nlohmann::json{
        {"type", "string"},
        {"description", "The memory content to save"}
    };
    params["required"] = nlohmann::json::array({"content"});
    save_memory_tool["function"]["parameters"] = params;

    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = 0.3;  // Lower temperature for memory extraction
    request.max_tokens = 1024;
    request.enable_thinking = agent_config_.enable_thinking;
    request.messages.emplace_back("system", system_prompt);
    request.messages.emplace_back("user", user_prompt);
    request.tools.push_back(save_memory_tool);
    request.tool_choice_auto = true;

    try {
        auto response = llm_provider_->chat_completion(request);

        // Process save_memory tool calls
        for (const auto& tc : response.tool_calls) {
            if (tc.name == "save_memory") {
                auto args = tc.arguments;
                std::string content;

                if (args.is_string()) {
                    auto parsed = nlohmann::json::parse(args.get<std::string>());
                    content = parsed.value("content", "");
                } else if (args.is_object()) {
                    content = args.value("content", "");
                }

                if (!content.empty()) {
                    memory_manager_->save_daily_memory(content);
                    ICRAW_LOG_DEBUG("[AgentLoop][memory_flush_debug] content_length={} preview={}",
                            content.size(), log_utils::truncate_for_debug(content));
                }
            }
        }

        // Record flush execution
        memory_manager_->record_memory_flush();
        last_flush_compaction_count_ = memory_manager_->get_compaction_count();

        ICRAW_LOG_INFO("[AgentLoop][memory_flush_complete]");
        return true;

    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[AgentLoop][memory_flush_failed] message={}", e.what());
        return false;
    }
}

bool AgentLoop::should_compact() const {
    if (!memory_manager_) {
        return false;
    }

    int64_t total_tokens = memory_manager_->get_total_tokens();
    return should_trigger_compaction(static_cast<int>(total_tokens), agent_config_.compaction);
}

int AgentLoop::get_current_token_count() const {
    if (!memory_manager_) {
        return 0;
    }
    return static_cast<int>(memory_manager_->get_total_tokens());
}

MemoryMetrics AgentLoop::get_memory_metrics() const {
    MemoryMetrics metrics;

    if (!memory_manager_) {
        return metrics;
    }

    metrics.total_messages = memory_manager_->get_message_count();
    metrics.context_tokens = get_current_token_count();
    metrics.compaction_count = static_cast<int>(memory_manager_->get_compaction_count());

    auto stats = memory_manager_->get_token_stats();
    if (stats) {
        // Could add more stats here
    }

    auto latest_compaction = memory_manager_->get_latest_compaction();
    if (latest_compaction) {
        if (latest_compaction->tokens_before > 0) {
            metrics.compression_ratio = static_cast<double>(latest_compaction->tokens_after) /
                                        latest_compaction->tokens_before;
        }
    }

    return metrics;
}

CompactionResult AgentLoop::execute_memory_compaction(
    const std::string& session_id,
    const std::vector<MemoryEntry>& messages) {
    const std::string effective_session_id = session_id.empty() ? "default" : session_id;

    if (messages.empty()) {
        return CompactionResult::Success;
    }

    ICRAW_LOG_INFO("[AgentLoop][compaction_start] session_id={} message_count={}", effective_session_id, messages.size());

    // Try full compaction first
    try {
        // Chunk messages if too large
        auto chunks = chunk_messages_by_tokens(messages, agent_config_.compaction.max_chunk_tokens);

        std::string combined_summary;
        int total_tokens_before = estimate_memory_entries_tokens(messages);
        int64_t first_kept_id = messages.empty() ? 0 : messages.back().id;

        for (size_t i = 0; i < chunks.size(); ++i) {
            ICRAW_LOG_DEBUG("[AgentLoop][compaction_debug] stage=chunk session_id={} index={} total={} message_count={}",
                effective_session_id, i + 1, chunks.size(), chunks[i].size());

            auto current_summary = memory_manager_->get_latest_summary(effective_session_id);
            std::string summary_str = current_summary ? current_summary->summary : "";

            std::string prompt = build_consolidation_prompt(chunks[i], summary_str);

            // Create save_memory tool
            nlohmann::json save_memory_tool;
            save_memory_tool["type"] = "function";
            save_memory_tool["function"]["name"] = "save_memory";
            save_memory_tool["function"]["description"] = "Save the memory consolidation result.";
            nlohmann::json params;
            params["type"] = "object";
            params["properties"]["history_entry"] = nlohmann::json{
                {"type", "string"},
                {"description", "A paragraph (2-5 sentences) summarizing key events/decisions/topics. Start with [YYYY-MM-DD HH:MM]."}
            };
            params["properties"]["memory_update"] = nlohmann::json{
                {"type", "string"},
                {"description", "Full updated long-term memory as markdown. Include all existing facts plus new ones."}
            };
            params["required"] = nlohmann::json::array({"history_entry", "memory_update"});
            save_memory_tool["function"]["parameters"] = params;

            ChatCompletionRequest request;
            request.model = agent_config_.model;
            request.temperature = 0.3;
            request.max_tokens = agent_config_.max_tokens;
            request.enable_thinking = agent_config_.enable_thinking;
            request.messages.emplace_back("system",
                "You are a memory consolidation agent. Call the save_memory tool to save important information.");
            request.messages.emplace_back("user", prompt);
            request.tools.push_back(save_memory_tool);
            request.tool_choice_auto = true;

            auto response = llm_provider_->chat_completion(request);

            if (response.tool_calls.empty()) {
                ICRAW_LOG_WARN("[AgentLoop][compaction_failed] session_id={} reason=missing_save_memory_tool chunk_index={}",
                        effective_session_id, i + 1);
                continue;
            }

            auto args = response.tool_calls[0].arguments;
            std::string history_entry;
            std::string memory_update;

            if (args.is_string()) {
                auto parsed = nlohmann::json::parse(args.get<std::string>());
                history_entry = parsed.value("history_entry", "");
                memory_update = parsed.value("memory_update", "");
            } else if (args.is_object()) {
                history_entry = args.value("history_entry", "");
                memory_update = args.value("memory_update", "");
            }

            if (!history_entry.empty()) {
                memory_manager_->save_daily_memory(history_entry);
                ICRAW_LOG_DEBUG("[AgentLoop][compaction_debug] stage=save_history_entry session_id={} chunk_index={} content_length={} preview={}",
                        effective_session_id, i + 1, history_entry.size(), log_utils::truncate_for_debug(history_entry));
            }

            if (!memory_update.empty()) {
                combined_summary = memory_update;
                ICRAW_LOG_DEBUG("[AgentLoop][compaction_debug] stage=save_memory_update session_id={} chunk_index={} content_length={} preview={}",
                        effective_session_id, i + 1, memory_update.size(), log_utils::truncate_for_debug(memory_update));
            }

            if (!chunks[i].empty()) {
                first_kept_id = std::min(first_kept_id, chunks[i].back().id);
            }
        }

        if (combined_summary.empty()) {
            ICRAW_LOG_WARN("[AgentLoop][compaction_failed] session_id={} reason=empty_combined_summary", effective_session_id);
            return CompactionResult::Failed;
        }

        // Save final summary
        memory_manager_->create_summary(effective_session_id, combined_summary,
            static_cast<int>(messages.size()));

        // Mark messages as consolidated
        memory_manager_->mark_consolidated(static_cast<int>(messages.size()), effective_session_id);

        // Create compaction record
        int total_tokens_after = estimate_tokens(combined_summary);
        memory_manager_->create_compaction_record(effective_session_id, combined_summary,
            first_kept_id, total_tokens_before, total_tokens_after, "full");

        // Update token stats
        memory_manager_->update_token_stats(effective_session_id);

        // Delete compacted messages now that the summary has been persisted.
        const int64_t deleted_count = memory_manager_->delete_consolidated_messages(effective_session_id);
        ICRAW_LOG_INFO("[AgentLoop][compaction_cleanup_complete] session_id={} deleted_count={}",
                effective_session_id, deleted_count);

        ICRAW_LOG_INFO("[AgentLoop][compaction_complete] session_id={} tokens_before={} tokens_after={} reduction_percent={:.1f}",
            effective_session_id, total_tokens_before, total_tokens_after,
            100.0 * (1.0 - static_cast<double>(total_tokens_after) / total_tokens_before));

        return CompactionResult::Success;

    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[AgentLoop][compaction_failed] session_id={} message={}", effective_session_id, e.what());

        // Fallback: just record metadata
        try {
            // Generate timestamp locally
            auto now = std::chrono::system_clock::now();
            auto now_time = std::chrono::system_clock::to_time_t(now);
            std::tm tm = *std::gmtime(&now_time);
            std::ostringstream time_ss;
            time_ss << std::put_time(&tm, "%Y-%m-%dT%H:%M:%SZ");

            std::string fallback_summary = "Compaction failed at " + time_ss.str() +
                ". Messages: " + std::to_string(messages.size());
            memory_manager_->create_summary(effective_session_id, fallback_summary,
                static_cast<int>(messages.size()));

            return CompactionResult::Fallback;
        } catch (...) {
            return CompactionResult::Failed;
        }
    }
}

std::string AgentLoop::build_consolidation_prompt(
    const std::vector<MemoryEntry>& messages,
    const std::string& current_summary) const {

    std::ostringstream ss;

    ss << "Process this conversation and call the save_memory tool with your consolidation.\n\n";

    // Add identifier preservation instructions
    ss << get_identifier_preservation_prompt(agent_config_.compaction.identifier_policy);

    ss << "\n## Current Long-term Memory\n";
    ss << (current_summary.empty() ? "(empty)" : current_summary) << "\n\n";

    ss << "## Conversation to Process\n";
    for (const auto& msg : messages) {
        std::string tools_used;
        if (msg.metadata.contains("tools_used")) {
            auto tools = msg.metadata["tools_used"];
            for (const auto& t : tools) {
                if (!tools_used.empty()) tools_used += ", ";
                tools_used += t.get<std::string>();
            }
            if (!tools_used.empty()) {
                tools_used = " [tools: " + tools_used + "]";
            }
        }

        std::string timestamp = msg.timestamp;
        if (timestamp.length() >= 16) {
            timestamp = timestamp.substr(0, 16);
        }

        ss << "[" << timestamp << "] " << msg.role << tools_used << ": "
           << msg.content << "\n";
    }

    return ss.str();
}

void AgentLoop::prune_context_tool_results(std::vector<Message>& messages, int max_chars) {
    const int total_tool_result_messages = count_memory_tool_result_messages(messages);
    if (total_tool_result_messages <= static_cast<int>(CONTEXT_DETAILED_TOOL_RESULT_COUNT)) {
        return;
    }

    int tool_result_index = 0;
    size_t total_before = 0;
    size_t total_after = 0;

    for (auto& msg : messages) {
        if (msg.role != "tool") {
            continue;
        }
        for (auto& block : msg.content) {
            if (block.type != "tool_result") {
                continue;
            }
            tool_result_index++;
            const bool keep_detailed =
                    (total_tool_result_messages - tool_result_index) < static_cast<int>(CONTEXT_DETAILED_TOOL_RESULT_COUNT);
            const int limit = keep_detailed ? CONTEXT_RECENT_TOOL_RESULT_MAX_CHARS : max_chars;
            total_before += block.content.size();
            block.content = prune_tool_result(block.content, limit);
            total_after += block.content.size();
        }
    }

    if (total_after < total_before) {
        ICRAW_LOG_INFO(
                "[AgentLoop][context_tool_results_pruned] tool_result_count={} original_chars={} pruned_chars={}",
                total_tool_result_messages,
                total_before,
                total_after);
    }
}


} // namespace icraw

