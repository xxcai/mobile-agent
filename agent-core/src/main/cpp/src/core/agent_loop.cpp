#include "icraw/core/agent_loop.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/tools/tool_registry.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/core/logger.hpp"
#include "icraw/core/token_utils.hpp"
#include <algorithm>
#include <random>
#include <set>
#include <sstream>
#include <iomanip>
#include <ctime>
#include <chrono>

namespace icraw {

// Helper to generate unique tool call IDs (must be defined before use)
static std::string generate_tool_id() {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    static std::uniform_int_distribution<> dis(0, 15);
    
    std::string id = "toolu_";
    for (int i = 0; i < 24; ++i) {
        int val = dis(gen);
        id += (val < 10) ? ('0' + val) : ('a' + val - 10);
    }
    return id;
}

AgentLoop::AgentLoop(std::shared_ptr<MemoryManager> memory_manager,
                     std::shared_ptr<SkillLoader> skill_loader,
                     std::shared_ptr<ToolRegistry> tool_registry,
                     std::shared_ptr<LLMProvider> llm_provider,
                     const AgentConfig& agent_config)
    : memory_manager_(std::move(memory_manager))
    , skill_loader_(std::move(skill_loader))
    , tool_registry_(std::move(tool_registry))
    , llm_provider_(std::move(llm_provider))
    , agent_config_(agent_config)
    , max_iterations_(agent_config.max_iterations) {
}

std::vector<Message> AgentLoop::process_message(const std::string& message,
                                                  const std::vector<Message>& history,
                                                  const std::string& system_prompt) {
    std::vector<Message> new_messages;
    stop_requested_ = false;
    
    // Build request
    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = agent_config_.temperature;
    request.max_tokens = agent_config_.max_tokens;
    request.stream = false;
    
    // Add system message
    request.messages.emplace_back("system", system_prompt);
    
    // Add history
    for (const auto& msg : history) {
        request.messages.push_back(msg);
    }
    
    // Add user message
    request.messages.emplace_back("user", message);
    
    // Add tool schemas
    auto tool_schemas = tool_registry_->get_tool_schemas();
    for (const auto& schema : tool_schemas) {
        nlohmann::json tool;
        tool["type"] = "function";
        tool["function"]["name"] = schema.name;
        tool["function"]["description"] = schema.description;
        tool["function"]["parameters"] = schema.parameters;
        request.tools.push_back(tool);
    }
    
    // Agent loop
    int iteration = 0;
    while (iteration < max_iterations_ && !stop_requested_) {
        iteration++;
        
        // Call LLM
        auto response = llm_provider_->chat_completion(request);
        
        // Build assistant message
        Message assistant_msg;
        assistant_msg.role = "assistant";
        
        if (!response.content.empty()) {
            assistant_msg.content.push_back(ContentBlock::make_text(response.content));
        }
        
        // Add tool_calls as separate field (OpenAI format)
        for (const auto& tc : response.tool_calls) {
            std::string tool_id = tc.id.empty() ? generate_tool_id() : tc.id;
            ToolCallForMessage tc_msg;
            tc_msg.id = tool_id;
            tc_msg.type = "function";
            tc_msg.function_name = tc.name;
            tc_msg.function_arguments = tc.arguments.is_string() 
                ? tc.arguments.get<std::string>() 
                : tc.arguments.dump();
            assistant_msg.tool_calls.push_back(std::move(tc_msg));
        }
        
        new_messages.push_back(assistant_msg);
        request.messages.push_back(assistant_msg);
        
        // Check if we're done
        if (response.tool_calls.empty() || response.finish_reason == "end_turn") {
            break;
        }
        
        // Execute tool calls
        auto tool_results = handle_tool_calls(response.tool_calls);
        
        // Add tool results
        for (const auto& result : tool_results) {
            Message tool_msg;
            tool_msg.role = "tool";
            tool_msg.tool_call_id = result.tool_use_id;
            tool_msg.content.push_back(ContentBlock::make_tool_result(result.tool_use_id, result.content));
            new_messages.push_back(tool_msg);
            request.messages.push_back(tool_msg);
        }
    }
    
    return new_messages;
}

std::vector<Message> AgentLoop::process_message_stream(const std::string& message,
                                                        const std::vector<Message>& history,
                                                        const std::string& system_prompt,
                                                        AgentEventCallback callback) {
    std::vector<Message> new_messages;
    stop_requested_ = false;
    
    // Build request
    ChatCompletionRequest request;
    request.model = agent_config_.model;
    request.temperature = agent_config_.temperature;
    request.max_tokens = agent_config_.max_tokens;
    request.stream = true;
    
    // Add system message
    request.messages.emplace_back("system", system_prompt);
    
    // Add history
    for (const auto& msg : history) {
        request.messages.push_back(msg);
    }
    
    // Add user message
    request.messages.emplace_back("user", message);
    
    // Add tool schemas
    auto tool_schemas = tool_registry_->get_tool_schemas();
    for (const auto& schema : tool_schemas) {
        nlohmann::json tool;
        tool["type"] = "function";
        tool["function"]["name"] = schema.name;
        tool["function"]["description"] = schema.description;
        tool["function"]["parameters"] = schema.parameters;
        request.tools.push_back(tool);
    }
    
    // Agent loop
    int iteration = 0;
    auto loop_start_time = std::chrono::steady_clock::now();
    ICRAW_LOG_DEBUG("[AGENT_LOOP] Starting agent loop, max_iterations={}", max_iterations_);
    while (iteration < max_iterations_ && !stop_requested_) {
        iteration++;
        auto iter_start_time = std::chrono::steady_clock::now();
        ICRAW_LOG_DEBUG("[AGENT_LOOP] Iteration {} started", iteration);
        
        // Reset state for this iteration
        last_finish_reason_.clear();
        
        // === Accumulator Pattern ===
        // StreamParser handles tool call accumulation internally
        // We only accumulate text here for real-time display
        std::string accumulated_text;
        std::vector<ToolCall> final_tool_calls;
        bool stream_complete = false;
        
        ICRAW_LOG_DEBUG("[AGENT_LOOP] Starting chat_completion_stream for iteration {}", iteration);
        
        // Stream LLM response
        llm_provider_->chat_completion_stream(request, 
            [&](const ChatCompletionResponse& chunk) {
                // Accumulate and emit text delta
                if (!chunk.content.empty()) {
                    accumulated_text += chunk.content;
                    
                    AgentEvent event;
                    event.type = "text_delta";
                    event.data["delta"] = chunk.content;
                    callback(event);
                }
                
                // When stream ends, StreamParser provides complete tool calls
                if (chunk.is_stream_end) {
                    ICRAW_LOG_DEBUG("[AGENT_LOOP] Stream end: finish_reason='{}', tool_calls={}", 
                        chunk.finish_reason, chunk.tool_calls.size());
                    stream_complete = true;
                    final_tool_calls = chunk.tool_calls;  // Already accumulated by StreamParser
                    last_finish_reason_ = chunk.finish_reason;
                    
                    AgentEvent event;
                    event.type = "message_end";
                    event.data["finish_reason"] = chunk.finish_reason;
                    callback(event);
                }
            });
        
        ICRAW_LOG_DEBUG("[AGENT_LOOP] Stream complete: stream_complete={}, text_len={}, tool_calls={}", 
            stream_complete, accumulated_text.length(), final_tool_calls.size());
        
        // === Deferred Processing ===
        // StreamParser has already accumulated and validated tool calls
        // Build assistant message with accumulated content
        Message assistant_msg;
        assistant_msg.role = "assistant";
        
        if (!accumulated_text.empty()) {
            assistant_msg.content.push_back(ContentBlock::make_text(accumulated_text));
        }
        
        // Process tool calls - StreamParser already accumulated and validated them
        std::vector<ToolCall> valid_tool_calls;
        for (const auto& tc : final_tool_calls) {
            // Validate tool call has both name AND valid arguments
            if (tc.name.empty() || tc.arguments.is_null()) {
                ICRAW_LOG_WARN("Skipping incomplete tool call: name='{}', has_arguments={}", 
                    tc.name, !tc.arguments.is_null());
                continue;
            }
            
            // StreamParser already parsed arguments, just validate
            nlohmann::json parsed_args;
            
            if (tc.arguments.is_string()) {
                try {
                    std::string args_str = tc.arguments.get<std::string>();
                    parsed_args = nlohmann::json::parse(args_str);
                } catch (...) {
                    ICRAW_LOG_WARN("Skipping tool call with invalid arguments: name='{}'", tc.name);
                    continue;
                }
            } else if (tc.arguments.is_object()) {
                parsed_args = tc.arguments;
            } else {
                continue;
            }
            
            ToolCall valid_tc = tc;
            valid_tc.arguments = parsed_args;
            valid_tool_calls.push_back(valid_tc);
            
            ICRAW_LOG_DEBUG("Valid tool call: name={}, id={}", valid_tc.name, valid_tc.id);
        }
        
        // Add valid tool calls to assistant message (OpenAI format)
        for (const auto& tc : valid_tool_calls) {
            std::string tool_id = tc.id.empty() ? generate_tool_id() : tc.id;
            ToolCallForMessage tc_msg;
            tc_msg.id = tool_id;
            tc_msg.type = "function";
            tc_msg.function_name = tc.name;
            tc_msg.function_arguments = tc.arguments.is_string() 
                ? tc.arguments.get<std::string>() 
                : tc.arguments.dump();
            assistant_msg.tool_calls.push_back(std::move(tc_msg));
            
            // Emit tool_use event
            AgentEvent event;
            event.type = "tool_use";
            event.data["id"] = tool_id;
            event.data["name"] = tc.name;
            event.data["input"] = tc.arguments;
            callback(event);
        }
        
        new_messages.push_back(assistant_msg);
        request.messages.push_back(assistant_msg);
        
        // === Deduplication ===
        // Remove duplicate tool calls (same name + arguments)
        std::vector<ToolCall> deduplicated_tool_calls;
        std::set<std::pair<std::string, std::string>> seen_tool_signatures;
        
        for (const auto& tc : valid_tool_calls) {
            // Create signature from tool name and normalized arguments
            std::string args_str = tc.arguments.is_string() 
                ? tc.arguments.get<std::string>() 
                : tc.arguments.dump();
            
            // Normalize JSON by re-parsing and re-dumping to catch equivalent JSON
            try {
                auto args_json = nlohmann::json::parse(args_str);
                args_str = args_json.dump();  // Normalize JSON format
            } catch (...) {
                // If not valid JSON, use as-is
            }
            
            std::pair<std::string, std::string> signature = {tc.name, args_str};
            
            if (seen_tool_signatures.find(signature) == seen_tool_signatures.end()) {
                seen_tool_signatures.insert(signature);
                deduplicated_tool_calls.push_back(tc);
            } else {
                ICRAW_LOG_DEBUG("Skipping duplicate tool call: name={}, args={}", tc.name, args_str);
            }
        }
        
        ICRAW_LOG_DEBUG("Tool call deduplication: {} -> {} (removed {} duplicates)", 
            valid_tool_calls.size(), deduplicated_tool_calls.size(), 
            valid_tool_calls.size() - deduplicated_tool_calls.size());
        
        // Use deduplicated tool calls
        valid_tool_calls = std::move(deduplicated_tool_calls);
        
        // === Decision Point ===
        // Check if we should continue the loop or exit
        ICRAW_LOG_DEBUG("Decision: valid_tool_calls={}, finish_reason='{}'", 
            valid_tool_calls.size(), last_finish_reason_);
        
        if (valid_tool_calls.empty()) {
            // No valid tool calls - check finish_reason to decide
            if (last_finish_reason_ == "stop" || last_finish_reason_ == "end_turn") {
                ICRAW_LOG_DEBUG("Exiting loop: finish_reason={}", last_finish_reason_);
                break;  // Exit loop - LLM is done
            }
            
            // For text-only responses (no tool calls), also exit the loop
            // The LLM has finished its response
            ICRAW_LOG_DEBUG("Exiting loop: text-only response");
            break;
        }
        // If we have valid tool calls, execute them and loop continues
        
        // Execute valid tool calls
        ICRAW_LOG_DEBUG("Executing {} valid tool call(s)", valid_tool_calls.size());
        auto tool_results = handle_tool_calls(valid_tool_calls);
        
        // Add tool results
        for (const auto& result : tool_results) {
            Message tool_msg;
            tool_msg.role = "tool";
            tool_msg.tool_call_id = result.tool_use_id;
            tool_msg.content.push_back(ContentBlock::make_tool_result(result.tool_use_id, result.content));
            new_messages.push_back(tool_msg);
            request.messages.push_back(tool_msg);
            
            // Debug: Log tool result
            // Parse result JSON to check for success/failure
            try {
                auto result_json = nlohmann::json::parse(result.content);
                if (result_json.value("success", false)) {
                    ICRAW_LOG_DEBUG("Tool call succeeded: tool_use_id={}, bytes_written={}", 
                        result.tool_use_id, result_json.value("bytes_written", 0));
                } else {
                    ICRAW_LOG_WARN("Tool call FAILED: tool_use_id={}, error={}", 
                        result.tool_use_id, result_json.value("error", "unknown error"));
                }
            } catch (...) {
                ICRAW_LOG_DEBUG("Tool call result: tool_use_id={}", result.tool_use_id);
            }
            
            AgentEvent event;
            event.type = "tool_result";
            event.data["tool_use_id"] = result.tool_use_id;
            event.data["content"] = result.content;
            callback(event);
        }

        auto iter_end_time = std::chrono::steady_clock::now();
        auto iter_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(iter_end_time - iter_start_time).count();
        ICRAW_LOG_INFO("[LOOP] Iteration {} - {}ms", iteration, iter_duration_ms);
    }

    auto loop_end_time = std::chrono::steady_clock::now();
    auto loop_duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(loop_end_time - loop_start_time).count();
    ICRAW_LOG_INFO("[LOOP] Full loop - {}ms ({} iterations)", loop_duration_ms, iteration);

    ICRAW_LOG_DEBUG("[AGENT_LOOP] Loop ended, iteration={}, total_messages={}",
        iteration, new_messages.size());

    return new_messages;
}

void AgentLoop::maybe_consolidate_memory(const std::vector<Message>& messages) {
    // Check if consolidation is needed
    int message_count = memory_manager_ ? memory_manager_->get_message_count() : 0;
    int threshold = agent_config_.consolidation_threshold;
    
    ICRAW_LOG_DEBUG("Memory check: {} messages, threshold: {}", message_count, threshold);
    
    if (message_count > threshold) {
        // Check if previous consolidation is still running
        if (consolidation_future_.valid() && 
            consolidation_future_.wait_for(std::chrono::seconds(0)) != std::future_status::ready) {
            ICRAW_LOG_DEBUG("Skipping consolidation: previous consolidation still running");
            return;
        }
        
        ICRAW_LOG_INFO("Triggering async memory consolidation: {} messages > {} threshold", 
            message_count, threshold);
        
        // Launch async consolidation - captures shared_ptrs to ensure lifetime
        auto memory_mgr = memory_manager_;
        auto llm_prov = llm_provider_;
        auto config = agent_config_;
        auto msgs_copy = messages;
        
        consolidation_future_ = std::async(std::launch::async, 
            [memory_mgr, llm_prov, config, msgs_copy]() {
                // Re-create consolidation logic inline for async execution
                if (!memory_mgr || !llm_prov) {
                    return false;
                }
                
                int keep_count = config.memory_window / 2;
                auto old_messages = memory_mgr->get_messages_for_consolidation(keep_count);
                
                if (old_messages.empty()) {
                    ICRAW_LOG_DEBUG("No messages to consolidate (async)");
                    return true;
                }
                
                ICRAW_LOG_DEBUG("Async consolidation: {} messages to process", old_messages.size());
                
                // Format messages for consolidation
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
                
                auto latest_summary = memory_mgr->get_latest_summary();
                std::string current_memory = latest_summary ? latest_summary->summary : "(empty)";
                
                std::string system_prompt = "You are a memory consolidation agent. Call the save_memory tool to save important information from the conversation.";
                std::string user_prompt = "Process this conversation and call the save_memory tool with your consolidation.\n\n"
                                       "## Current Long-term Memory\n" + current_memory + "\n\n"
                                       "## Conversation to Process\n" + conversation;
                
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
                
                ChatCompletionRequest request;
                request.model = config.model;
                request.temperature = config.temperature;
                request.max_tokens = config.max_tokens;
                request.messages.emplace_back("system", system_prompt);
                request.messages.emplace_back("user", user_prompt);
                request.tools.push_back(save_memory_tool);
                request.tool_choice_auto = true;
                
                ICRAW_LOG_DEBUG("Sending async consolidation request to LLM");
                
                auto response = llm_prov->chat_completion(request);
                
                if (response.tool_calls.empty()) {
                    ICRAW_LOG_WARN("Async consolidation: LLM did not call save_memory tool");
                    return false;
                }
                
                try {
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
                        memory_mgr->save_daily_memory(history_entry);
                        ICRAW_LOG_DEBUG("Saved history entry (async): {}", history_entry.substr(0, 50));
                    }
                    
                    if (!memory_update.empty() && memory_update != current_memory) {
                        memory_mgr->create_summary("default", memory_update, static_cast<int>(old_messages.size()));
                        ICRAW_LOG_DEBUG("Saved memory update (async): {}", memory_update.substr(0, 50));
                    }
                    
                    ICRAW_LOG_INFO("Async memory consolidation completed successfully");
                    return true;
                    
                } catch (const std::exception& e) {
                    ICRAW_LOG_ERROR("Async memory consolidation failed: {}", e.what());
                    return false;
                }
            });
    }
}

