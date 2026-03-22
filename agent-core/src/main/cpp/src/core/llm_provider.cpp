#include "icraw/core/llm_provider.hpp"
#include "icraw/core/logger.hpp"
#include "icraw/core/log_utils.hpp"
#include <algorithm>
#include <sstream>
#include <chrono>

namespace icraw {

// ============================================================================
// Stream Parser Factory
// ============================================================================

std::unique_ptr<StreamParser> create_stream_parser(const std::string& base_url) {
    // Detect provider based on base_url
    if (base_url.find("dashscope.aliyuncs.com") != std::string::npos) {
        // Aliyun Qwen uses index-based matching
        ICRAW_LOG_DEBUG("Using index-based stream parser for Aliyun/Qwen");
        return std::make_unique<OpenAIStreamParser>(OpenAIStreamParser::ToolCallMatchMode::BY_INDEX);
    }
    
    // Default: OpenAI style with auto-detection
    ICRAW_LOG_DEBUG("Using auto-detect stream parser");
    return std::make_unique<OpenAIStreamParser>(OpenAIStreamParser::ToolCallMatchMode::AUTO);
}

// ============================================================================
// OpenAI Stream Parser Implementation
// ============================================================================

OpenAIStreamParser::OpenAIStreamParser(ToolCallMatchMode mode)
    : match_mode_(mode) {
}

void OpenAIStreamParser::reset_accumulators() {
    accumulators_by_index_.clear();
    accumulators_by_id_.clear();
    mode_detected_ = false;
    match_mode_ = ToolCallMatchMode::AUTO;  // Reset to AUTO for each new stream
}

OpenAIStreamParser::ToolCallAccumulator* OpenAIStreamParser::find_or_create_accumulator(
    const std::string& id, int index) {
    
    // Auto-detect mode from first chunk
    if (!mode_detected_ && (match_mode_ == ToolCallMatchMode::AUTO)) {
        // If we see index >= 0, prefer index-based matching
        if (index >= 0) {
            match_mode_ = ToolCallMatchMode::BY_INDEX;
            mode_detected_ = true;
        } else if (!id.empty()) {
            match_mode_ = ToolCallMatchMode::BY_ID;
            mode_detected_ = true;
        }
    }
    
    // Use appropriate lookup based on mode
    if (match_mode_ == ToolCallMatchMode::BY_INDEX && index >= 0) {
        return &accumulators_by_index_[index];
    } else if (!id.empty()) {
        return &accumulators_by_id_[id];
    } else if (index >= 0) {
        // Fallback to index
        return &accumulators_by_index_[index];
    }
    
    return nullptr;
}

void OpenAIStreamParser::accumulate_tool_call(const nlohmann::json& tc_json) {
    std::string id = tc_json.value("id", "");
    int index = tc_json.value("index", -1);
    
    ToolCallAccumulator* acc = find_or_create_accumulator(id, index);
    if (!acc) return;
    
    // Update id if provided
    if (!id.empty()) {
        acc->id = id;
    }
    acc->index = index;
    
    // Update function info if present
    if (tc_json.contains("function")) {
        const auto& func = tc_json["function"];
        
        std::string name = func.value("name", "");
        if (!name.empty()) {
            acc->name = name;
        }
        
        std::string args = func.value("arguments", "");
        if (!args.empty()) {
            acc->arguments += args;
        }
    }
}

std::vector<ToolCall> OpenAIStreamParser::get_accumulated_tool_calls() {
    std::vector<ToolCall> result;
    
    // Collect from index-based accumulators first
    for (auto& [index, acc] : accumulators_by_index_) {
        if (!acc.name.empty() && !acc.arguments.empty()) {
            ToolCall tc;
            tc.id = acc.id.empty() ? "tool_" + std::to_string(index) : acc.id;
            tc.name = acc.name;
            tc.index = index;
            
            // Try to parse arguments as JSON
            try {
                tc.arguments = nlohmann::json::parse(acc.arguments);
            } catch (...) {
                // Keep as string if not valid JSON
                tc.arguments = acc.arguments;
            }
            
            result.push_back(std::move(tc));
        }
    }
    
    // Collect from id-based accumulators (if not already in index-based)
    for (auto& [id, acc] : accumulators_by_id_) {
        bool found = false;
        for (const auto& tc : result) {
            if (tc.id == id) {
                found = true;
                break;
            }
        }
        
        if (!found && !acc.name.empty() && !acc.arguments.empty()) {
            ToolCall tc;
            tc.id = id;
            tc.name = acc.name;
            tc.index = acc.index;
            
            try {
                tc.arguments = nlohmann::json::parse(acc.arguments);
            } catch (...) {
                tc.arguments = acc.arguments;
            }
            
            result.push_back(std::move(tc));
        }
    }
    
    return result;
}

bool OpenAIStreamParser::is_stream_end(const std::string& sse_event) const {
    // Check for explicit [DONE] marker
    if (sse_event.find("data: [DONE]") != std::string::npos) {
        return true;
    }
    // Check for finish_reason in the JSON - indicates stream is ending
    if (sse_event.find("finish_reason") != std::string::npos) {
        return true;
    }
    return false;
}

bool OpenAIStreamParser::parse_chunk(const std::string& sse_event,
                                     ChatCompletionResponse& response) {
    if (sse_event.empty()) {
        return false;
    }

    // Don't return early on stream end - we need to parse finish_reason from JSON
    // The is_stream_end check is done after JSON parsing below

    if (sse_event.find("data:") != 0) {
        return false;
    }
    
    std::string json_str = sse_event.substr(5);
    
    size_t start = json_str.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return false;
    size_t end = json_str.find_last_not_of(" \t\r\n");
    json_str = json_str.substr(start, end - start + 1);
    
    if (json_str == "[DONE]") {
        response.is_stream_end = true;
        response.tool_calls = get_accumulated_tool_calls();
        return true;
    }
    
    try {
        nlohmann::json chunk_json = nlohmann::json::parse(json_str);

        if (!chunk_json.contains("choices") || chunk_json["choices"].empty()) {
            return false;
        }

        const auto& choice = chunk_json["choices"][0];
        response.finish_reason = choice.value("finish_reason", "");

        if (choice.contains("delta")) {
            const auto& delta = choice["delta"];

            if (delta.contains("content") && !delta["content"].is_null()) {
                response.content = delta["content"].get<std::string>();
            }
            
            if (delta.contains("tool_calls") && delta["tool_calls"].is_array()) {
                for (const auto& tc : delta["tool_calls"]) {
                    accumulate_tool_call(tc);
                }
            }
        }
        
        if (!response.finish_reason.empty()) {
            response.tool_calls = get_accumulated_tool_calls();
            // Stream ends when we receive a finish_reason (stop, tool_calls, etc.)
            response.is_stream_end = true;
        }
        
        return true;
        
    } catch (const nlohmann::json::parse_error&) {
        return false;
    }
}

// ============================================================================
// LLMProvider Base Implementation
// ============================================================================

nlohmann::json LLMProvider::build_request_body(const ChatCompletionRequest& request) const {
    nlohmann::json body;
    
    body["model"] = request.model;
    body["temperature"] = request.temperature;
    body["max_tokens"] = request.max_tokens;
    
    nlohmann::json messages = nlohmann::json::array();
    for (const auto& msg : request.messages) {
        messages.push_back(msg.to_json());
    }
    body["messages"] = messages;
    
    if (!request.tools.empty()) {
        body["tools"] = request.tools;
        body["tool_choice"] = request.tool_choice_auto ? "auto" : "required";
    }
    
    if (request.stream) {
        body["stream"] = true;
    }
    
    return body;
}

    
ChatCompletionResponse LLMProvider::parse_response(const nlohmann::json& response) const {
    ChatCompletionResponse result;

    // Debug: log full response using printf to ensure visibility
    printf("[LLM] Full response: %s\n", response.dump(2).c_str());
    fflush(stdout);

    if (!response.contains("choices") || response["choices"].empty()) {
        result.finish_reason = "error";
        return result;
    }

    const auto& choice = response["choices"][0];
    result.finish_reason = choice.value("finish_reason", "");

    if (choice.contains("message")) {
        const auto& message = choice["message"];

        // Check for reasoning_content (o1 model)
        if (message.contains("reasoning_content") && !message["reasoning_content"].is_null()) {
            printf("[LLM] Found reasoning_content: %s\n", message["reasoning_content"].get<std::string>().c_str());
            fflush(stdout);
        }

        if (message.contains("content") && !message["content"].is_null()) {
            result.content = message["content"].get<std::string>();
        }
        
        if (message.contains("tool_calls") && message["tool_calls"].is_array()) {
            for (const auto& tc : message["tool_calls"]) {
                ToolCall call;
                call.id = tc.value("id", "");
                
                if (tc.contains("function")) {
                    const auto& func = tc["function"];
                    call.name = func.value("name", "");
                    
                    std::string args_str = func.value("arguments", "{}");
                    try {
                        call.arguments = nlohmann::json::parse(args_str);
                    } catch (const nlohmann::json::parse_error&) {
                        call.arguments = nlohmann::json::object();
                    }
                }
                
                result.tool_calls.push_back(std::move(call));
            }
        }
    }
    
    return result;
}

// ============================================================================
// OpenAICompatibleProvider Implementation
// ============================================================================

OpenAICompatibleProvider::OpenAICompatibleProvider(const std::string& api_key,
                                                   const std::string& base_url,
                                                   const std::string& default_model)
    : api_key_(api_key)
    , base_url_(base_url)
    , default_model_(default_model.empty() ? "gpt-4o" : default_model)
    , http_client_(std::make_unique<CurlHttpClient>())
    , stream_parser_(create_stream_parser(base_url))
{
    if (!base_url_.empty() && base_url_.back() == '/') {
        base_url_.pop_back();
    }
}

void OpenAICompatibleProvider::set_http_client(std::unique_ptr<HttpClient> client) {
    http_client_ = std::move(client);
}

std::string OpenAICompatibleProvider::get_provider_name() const {
    return "OpenAI-Compatible";
}

std::vector<std::string> OpenAICompatibleProvider::get_supported_models() const {
    return {"gpt-4o", "gpt-4o-mini", "qwen-max", "qwen3-max"};
}

ChatCompletionResponse OpenAICompatibleProvider::chat_completion(const ChatCompletionRequest& request) {
    auto start_time = std::chrono::steady_clock::now();

    if (!http_client_) {
        ChatCompletionResponse response;
        response.finish_reason = "error";
        response.content = "HTTP client not initialized";
        return response;
    }

    nlohmann::json body;
    body["model"] = request.model.empty() ? default_model_ : request.model;
    body["temperature"] = request.temperature;
    body["max_tokens"] = request.max_tokens;

    nlohmann::json messages = nlohmann::json::array();
    for (const auto& msg : request.messages) {
        messages.push_back(msg.to_json());
    }
    body["messages"] = messages;

    if (!request.tools.empty()) {
        body["tools"] = request.tools;
        body["tool_choice"] = request.tool_choice_auto ? "auto" : "required";
    }

    std::string request_body_str = body.dump();

    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=non_stream message_count={} tool_count={}",
            request.messages.size(), request.tools.size());
    ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream url={}/chat/completions body={}",
            base_url_, log_utils::truncate_for_debug(request_body_str));

    // If API key is empty, return a mock response for local testing
    if (api_key_.empty()) {
        ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream state=mock_response");
        ChatCompletionResponse response;
        response.finish_reason = "stop";

        // Extract the last user message for echo
        std::string user_message;
        for (auto it = request.messages.rbegin(); it != request.messages.rend(); ++it) {
            if (it->role == "user") {
                user_message = it->content.empty() ? "" : it->content[0].text;
                break;
            }
        }

        // Return a simple mock response
        response.content = "Mock Agent: I received your message: \"" + user_message + "\". (Configure API key for real responses)";

        auto end_time = std::chrono::steady_clock::now();
        auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        ICRAW_LOG_INFO("[LlmProvider][chat_request_complete] mode=non_stream duration_ms={} finish_reason={}",
                duration_ms, response.finish_reason);

        return response;
    }

    std::string url = base_url_ + "/chat/completions";

    HttpHeaders headers;
    if (!api_key_.empty()) {
        headers["Authorization"] = "Bearer " + api_key_;
    }

    std::string response_body;
    std::map<std::string, std::string> response_headers;
    HttpError error;

    if (!http_client_->perform_request(url, "POST", request_body_str, response_body, response_headers, error, headers)) {
        ChatCompletionResponse response;
        response.finish_reason = "http_error";
        
        try {
            nlohmann::json error_json = nlohmann::json::parse(response_body);
            if (error_json.contains("error") && error_json["error"].contains("message")) {
                response.content = "API Error: " + error_json["error"]["message"].get<std::string>();
            } else {
                response.content = "HTTP error " + std::to_string(error.code) + ": " + response_body;
            }
        } catch (...) {
            response.content = "HTTP error " + std::to_string(error.code) + ": " + response_body;
        }

        auto end_time = std::chrono::steady_clock::now();
        auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        ICRAW_LOG_WARN("[LlmProvider][chat_request_failed] mode=non_stream duration_ms={} error_code={} response_length={}",
                duration_ms, error.code, response_body.size());
        ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream error_response={}",
                log_utils::truncate_for_debug(response_body));

        return response;
    }
    
    try {
        nlohmann::json response_json = nlohmann::json::parse(response_body);

        auto end_time = std::chrono::steady_clock::now();
        auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        ICRAW_LOG_INFO("[LlmProvider][chat_request_complete] mode=non_stream duration_ms={} response_length={}",
                duration_ms, response_body.size());
        ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream response_body={}",
                log_utils::truncate_for_debug(response_body));

        return parse_response(response_json);
    } catch (const nlohmann::json::parse_error& e) {
        ChatCompletionResponse response;
        response.finish_reason = "parse_error";
        response.content = "Failed to parse response: " + std::string(e.what());

        auto end_time = std::chrono::steady_clock::now();
        auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        ICRAW_LOG_ERROR("[LlmProvider][chat_request_failed] mode=non_stream duration_ms={} message={}",
                duration_ms, e.what());

        return response;
    }
}

