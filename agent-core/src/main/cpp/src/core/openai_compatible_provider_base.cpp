#include "icraw/core/openai_compatible_provider_base.hpp"

#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"
#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstdlib>

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

size_t utf8_safe_boundary(std::string_view value, size_t max_length) {
    if (value.size() <= max_length) {
        return value.size();
    }

    size_t safe_end = max_length;
    while (safe_end > 0 && (static_cast<unsigned char>(value[safe_end]) & 0xC0u) == 0x80u) {
        --safe_end;
    }
    if (safe_end == 0) {
        return max_length;
    }

    const unsigned char lead = static_cast<unsigned char>(value[safe_end]);
    size_t code_point_length = 1;
    if ((lead & 0x80u) == 0x00u) {
        code_point_length = 1;
    } else if ((lead & 0xE0u) == 0xC0u) {
        code_point_length = 2;
    } else if ((lead & 0xF0u) == 0xE0u) {
        code_point_length = 3;
    } else if ((lead & 0xF8u) == 0xF0u) {
        code_point_length = 4;
    }

    if (safe_end + code_point_length > max_length) {
        return safe_end;
    }
    return max_length;
}

std::vector<std::string> split_utf8_chunks(const std::string& text, size_t chunk_size) {
    std::vector<std::string> chunks;
    if (text.empty()) {
        return chunks;
    }

    size_t offset = 0;
    while (offset < text.size()) {
        const size_t remaining = text.size() - offset;
        const size_t desired = std::min(chunk_size, remaining);
        size_t actual = utf8_safe_boundary(std::string_view(text).substr(offset), desired);
        if (actual == 0) {
            actual = desired;
        }
        chunks.push_back(text.substr(offset, actual));
        offset += actual;
    }
    return chunks;
}

std::string message_content_preview(const Message& message) {
    if (message.role == "tool" && !message.content.empty()) {
        const auto& block = message.content.front();
        if (block.type == "tool_result") {
            return block.content;
        }
    }
    return message.text();
}

const char* payload_log_mode_name(PayloadLogMode mode) {
    switch (mode) {
        case PayloadLogMode::Verbose:
            return "verbose";
        case PayloadLogMode::Summary:
        default:
            return "summary";
    }
}

