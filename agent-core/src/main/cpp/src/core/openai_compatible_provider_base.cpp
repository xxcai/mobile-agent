#include "icraw/core/openai_compatible_provider_base.hpp"

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

    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=non_stream message_count={} tool_count={}",
            request.messages.size(), request.tools.size());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_flags] mode=non_stream profile={} thinking_type={} reasoning_split={}",
            provider_name_,
            describe_thinking_type_from_body(request, body),
            body.value("reasoning_split", false));
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

void OpenAICompatibleProviderBase::chat_completion_stream(
    const ChatCompletionRequest& request,
    std::function<void(const ChatCompletionResponse&)> callback) {
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

    ICRAW_LOG_INFO("[LlmProvider][parser_selected] parser={}", stream_parser_->get_parser_name());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=stream message_count={} tool_count={}",
            request.messages.size(), request.tools.size());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_flags] mode=stream profile={} thinking_type={} reasoning_split={}",
            provider_name_,
            describe_thinking_type_from_body(request, body),
            body.value("reasoning_split", false));
    ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=stream url={}/chat/completions body={}",
            base_url_, log_utils::truncate_for_debug(request_body_str));

    std::string url = base_url_ + "/chat/completions";

    HttpHeaders headers;
    if (!api_key_.empty()) {
        headers["Authorization"] = "Bearer " + api_key_;
    }

    auto sse_callback = [&](const std::string& sse_event) -> bool {
        ICRAW_LOG_DEBUG("[LlmProvider][stream_chunk_debug] event_length={} preview={}",
                sse_event.size(), log_utils::truncate_for_debug(sse_event));
        ChatCompletionResponse response;

        if (stream_parser_->parse_chunk(sse_event, response)) {
            if (response.is_stream_end) {
                ICRAW_LOG_DEBUG("[LlmProvider][chat_stream_debug] finish_reason={} tool_call_count={}",
                        response.finish_reason, response.tool_calls.size());
            }

            callback(response);

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