bool AgentLoop::perform_consolidation(const std::vector<Message>& messages) {
    (void)messages; // Suppress unused parameter warning
    
    if (!memory_manager_ || !llm_provider_) {
        ICRAW_LOG_WARN("Cannot consolidate: memory_manager or llm_provider not available");
        return false;
    }
    
    // Get messages to consolidate
    int keep_count = agent_config_.memory_window / 2;
    auto old_messages = memory_manager_->get_messages_for_consolidation(keep_count);
    
    if (old_messages.empty()) {
        ICRAW_LOG_DEBUG("No messages to consolidate");
        return true;
    }
    
    ICRAW_LOG_DEBUG("Memory consolidation: {} messages to consolidate", old_messages.size());
    
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
    auto latest_summary = memory_manager_->get_latest_summary();
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
    request.messages.emplace_back("system", system_prompt);
    request.messages.emplace_back("user", user_prompt);
    request.tools.push_back(save_memory_tool);
    request.tool_choice_auto = true;
    
    // Log the consolidation request
    ICRAW_LOG_DEBUG("Sending consolidation request to LLM");
    
    // Call LLM (non-streaming for consolidation)
    auto response = llm_provider_->chat_completion(request);
    
    // Check if LLM called the save_memory tool
    if (response.tool_calls.empty()) {
        ICRAW_LOG_WARN("Memory consolidation: LLM did not call save_memory tool");
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
            ICRAW_LOG_DEBUG("Saved history entry: {}", history_entry.substr(0, 50));
        }
        
        if (!memory_update.empty() && memory_update != current_memory) {
            // Save summary to summaries table
            memory_manager_->create_summary("default", memory_update, static_cast<int>(old_messages.size()));
            ICRAW_LOG_DEBUG("Saved memory update: {}", memory_update.substr(0, 50));
        }
        
        ICRAW_LOG_INFO("Memory consolidation completed successfully");
        return true;
        
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("Memory consolidation failed: {}", e.what());
        return false;
    }
}

