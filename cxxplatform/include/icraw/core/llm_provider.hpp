#pragma once

#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <map>
#include "icraw/types.hpp"
#include "icraw/core/http_client.hpp"

namespace icraw {

struct ChatCompletionRequest {
    std::vector<Message> messages;
    std::string model;
    double temperature = 0.7;
    int max_tokens = 4096;
    std::vector<nlohmann::json> tools;
    bool tool_choice_auto = true;
    bool stream = false;
};

struct ChatCompletionResponse {
    std::string content;
    std::vector<ToolCall> tool_calls;
    std::string finish_reason;
    bool is_stream_end = false;
};

// ============================================================================
// Stream Parser - Abstract interface for parsing streaming responses
// Different LLM providers may have different streaming formats
// ============================================================================

class StreamParser {
public:
    virtual ~StreamParser() = default;
    
    // Parse a single SSE event into response
    // Returns true if parsing succeeded, false if event should be skipped
    virtual bool parse_chunk(const std::string& sse_event, 
                            ChatCompletionResponse& response) = 0;
    
    // Check if this event indicates stream end
    virtual bool is_stream_end(const std::string& sse_event) const = 0;
    
    // Get parser name for debugging
    virtual std::string get_parser_name() const = 0;
    
    // Tool call accumulation state management
    // Different providers use different ways to match tool call chunks:
    // - OpenAI: uses "id" field
    // - Qwen/Aliyun: uses "index" field
    // - Others: may use different approaches
    
    struct ToolCallAccumulator {
        std::string id;
        std::string name;
        std::string arguments;
        int index = -1;
    };
    
    // Reset accumulator state for a new stream
    virtual void reset_accumulators() = 0;
    
    // Get accumulated tool calls (called at stream end)
    virtual std::vector<ToolCall> get_accumulated_tool_calls() = 0;
};

// ============================================================================
// OpenAI-compatible stream parser
// Supports OpenAI, Qwen, and other OpenAI-compatible APIs
// ============================================================================

class OpenAIStreamParser : public StreamParser {
public:
    // Matching mode for tool call chunks
    enum class ToolCallMatchMode {
        BY_ID,      // OpenAI style: match by "id" field
        BY_INDEX,   // Qwen style: match by "index" field
        AUTO        // Auto-detect based on first chunk
    };
    
    explicit OpenAIStreamParser(ToolCallMatchMode mode = ToolCallMatchMode::AUTO);
    
    bool parse_chunk(const std::string& sse_event, 
                    ChatCompletionResponse& response) override;
    
    bool is_stream_end(const std::string& sse_event) const override;
    
    std::string get_parser_name() const override { return "OpenAI-compatible"; }
    
    void reset_accumulators() override;
    
    std::vector<ToolCall> get_accumulated_tool_calls() override;
    
private:
    ToolCallMatchMode match_mode_;
    std::map<int, ToolCallAccumulator> accumulators_by_index_;
    std::map<std::string, ToolCallAccumulator> accumulators_by_id_;
    bool mode_detected_ = false;
    
    // Accumulate a tool call chunk
    void accumulate_tool_call(const nlohmann::json& tc_json);
    
    // Find or create accumulator for a chunk
    ToolCallAccumulator* find_or_create_accumulator(const std::string& id, int index);
};

// ============================================================================
// Stream Parser Factory
// ============================================================================

// Create appropriate stream parser based on provider/base_url
std::unique_ptr<StreamParser> create_stream_parser(const std::string& base_url);

// ============================================================================
// LLM Provider Base Class
// ============================================================================

class LLMProvider {
public:
    virtual ~LLMProvider() = default;

    // Non-streaming chat completion
    virtual ChatCompletionResponse chat_completion(const ChatCompletionRequest& request) = 0;

    // Streaming chat completion
    virtual void chat_completion_stream(
        const ChatCompletionRequest& request,
        std::function<void(const ChatCompletionResponse&)> callback) = 0;

    // Provider info
    virtual std::string get_provider_name() const = 0;
    virtual std::vector<std::string> get_supported_models() const = 0;

    // Build request body for OpenAI-compatible API
    virtual nlohmann::json build_request_body(const ChatCompletionRequest& request) const;
    
    // Parse response from OpenAI-compatible API
    virtual ChatCompletionResponse parse_response(const nlohmann::json& response) const;
};

// ============================================================================
// OpenAI-compatible Provider
// ============================================================================

class OpenAICompatibleProvider : public LLMProvider {
public:
    OpenAICompatibleProvider(const std::string& api_key,
                             const std::string& base_url = "https://api.openai.com/v1",
                             const std::string& default_model = "gpt-4o");
    ~OpenAICompatibleProvider() override = default;

    ChatCompletionResponse chat_completion(const ChatCompletionRequest& request) override;
    void chat_completion_stream(const ChatCompletionRequest& request,
                                std::function<void(const ChatCompletionResponse&)> callback) override;

    std::string get_provider_name() const override;
    std::vector<std::string> get_supported_models() const override;

    void set_http_client(std::unique_ptr<HttpClient> client);

protected:
    std::string api_key_;
    std::string base_url_;
    std::string default_model_;
    std::unique_ptr<HttpClient> http_client_;
    std::unique_ptr<StreamParser> stream_parser_;
};

} // namespace icraw