void OpenAICompatibleProvider::chat_completion_stream(
    const ChatCompletionRequest& request,
    std::function<void(const ChatCompletionResponse&)> callback) {

    auto start_time = std::chrono::steady_clock::now();

    if (!http_client_) {
        throw std::runtime_error("HTTP client not initialized");
    }
    
    if (!callback) {
        throw std::runtime_error("No callback provided");
    }
    
    // Reset stream parser state
    stream_parser_->reset_accumulators();
    
    nlohmann::json body;
    body["model"] = request.model.empty() ? default_model_ : request.model;
    body["temperature"] = request.temperature;
    body["max_tokens"] = request.max_tokens;
    body["stream"] = true;
    
    nlohmann::json messages = nlohmann::json::array();
    for (const auto& msg : request.messages) {
        messages.push_back(msg.to_json());
    }
    body["messages"] = messages;
    
    if (!request.tools.empty()) {
        body["tools"] = request.tools;
        body["tool_choice"] = request.tool_choice_auto ? "auto" : "required";
    }
    
    std::string request_body_str = body.dump();
    
    ICRAW_LOG_INFO("[LlmProvider][parser_selected] parser={}", stream_parser_->get_parser_name());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=stream message_count={} tool_count={}",
            request.messages.size(), request.tools.size());
    ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=stream url={}/chat/completions body={}",
            base_url_, log_utils::truncate_for_debug(request_body_str));
    
    std::string url = base_url_ + "/chat/completions";
    
    HttpHeaders headers;
    if (!api_key_.empty()) {
        headers["Authorization"] = "Bearer " + api_key_;
    }
    
    // Create SSE callback using stream parser
    auto sse_callback = [&](const std::string& sse_event) -> bool {
        ICRAW_LOG_DEBUG("[LlmProvider][stream_chunk_debug] event_length={} preview={}",
                sse_event.size(), log_utils::truncate_for_debug(sse_event));
        ChatCompletionResponse response;
        
        if (stream_parser_->parse_chunk(sse_event, response)) {
            // Check for stream end before callback
            if (response.is_stream_end) {
                ICRAW_LOG_DEBUG("[LlmProvider][chat_stream_debug] finish_reason={} tool_call_count={}",
                    response.finish_reason, response.tool_calls.size());
            }
            
            callback(response);
            
            // Stop curl when stream ends - return false to abort the transfer
            if (response.is_stream_end) {
                ICRAW_LOG_DEBUG("[LlmProvider][chat_stream_debug] action=stop_transfer");
                return false;
            }
        }
        
        return true;
    };
    
    HttpError error;
    ICRAW_LOG_INFO("[LlmProvider][chat_stream_start]");
    if (!http_client_->perform_request_stream(url, "POST", request_body_str, sse_callback, error, headers)) {
        ICRAW_LOG_ERROR("[LlmProvider][chat_stream_failed] error_code={} message={}", error.code, error.message);
        throw std::runtime_error("Streaming request failed: " + error.message);
    }
    ICRAW_LOG_DEBUG("[LlmProvider][chat_stream_debug] state=perform_request_stream_completed");

    auto end_time = std::chrono::steady_clock::now();
    auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    ICRAW_LOG_INFO("[LlmProvider][chat_stream_complete] duration_ms={}", duration_ms);
}

} // namespace icraw