void AgentLoop::stop() {
    stop_requested_ = true;
}

void AgentLoop::set_config(const AgentConfig& config) {
    agent_config_ = config;
    max_iterations_ = config.max_iterations;
}

std::vector<ContentBlock> AgentLoop::handle_tool_calls(const std::vector<ToolCall>& tool_calls) {
    std::vector<ContentBlock> results;
    
    for (const auto& tc : tool_calls) {
        // Debug: Log raw tool call details before execution
        ICRAW_LOG_DEBUG("handle_tool_calls: name='{}', id='{}', arguments_type={}, arguments={}", 
            tc.name, tc.id, 
            tc.arguments.is_string() ? "string" : (tc.arguments.is_object() ? "object" : (tc.arguments.is_null() ? "null" : "other")),
            tc.arguments.dump());
        
        std::string result = tool_registry_->execute_tool(tc.name, tc.arguments);
        
        // Prune large tool results
        std::string pruned_result = prune_tool_result(result, 10000);
        if (pruned_result.size() != result.size()) {
            ICRAW_LOG_DEBUG("Pruned tool result: {} -> {} chars", result.size(), pruned_result.size());
        }
        
        results.push_back(ContentBlock::make_tool_result(tc.id, pruned_result));
    }
    
    return results;
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
    
    ICRAW_LOG_INFO("Executing memory flush");
    
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
                    ICRAW_LOG_DEBUG("Memory flush saved: {}", content.substr(0, 50));
                }
            }
        }
        
        // Record flush execution
        memory_manager_->record_memory_flush();
        last_flush_compaction_count_ = memory_manager_->get_compaction_count();
        
        ICRAW_LOG_INFO("Memory flush completed");
        return true;
        
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("Memory flush failed: {}", e.what());
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

