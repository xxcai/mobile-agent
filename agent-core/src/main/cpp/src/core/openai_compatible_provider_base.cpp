#include "icraw/core/openai_compatible_provider_base.hpp"

#include "icraw/core/token_utils.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"
#include <chrono>

namespace icraw {

namespace {

const char* describe_thinking_type_from_body(const ChatCompletionRequest& request,
                                             const nlohmann::json& body) {
    if (body.contains("thinking")
            && body["thinking"].is_object()
            && body["thinking"].contains("type")
            && body["thinking"]["type"].is_string()) {
        return body["thinking"]["type"].get_ref<const std::string&>().c_str();
    }
    if (request.enable_thinking.has_value()) {
        return *request.enable_thinking ? "enabled_not_emitted" : "disabled_not_emitted";
    }
    return "absent";
}

int estimate_request_tokens_for_log(const ChatCompletionRequest& request) {
    int estimated_tokens = estimate_messages_tokens(request.messages);
    if (!request.tools.empty()) {
        estimated_tokens += estimate_tokens(nlohmann::json(request.tools).dump());
    }
    return estimated_tokens;
}

int estimate_response_tokens_for_log(const std::string& content,
                                     const std::string& reasoning_content,
                                     const std::vector<ToolCall>& tool_calls) {
    Message assistant_message;
    assistant_message.role = "assistant";

    if (!reasoning_content.empty()) {
        assistant_message.content.push_back(ContentBlock::make_think(reasoning_content));
    }
    if (!content.empty()) {
        assistant_message.content.push_back(ContentBlock::make_text(content));
    }
    for (const auto& tool_call : tool_calls) {
        ToolCallForMessage tc;
        tc.id = tool_call.id;
        tc.function_name = tool_call.name;
        tc.function_arguments = tool_call.arguments.dump();
        assistant_message.tool_calls.push_back(std::move(tc));
    }

    return estimate_message_tokens(assistant_message);
}

} // namespace

OpenAICompatibleProviderBase::OpenAICompatibleProviderBase(const std::string& api_key,
                                                           const std::string& base_url,
                                                           const std::string& default_model)
    : api_key_(api_key)
    , base_url_(base_url)
    , default_model_(default_model.empty() ? "gpt-4o" : default_model)
    , http_client_(std::make_unique<CurlHttpClient>())
    , stream_parser_(nullptr) {
    if (!base_url_.empty() && base_url_.back() == '/') {
        base_url_.pop_back();
    }

    stream_parser_ = create_provider_stream_parser();
}

void OpenAICompatibleProviderBase::set_http_client(std::unique_ptr<HttpClient> client) {
    http_client_ = std::move(client);
}

void OpenAICompatibleProviderBase::cancel_active_request() {
    cancel_requested_.store(true);
    ICRAW_LOG_INFO("[LlmProvider][cancel_requested] profile={}", provider_name_);
}

void OpenAICompatibleProviderBase::set_provider_name(std::string provider_name) {
    provider_name_ = std::move(provider_name);
    ICRAW_LOG_INFO("[LlmProvider][profile_selected] profile={} base_url={}",
            provider_name_, base_url_);
}

void OpenAICompatibleProviderBase::refresh_stream_parser() {
    stream_parser_ = create_provider_stream_parser();
}

std::string OpenAICompatibleProviderBase::get_provider_name() const {
    return "OpenAI-Compatible(" + provider_name_ + ")";
}

std::vector<std::string> OpenAICompatibleProviderBase::get_supported_models() const {
    return {"gpt-4o", "gpt-4o-mini", "qwen-max", "qwen3-max", "glm-5", "glm-4-plus"};
}

void OpenAICompatibleProviderBase::apply_request_quirks(const ChatCompletionRequest& request,
                                                        nlohmann::json& body) const {
    (void) request;
    (void) body;
}

std::unique_ptr<StreamParser> OpenAICompatibleProviderBase::create_provider_stream_parser() const {
    ICRAW_LOG_DEBUG("[LlmProvider][profile_debug] profile={} parser_mode=auto",
            provider_name_);
    return std::make_unique<OpenAIStreamParser>(OpenAIStreamParser::ToolCallMatchMode::AUTO);
}

ChatCompletionResponse OpenAICompatibleProviderBase::chat_completion(const ChatCompletionRequest& request) {
    cancel_requested_.store(false);
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

    apply_request_quirks(request, body);

    std::string request_body_str = body.dump();
    const int estimated_input_tokens = estimate_request_tokens_for_log(request);

    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=non_stream message_count={} tool_count={}",
            request.messages.size(), request.tools.size());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_flags] mode=non_stream profile={} thinking_type={} reasoning_split={}",
            provider_name_,
            describe_thinking_type_from_body(request, body),
            body.value("reasoning_split", false));
    ICRAW_LOG_INFO("[LlmProvider][chat_request_local_tokens] mode=non_stream estimated_input_tokens={}",
            estimated_input_tokens);
    ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream url={}/chat/completions body={}",
            base_url_, log_utils::truncate_for_debug(request_body_str));

    if (api_key_.empty()) {
        ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream state=mock_response");
        ChatCompletionResponse response;
        response.finish_reason = "stop";

        std::string user_message;
        for (auto it = request.messages.rbegin(); it != request.messages.rend(); ++it) {
            if (it->role == "user") {
                user_message = it->content.empty() ? "" : it->content[0].text;
                break;
            }
        }

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
        ChatCompletionResponse parsed_response = parse_response(response_json);
        const int estimated_output_tokens = estimate_response_tokens_for_log(
                parsed_response.content,
                parsed_response.reasoning_content,
                parsed_response.tool_calls);
        ICRAW_LOG_INFO("[LlmProvider][chat_request_complete] mode=non_stream duration_ms={} response_length={}",
                duration_ms, response_body.size());
        ICRAW_LOG_INFO("[LlmProvider][chat_request_local_tokens] mode=non_stream estimated_output_tokens={} estimated_total_tokens={}",
                estimated_output_tokens,
                estimated_input_tokens + estimated_output_tokens);
        ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream response_body={}",
                log_utils::truncate_for_debug(response_body));

        return parsed_response;
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