bool is_truthy_env_value(const char* value) {
    if (value == nullptr) {
        return false;
    }
    std::string normalized(value);
    std::transform(normalized.begin(), normalized.end(), normalized.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return normalized == "1"
            || normalized == "true"
            || normalized == "yes"
            || normalized == "on"
            || normalized == "debug"
            || normalized == "verbose";
}

PayloadLogMode resolve_payload_log_mode(const ChatCompletionRequest& request) {
    if (request.payload_log_mode == PayloadLogMode::Verbose) {
        return PayloadLogMode::Verbose;
    }
    return is_truthy_env_value(std::getenv("ICRAW_VERBOSE_PAYLOAD_LOGGING"))
            ? PayloadLogMode::Verbose
            : PayloadLogMode::Summary;
}

bool verbose_transport_debug_enabled(PayloadLogMode payload_log_mode) {
    if (payload_log_mode == PayloadLogMode::Verbose) {
        return true;
    }
    return is_truthy_env_value(std::getenv("ICRAW_VERBOSE_STREAM_DEBUG"));
}

void log_network_timing_summary(const ChatCompletionRequest& request,
                                const std::string& mode,
                                const std::optional<NetworkTimingMetrics>& metrics) {
    if (!metrics.has_value()) {
        return;
    }
    ICRAW_LOG_INFO(
            "[LlmProvider][network_timing_summary] mode={} request_profile={} dns_ms={} connect_ms={} tls_ms={} ttfb_ms={} download_ms={} total_ms={}",
            mode,
            request.request_profile,
            metrics->dns_ms,
            metrics->connect_ms,
            metrics->tls_ms,
            metrics->ttfb_ms,
            metrics->download_ms,
            metrics->total_ms);
}

void log_request_payload_details(const ChatCompletionRequest& request,
                                 const std::string& request_body_str,
                                 const std::string& mode) {
    constexpr size_t kChunkSize = 3000;

    const PayloadLogMode payload_log_mode = resolve_payload_log_mode(request);
    const bool verbose = payload_log_mode == PayloadLogMode::Verbose;

    size_t system_chars = 0;
    size_t user_chars = 0;
    size_t assistant_chars = 0;
    size_t tool_chars = 0;

    for (size_t i = 0; i < request.messages.size(); ++i) {
        const auto& message = request.messages[i];
        const std::string preview = message_content_preview(message);

        if (message.role == "system") {
            system_chars += preview.size();
        } else if (message.role == "user") {
            user_chars += preview.size();
        } else if (message.role == "assistant") {
            assistant_chars += preview.size();
        } else if (message.role == "tool") {
            tool_chars += preview.size();
        }

        if (!verbose) {
            continue;
        }

        const std::string message_json_str = message.to_json().dump();
        ICRAW_LOG_INFO(
                "[LlmProvider][request_payload_message] mode={} request_profile={} index={} role={} json_length={} preview_length={} block_count={} tool_call_count={}",
                mode,
                request.request_profile,
                i,
                message.role,
                message_json_str.size(),
                preview.size(),
                message.content.size(),
                message.tool_calls.size());

        const auto message_chunks = split_utf8_chunks(message_json_str, kChunkSize);
        for (size_t chunk_index = 0; chunk_index < message_chunks.size(); ++chunk_index) {
            ICRAW_LOG_INFO(
                    "[LlmProvider][request_payload_message_chunk] mode={} request_profile={} index={} chunk={}/{} data={}",
                    mode,
                    request.request_profile,
                    i,
                    chunk_index + 1,
                    message_chunks.size(),
                    message_chunks[chunk_index]);
        }
    }

    size_t tool_schema_chars = 0;
    for (size_t i = 0; i < request.tools.size(); ++i) {
        const auto& tool = request.tools[i];
        const std::string tool_json = tool.dump();
        tool_schema_chars += tool_json.size();

        if (!verbose) {
            continue;
        }

        ICRAW_LOG_INFO(
                "[LlmProvider][request_payload_tool] mode={} request_profile={} index={} name={} json_length={}",
                mode,
                request.request_profile,
                i,
                tool.value("function", nlohmann::json::object()).value("name", ""),
                tool_json.size());
        const auto tool_chunks = split_utf8_chunks(tool_json, kChunkSize);
        for (size_t chunk_index = 0; chunk_index < tool_chunks.size(); ++chunk_index) {
            ICRAW_LOG_INFO(
                    "[LlmProvider][request_payload_tool_chunk] mode={} request_profile={} index={} chunk={}/{} data={}",
                    mode,
                    request.request_profile,
                    i,
                    chunk_index + 1,
                    tool_chunks.size(),
                    tool_chunks[chunk_index]);
        }
    }

    ICRAW_LOG_INFO(
            "[LlmProvider][prompt_segment_lengths] mode={} request_profile={} payload_log_mode={} system_chars={} user_chars={} assistant_chars={} tool_result_chars={} tool_schema_chars={}",
            mode,
            request.request_profile,
            payload_log_mode_name(payload_log_mode),
            system_chars,
            user_chars,
            assistant_chars,
            tool_chars,
            tool_schema_chars);
    ICRAW_LOG_INFO(
            "[LlmProvider][request_payload_summary] mode={} request_profile={} payload_log_mode={} body_length={} message_count={} tool_count={} system_chars={} user_chars={} assistant_chars={} tool_result_chars={} tool_schema_chars={}",
            mode,
            request.request_profile,
            payload_log_mode_name(payload_log_mode),
            request_body_str.size(),
            request.messages.size(),
            request.tools.size(),
            system_chars,
            user_chars,
            assistant_chars,
            tool_chars,
            tool_schema_chars);

    if (!verbose) {
        return;
    }

    const auto body_chunks = split_utf8_chunks(request_body_str, kChunkSize);
    for (size_t chunk_index = 0; chunk_index < body_chunks.size(); ++chunk_index) {
        ICRAW_LOG_INFO(
                "[LlmProvider][request_payload_body_chunk] mode={} request_profile={} chunk={}/{} data={}",
                mode,
                request.request_profile,
                chunk_index + 1,
                body_chunks.size(),
                body_chunks[chunk_index]);
    }
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

    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=non_stream request_profile={} message_count={} tool_count={}",
            request.request_profile,
            request.messages.size(),
            request.tools.size());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_flags] mode=non_stream provider={} request_profile={} payload_log_mode={} thinking_type={} reasoning_split={} max_tokens={} temperature={}",
            provider_name_,
            request.request_profile,
            payload_log_mode_name(resolve_payload_log_mode(request)),
            describe_thinking_type_from_body(request, body),
            body.value("reasoning_split", false),
            request.max_tokens,
            request.temperature);
    const PayloadLogMode non_stream_payload_log_mode = resolve_payload_log_mode(request);
    const bool non_stream_verbose_transport = verbose_transport_debug_enabled(non_stream_payload_log_mode);
    if (non_stream_verbose_transport) {
        ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream url={}/chat/completions body={}",
                base_url_, log_utils::truncate_for_debug(request_body_str));
    }
    log_request_payload_details(request, request_body_str, "non_stream");

    if (api_key_.empty()) {
        if (non_stream_verbose_transport) {
            ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream state=mock_response");
        }
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

    const bool request_ok = http_client_->perform_request(
            url,
            "POST",
            request_body_str,
            response_body,
            response_headers,
            error,
            headers);
    log_network_timing_summary(request, "non_stream", http_client_->get_last_timing_metrics());
    if (!request_ok) {
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
        if (non_stream_verbose_transport) {
            ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream error_response={}",
                    log_utils::truncate_for_debug(response_body));
        }

        return response;
    }

    try {
        nlohmann::json response_json = nlohmann::json::parse(response_body);

        auto end_time = std::chrono::steady_clock::now();
        auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        ICRAW_LOG_INFO("[LlmProvider][chat_request_complete] mode=non_stream duration_ms={} response_length={}",
                duration_ms, response_body.size());
        if (non_stream_verbose_transport) {
            ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=non_stream response_body={}",
                    log_utils::truncate_for_debug(response_body));
        }

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

    ICRAW_LOG_INFO("[LlmProvider][parser_selected] parser={}", stream_parser_->get_parser_name());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_start] mode=stream request_profile={} message_count={} tool_count={}",
            request.request_profile,
            request.messages.size(),
            request.tools.size());
    ICRAW_LOG_INFO("[LlmProvider][chat_request_flags] mode=stream provider={} request_profile={} payload_log_mode={} thinking_type={} reasoning_split={} max_tokens={} temperature={}",
            provider_name_,
            request.request_profile,
            payload_log_mode_name(resolve_payload_log_mode(request)),
            describe_thinking_type_from_body(request, body),
            body.value("reasoning_split", false),
            request.max_tokens,
            request.temperature);
    const PayloadLogMode stream_payload_log_mode = resolve_payload_log_mode(request);
    const bool stream_verbose_transport = verbose_transport_debug_enabled(stream_payload_log_mode);
    if (stream_verbose_transport) {
        ICRAW_LOG_DEBUG("[LlmProvider][chat_request_debug] mode=stream url={}/chat/completions body={}",
                base_url_, log_utils::truncate_for_debug(request_body_str));
    }
    log_request_payload_details(request, request_body_str, "stream");

    std::string url = base_url_ + "/chat/completions";

    HttpHeaders headers;
    if (!api_key_.empty()) {
        headers["Authorization"] = "Bearer " + api_key_;
    }

    bool first_delta_logged = false;
    bool first_tool_call_delta_logged = false;

    auto sse_callback = [&](const std::string& sse_event) -> bool {
        if (cancel_requested_.load()) {
            ICRAW_LOG_INFO("[LlmProvider][chat_stream_cancel] profile={} stage=before_parse",
                    provider_name_);
            return false;
        }

        if (stream_verbose_transport) {
            ICRAW_LOG_DEBUG("[LlmProvider][stream_chunk_debug] event_length={} preview={}",
                    sse_event.size(), log_utils::truncate_for_debug(sse_event));
        }
        ChatCompletionResponse response;
        const bool raw_tool_call_delta = sse_event.find("\"tool_calls\"") != std::string::npos;

        if (stream_parser_->parse_chunk(sse_event, response)) {
            const auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now() - start_time).count();
            if (!first_delta_logged
                    && (!response.content.empty() || !response.reasoning_content.empty()
                        || response.has_tool_call_delta || response.is_stream_end)) {
                first_delta_logged = true;
                ICRAW_LOG_INFO("[LlmProvider][first_delta_ms] request_profile={} duration_ms={}",
                        request.request_profile,
                        elapsed_ms);
            }
            if (!first_tool_call_delta_logged && (response.has_tool_call_delta || raw_tool_call_delta)) {
                first_tool_call_delta_logged = true;
                ICRAW_LOG_INFO("[LlmProvider][first_tool_call_delta_ms] request_profile={} duration_ms={}",
                        request.request_profile,
                        elapsed_ms);
            }

            if (stream_verbose_transport && response.is_stream_end) {
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
                if (stream_verbose_transport) {
                    ICRAW_LOG_DEBUG("[LlmProvider][chat_stream_debug] action=stop_transfer");
                }
                return false;
            }
        }

        return true;
    };

    HttpError error;
    ICRAW_LOG_INFO("[LlmProvider][chat_stream_start] request_profile={}", request.request_profile);
    const bool stream_ok = http_client_->perform_request_stream(
            url,
            "POST",
            request_body_str,
            sse_callback,
            error,
            headers);
    log_network_timing_summary(request, "stream", http_client_->get_last_timing_metrics());
    if (!stream_ok) {
        ICRAW_LOG_ERROR("[LlmProvider][chat_stream_failed] error_code={} message={}", error.code, error.message);
        throw std::runtime_error("Streaming request failed: " + error.message);
    }
    if (stream_verbose_transport) {
        ICRAW_LOG_DEBUG("[LlmProvider][chat_stream_debug] state=perform_request_stream_completed");
    }

    auto end_time = std::chrono::steady_clock::now();
    auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    ICRAW_LOG_INFO("[LlmProvider][stream_complete_ms] request_profile={} duration_ms={}",
            request.request_profile,
            duration_ms);
    ICRAW_LOG_INFO("[LlmProvider][chat_stream_complete] duration_ms={}", duration_ms);
}

} // namespace icraw