CompactionResult AgentLoop::perform_compaction_with_fallback(
    const std::vector<MemoryEntry>& messages) {
    
    if (messages.empty()) {
        return CompactionResult::Success;
    }
    
    ICRAW_LOG_INFO("Starting compaction with {} messages", messages.size());
    
    // Try full compaction first
    try {
        // Chunk messages if too large
        auto chunks = chunk_messages_by_tokens(messages, agent_config_.compaction.max_chunk_tokens);
        
        std::string combined_summary;
        int total_tokens_before = estimate_memory_entries_tokens(messages);
        int64_t first_kept_id = messages.empty() ? 0 : messages.back().id;
        
        for (size_t i = 0; i < chunks.size(); ++i) {
            ICRAW_LOG_DEBUG("Compacting chunk {}/{} with {} messages", 
                i + 1, chunks.size(), chunks[i].size());
            
            auto current_summary = memory_manager_->get_latest_summary();
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
            request.messages.emplace_back("system", 
                "You are a memory consolidation agent. Call the save_memory tool to save important information.");
            request.messages.emplace_back("user", prompt);
            request.tools.push_back(save_memory_tool);
            request.tool_choice_auto = true;
            
            auto response = llm_provider_->chat_completion(request);
            
            if (response.tool_calls.empty()) {
                ICRAW_LOG_WARN("Compaction chunk {} did not call save_memory tool", i + 1);
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
            }
            
            if (!memory_update.empty()) {
                combined_summary = memory_update;
            }
            
            if (!chunks[i].empty()) {
                first_kept_id = std::min(first_kept_id, chunks[i].back().id);
            }
        }
        
        // Save final summary
        if (!combined_summary.empty()) {
            memory_manager_->create_summary("default", combined_summary, 
                static_cast<int>(messages.size()));
        }
        
        // Mark messages as consolidated
        memory_manager_->mark_consolidated(static_cast<int>(messages.size()));
        
        // Create compaction record
        int total_tokens_after = estimate_tokens(combined_summary);
        memory_manager_->create_compaction_record("default", combined_summary,
            first_kept_id, total_tokens_before, total_tokens_after, "full");
        
        // Update token stats
        memory_manager_->update_token_stats();
        
        ICRAW_LOG_INFO("Compaction completed: {} -> {} tokens ({:.1f}% reduction)",
            total_tokens_before, total_tokens_after,
            100.0 * (1.0 - static_cast<double>(total_tokens_after) / total_tokens_before));
        
        return CompactionResult::Success;
        
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("Compaction failed: {}", e.what());
        
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
            memory_manager_->create_summary("default", fallback_summary, 
                static_cast<int>(messages.size()));
            memory_manager_->mark_consolidated(static_cast<int>(messages.size()));
            
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
    for (auto& msg : messages) {
        if (msg.role == "tool") {
            for (auto& block : msg.content) {
                if (block.type == "tool_result") {
                    block.content = prune_tool_result(block.content, max_chars);
                }
            }
        }
    }
}

} // namespace icraw