void OpenAICompatibleProviderBase::chat_completion_stream(
    const ChatCompletionRequest& request,
    std::function<void(const ChatCompletionResponse&)> callback) {
    cancel_requested_.store(false);
    auto start_time = std::chrono::steady_clock::now();

    if (!http_client_) {
        throw std::runtime_error("HTTP client not initialized");
    }

    if (!callback) {
        throw std::runtime_error("No callback provided");
    }

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

    apply_request_quirks(request, body);

    std::string request_body_str = body.dump();
    const int estimated_input_tokens = estimate_request_tokens_for_log(request);

    ICRAW_LOG_INFO("[LlmProvider][parser_selected] parser={}", stream_parser_->get_parser_name());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=stream message_count={} tool_count={}",
            request.messages.size(), request.tools.size());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_flags] mode=stream profile={} thinking_type={} reasoning_split={}",
            provider_name_,
            describe_thinking_type_from_body(request, body),
            body.value("reasoning_split", false));
    ICRAW_LOG_INFO("[LlmProvider][chat_request_local_tokens] mode=stream estimated_input_tokens={}",
            estimated_input_tokens);
    ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=stream url={}/chat/completions body={}",
            base_url_, log_utils::truncate_for_debug(request_body_str));

    std::string url = base_url_ + "/chat/completions";

    HttpHeaders headers;
    if (!api_key_.empty()) {
        headers["Authorization"] = "Bearer " + api_key_;
    }

    std::string final_finish_reason;
    int estimated_output_tokens = 0;
    std::string accumulated_content_for_log;
    std::string accumulated_reasoning_for_log;
    std::vector<ToolCall> final_tool_calls_for_log;

    auto sse_callback = [&](const std::string& sse_event) -> bool {
        if (cancel_requested_.load()) {
            ICRAW_LOG_INFO("[LlmProvider][chat_stream_cancel] profile={} stage=before_parse",
                    provider_name_);
            return false;
        }

        ICRAW_LOG_DEBUG("[LlmProvider][stream_chunk_debug] event_length={} preview={}",
                sse_event.size(), log_utils::truncate_for_debug(sse_event));
        ChatCompletionResponse response;

        if (stream_parser_->parse_chunk(sse_event, response)) {
            if (!response.content.empty()) {
                accumulated_content_for_log += response.content;
            }
            if (!response.reasoning_content.empty()) {
                accumulated_reasoning_for_log += response.reasoning_content;
            }
            if (response.is_stream_end) {
                final_finish_reason = response.finish_reason;
                final_tool_calls_for_log = response.tool_calls;
                // 流式 chunk 的 content/reasoning 是增量，这里按完整累积内容估算。
                estimated_output_tokens = estimate_response_tokens_for_log(
                        accumulated_content_for_log,
                        accumulated_reasoning_for_log,
                        final_tool_calls_for_log);
                ICRAW_LOG_DEBUG("[LlmProvider][chat_stream_debug] finish_reason={} tool_call_count={}",
                        response.finish_reason, response.tool_calls.size());
            }

            callback(response);

            if (cancel_requested_.load()) {
                ICRAW_LOG_INFO("[LlmProvider][chat_stream_cancel] profile={} stage=after_callback",
                        provider_name_);
                return false;
            }

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
    ICRAW_LOG_INFO("[LlmProvider][chat_stream_complete] duration_ms={} finish_reason={}",
            duration_ms, final_finish_reason);
    ICRAW_LOG_INFO("[LlmProvider][chat_request_local_tokens] mode=stream estimated_output_tokens={} estimated_total_tokens={}",
            estimated_output_tokens,
            estimated_input_tokens + estimated_output_tokens);
}
} // namespace icraw
