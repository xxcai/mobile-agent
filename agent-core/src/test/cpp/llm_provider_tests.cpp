#include "icraw/core/http_client.hpp"
#include "icraw/core/llm_provider.hpp"
#include "icraw/core/llm_provider_factory.hpp"
#include "icraw/core/providers/generic_openai_provider.hpp"
#include "icraw/core/providers/glm_provider.hpp"
#include "icraw/core/providers/minimax_provider.hpp"
#include "icraw/core/providers/qwen_provider.hpp"
#include "icraw/types.hpp"

#include <cassert>
#include <iostream>
#include <memory>
#include <string>

namespace icraw {
namespace {

class FakeHttpClient final : public HttpClient {
public:
    bool should_succeed = true;
    bool should_stream_succeed = true;
    std::string captured_url;
    std::string captured_method;
    std::string captured_request_body;
    HttpHeaders captured_headers;
    std::string response_body =
        R"({"choices":[{"finish_reason":"stop","message":{"content":"ok"}}]})";
    std::vector<std::string> stream_events;

    bool perform_request(const std::string& url,
                         const std::string& method,
                         const std::string& request_body,
                         std::string& out_response_body,
                         std::map<std::string, std::string>& response_headers,
                         HttpError& error,
                         const HttpHeaders& headers = {}) override {
        captured_url = url;
        captured_method = method;
        captured_request_body = request_body;
        captured_headers = headers;
        response_headers.clear();

        if (!should_succeed) {
            error.code = 500;
            error.message = "fake failure";
            out_response_body = R"({"error":{"message":"fake failure"}})";
            return false;
        }

        out_response_body = response_body;
        return true;
    }

    bool perform_request_stream(const std::string& url,
                                const std::string& method,
                                const std::string& request_body,
                                StreamCallback callback,
                                HttpError& error,
                                const HttpHeaders& headers = {}) override {
        captured_url = url;
        captured_method = method;
        captured_request_body = request_body;
        captured_headers = headers;

        if (!should_stream_succeed) {
            error.code = 500;
            error.message = "fake stream failure";
            return false;
        }

        for (const auto& event : stream_events) {
            if (!callback(event)) {
                break;
            }
        }
        return true;
    }
};

void expect(bool condition, const std::string& message) {
    if (!condition) {
        std::cerr << "TEST FAILED: " << message << std::endl;
        std::abort();
    }
}

void expect_equal(const std::string& actual,
                  const std::string& expected,
                  const std::string& message) {
    if (actual != expected) {
        std::cerr << "TEST FAILED: " << message
                  << " expected=[" << expected << "] actual=[" << actual << "]"
                  << std::endl;
        std::abort();
    }
}

ChatCompletionRequest make_basic_request() {
    ChatCompletionRequest request;
    request.model = "test-model";
    request.messages.push_back(Message("user", "hello"));
    return request;
}

void test_vendor_resolution() {
    {
        const auto vendor = resolve_openai_compatible_vendor("https://api.minimax.chat/v1");
        expect(vendor == OpenAICompatibleVendor::MINIMAX, "minimax vendor should resolve");
    }

    {
        const auto vendor = resolve_openai_compatible_vendor("https://dashscope.aliyuncs.com/compatible-mode/v1");
        expect(vendor == OpenAICompatibleVendor::QWEN, "qwen vendor should resolve");
    }

    {
        const auto vendor = resolve_openai_compatible_vendor("https://open.bigmodel.cn/api/paas/v4");
        expect(vendor == OpenAICompatibleVendor::GLM, "glm vendor should resolve");
    }

    {
        const auto vendor = resolve_openai_compatible_vendor("https://api.openai.com/v1");
        expect(vendor == OpenAICompatibleVendor::GENERIC, "generic vendor should resolve");
    }
}

void test_minimax_request_injects_reasoning_split() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    MinimaxProvider provider("test-key", "https://api.minimax.chat/v1", "MiniMax-M1");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.value("reasoning_split", false), "minimax requests should inject reasoning_split");
}

void test_generic_request_does_not_inject_reasoning_split() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    GenericOpenAIProvider provider("test-key", "https://api.openai.com/v1", "gpt-4o");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(!body_json.contains("reasoning_split"), "generic requests should not inject reasoning_split");
}

void test_glm_request_uses_generic_openai_compatible_shape() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    GlmProvider provider("glm-key", "https://open.bigmodel.cn/api/paas/v4", "glm-5");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    expect(provider.get_provider_name() == "GlmProvider",
           "glm provider name should expose the concrete provider class");
    expect(fake_http_raw->captured_url == "https://open.bigmodel.cn/api/paas/v4/chat/completions",
           "glm should use the standard chat completions path");
    expect(fake_http_raw->captured_headers["Authorization"] == "Bearer glm-key",
           "glm should use bearer authorization");

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.value("model", "") == "test-model",
           "glm request should preserve the normalized model field");
    expect(!body_json.contains("reasoning_split"),
           "glm request should not inherit minimax-specific reasoning_split");
}

void test_glm_request_omits_thinking_when_not_configured() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    GlmProvider provider("glm-key", "https://open.bigmodel.cn/api/paas/v4", "glm-5");
    provider.set_http_client(std::move(fake_http));

    ChatCompletionRequest request = make_basic_request();
    request.model = "glm-5";
    auto response = provider.chat_completion(request);
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(!body_json.contains("thinking"),
           "glm request should not send thinking config when request leaves it unset");
}

void test_glm_request_can_enable_thinking() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    GlmProvider provider("glm-key", "https://open.bigmodel.cn/api/paas/v4", "glm-5");
    provider.set_http_client(std::move(fake_http));

    ChatCompletionRequest request = make_basic_request();
    request.model = "glm-5";
    request.enable_thinking = true;
    auto response = provider.chat_completion(request);
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.contains("thinking"), "glm request should include thinking config when enabled");
    expect(body_json["thinking"]["type"] == "enabled",
           "glm thinking config should be encoded as enabled");
}

void test_glm_request_can_disable_thinking() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    GlmProvider provider("glm-key", "https://open.bigmodel.cn/api/paas/v4", "glm-5");
    provider.set_http_client(std::move(fake_http));

    ChatCompletionRequest request = make_basic_request();
    request.model = "glm-5";
    request.enable_thinking = false;
    auto response = provider.chat_completion(request);
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.contains("thinking"), "glm request should include thinking config when disabled");
    expect(body_json["thinking"]["type"] == "disabled",
           "glm thinking config should be encoded as disabled");
}

void test_qwen_stream_parser_uses_index_matching() {
    OpenAIStreamParser parser(OpenAIStreamParser::ToolCallMatchMode::BY_INDEX);

    ChatCompletionResponse first_chunk;
    const std::string first_event =
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"weather","arguments":"{"}}]}}]})";
    expect(parser.parse_chunk(first_event, first_chunk), "qwen first tool_call chunk should parse");

    ChatCompletionResponse second_chunk;
    const std::string second_event =
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"city\":\"Hangzhou\"}"}}],"content":null},"finish_reason":"tool_calls"}]})";
    expect(parser.parse_chunk(second_event, second_chunk), "qwen second tool_call chunk should parse");
    expect(second_chunk.is_stream_end, "finish_reason=tool_calls should end stream");
    expect(second_chunk.tool_calls.size() == 1, "qwen chunks should accumulate into one tool call");
    expect(second_chunk.tool_calls[0].name == "weather", "qwen accumulated tool name should match");
    expect(second_chunk.tool_calls[0].index == 0, "qwen accumulated tool index should match");
    expect(second_chunk.tool_calls[0].arguments["city"] == "Hangzhou",
           "qwen accumulated tool arguments should merge by index");
}

void test_qwen_stream_parser_tolerates_null_string_fields() {
    OpenAIStreamParser parser(OpenAIStreamParser::ToolCallMatchMode::BY_INDEX);

    ChatCompletionResponse first_chunk;
    const std::string first_event =
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":null,"function":{"name":"weather","arguments":"{"}}]},"finish_reason":null}]})";
    expect(parser.parse_chunk(first_event, first_chunk),
           "qwen chunk with null finish_reason and null id should still parse");
    expect(!first_chunk.is_stream_end,
           "null finish_reason should not end stream");

    ChatCompletionResponse second_chunk;
    const std::string second_event =
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":null,"function":{"name":null,"arguments":"\"city\":\"Hangzhou\"}"}}]},"finish_reason":"tool_calls"}]})";
    expect(parser.parse_chunk(second_event, second_chunk),
           "qwen chunk with null tool-call string fields should still parse");
    expect(second_chunk.is_stream_end,
           "tool_calls finish_reason should still end stream");
    expect(second_chunk.tool_calls.size() == 1,
           "qwen null-safe parsing should still accumulate one tool call");
    expect(second_chunk.tool_calls[0].name == "weather",
           "existing non-null tool name should be preserved when later chunk name is null");
    expect(second_chunk.tool_calls[0].arguments["city"] == "Hangzhou",
           "arguments should still merge when earlier/later chunks contain null string fields");
}

void test_qwen_request_omits_enable_thinking_when_not_configured() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    QwenProvider provider("qwen-key", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(!body_json.contains("enable_thinking"),
           "qwen request should not send enable_thinking when request leaves it unset");
}

void test_qwen_request_can_enable_thinking() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    QwenProvider provider("qwen-key", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max");
    provider.set_http_client(std::move(fake_http));

    ChatCompletionRequest request = make_basic_request();
    request.enable_thinking = true;
    auto response = provider.chat_completion(request);
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.contains("enable_thinking"),
           "qwen request should include enable_thinking when enabled");
    expect(body_json["enable_thinking"] == true,
           "qwen enable_thinking should be encoded as true");
}

void test_qwen_request_can_disable_thinking() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    QwenProvider provider("qwen-key", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max");
    provider.set_http_client(std::move(fake_http));

    ChatCompletionRequest request = make_basic_request();
    request.enable_thinking = false;
    auto response = provider.chat_completion(request);
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.contains("enable_thinking"),
           "qwen request should include enable_thinking when disabled");
    expect(body_json["enable_thinking"] == false,
           "qwen enable_thinking should be encoded as false");
}

void test_factory_returns_specialized_provider_classes() {
    {
        auto provider = LLMProviderFactory::create_openai_compatible_provider(
            "test-key", "https://api.minimax.chat/v1", "MiniMax-M1");
        expect(dynamic_cast<MinimaxProvider*>(provider.get()) != nullptr,
               "factory should return MinimaxProvider");
    }

    {
        auto provider = LLMProviderFactory::create_openai_compatible_provider(
            "test-key", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max");
        expect(dynamic_cast<QwenProvider*>(provider.get()) != nullptr,
               "factory should return QwenProvider");
    }

    {
        auto provider = LLMProviderFactory::create_openai_compatible_provider(
            "test-key", "https://open.bigmodel.cn/api/paas/v4", "glm-5");
        expect(dynamic_cast<GlmProvider*>(provider.get()) != nullptr,
               "factory should return GlmProvider");
    }

    {
        auto provider = LLMProviderFactory::create_openai_compatible_provider(
            "test-key", "https://api.openai.com/v1", "gpt-4o");
        expect(dynamic_cast<GenericOpenAIProvider*>(provider.get()) != nullptr,
               "factory should return GenericOpenAIProvider");
    }
}

void test_specialized_minimax_provider_injects_reasoning_split() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    MinimaxProvider provider("test-key", "https://api.minimax.chat/v1", "MiniMax-M1");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.value("reasoning_split", false),
           "specialized minimax provider should inject reasoning_split");
}

void test_specialized_glm_provider_controls_thinking() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    GlmProvider provider("glm-key", "https://open.bigmodel.cn/api/paas/v4", "glm-5");
    provider.set_http_client(std::move(fake_http));

    ChatCompletionRequest request = make_basic_request();
    request.enable_thinking = false;
    auto response = provider.chat_completion(request);
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.contains("thinking"),
           "specialized glm provider should emit thinking config");
    expect(body_json["thinking"]["type"] == "disabled",
           "specialized glm provider should encode disabled thinking");
}

void test_specialized_qwen_provider_uses_index_matching_in_streams() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();
    fake_http_raw->stream_events = {
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"weather","arguments":"{"}}]}}]})",
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"city\":\"Hangzhou\"}"}}]},"finish_reason":"tool_calls"}]})"
    };

    QwenProvider provider("qwen-key", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max");
    provider.set_http_client(std::move(fake_http));

    std::vector<ChatCompletionResponse> responses;
    provider.chat_completion_stream(make_basic_request(), [&](const ChatCompletionResponse& response) {
        responses.push_back(response);
    });

    expect(fake_http_raw->captured_url == "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
           "specialized qwen provider should use standard chat completions path");
    expect(!responses.empty(), "specialized qwen provider should emit stream responses");
    const auto& final_response = responses.back();
    expect(final_response.is_stream_end, "specialized qwen provider should end stream on tool_calls");
    expect(final_response.tool_calls.size() == 1,
           "specialized qwen provider should accumulate tool calls by index");
    expect(final_response.tool_calls[0].arguments["city"] == "Hangzhou",
           "specialized qwen provider should merge tool arguments by index");
}

void test_glm_stream_parser_supports_reasoning_content() {
    OpenAIStreamParser parser;

    ChatCompletionResponse first_chunk;
    const std::string first_event =
        R"(data: {"choices":[{"delta":{"reasoning_content":"先分析问题","content":null}}]})";
    expect(parser.parse_chunk(first_event, first_chunk), "glm reasoning chunk should parse");
    expect_equal(first_chunk.reasoning_content, "先分析问题",
                 "glm reasoning_content should be surfaced as reasoning delta");
    expect(first_chunk.content.empty(), "glm reasoning-only chunk should not emit text");

    ChatCompletionResponse second_chunk;
    const std::string second_event =
        R"(data: {"choices":[{"delta":{"reasoning_content":"先分析问题，再回答","content":"答案是 4"},"finish_reason":"stop"}]})";
    expect(parser.parse_chunk(second_event, second_chunk), "glm combined reasoning/text chunk should parse");
    expect_equal(second_chunk.reasoning_content, "，再回答",
                 "glm reasoning snapshot should be normalized to delta");
    expect_equal(second_chunk.content, "答案是 4",
                 "glm text content should still be parsed normally");
    expect(second_chunk.is_stream_end, "glm finish_reason should still end stream");
}

void test_parse_response_extracts_usage() {
    GenericOpenAIProvider provider("test-key", "https://api.openai.com/v1", "gpt-4o");

    const auto response = provider.parse_response(nlohmann::json::parse(
        R"({
            "choices":[{"finish_reason":"stop","message":{"content":"ok"}}],
            "usage":{"prompt_tokens":12,"completion_tokens":34,"total_tokens":46}
        })"));

    expect(response.usage.has_value(), "non-stream response should expose usage");
    expect(response.usage->prompt_tokens == 12, "prompt_tokens should match");
    expect(response.usage->completion_tokens == 34, "completion_tokens should match");
    expect(response.usage->total_tokens == 46, "total_tokens should match");
}

void test_stream_parser_extracts_usage_from_final_chunk() {
    OpenAIStreamParser parser;

    ChatCompletionResponse response;
    const std::string final_event =
        R"(data: {"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":34,"total_tokens":46}})";
    expect(parser.parse_chunk(final_event, response), "final stream chunk should parse");
    expect(response.is_stream_end, "finish_reason=stop should end stream");
    expect(response.usage.has_value(), "final stream chunk should expose usage");
    expect(response.usage->prompt_tokens == 12, "stream prompt_tokens should match");
    expect(response.usage->completion_tokens == 34, "stream completion_tokens should match");
    expect(response.usage->total_tokens == 46, "stream total_tokens should match");
}

} // namespace
} // namespace icraw

int main() {
    icraw::test_vendor_resolution();
    icraw::test_minimax_request_injects_reasoning_split();
    icraw::test_generic_request_does_not_inject_reasoning_split();
    icraw::test_glm_request_uses_generic_openai_compatible_shape();
    icraw::test_glm_request_omits_thinking_when_not_configured();
    icraw::test_glm_request_can_enable_thinking();
    icraw::test_glm_request_can_disable_thinking();
    icraw::test_qwen_stream_parser_uses_index_matching();
    icraw::test_qwen_stream_parser_tolerates_null_string_fields();
    icraw::test_qwen_request_omits_enable_thinking_when_not_configured();
    icraw::test_qwen_request_can_enable_thinking();
    icraw::test_qwen_request_can_disable_thinking();
    icraw::test_glm_stream_parser_supports_reasoning_content();
    icraw::test_parse_response_extracts_usage();
    icraw::test_stream_parser_extracts_usage_from_final_chunk();
    icraw::test_factory_returns_specialized_provider_classes();
    icraw::test_specialized_minimax_provider_injects_reasoning_split();
    icraw::test_specialized_glm_provider_controls_thinking();
    icraw::test_specialized_qwen_provider_uses_index_matching_in_streams();
    std::cout << "icraw_llm_provider_tests: PASS" << std::endl;
    return 0;
}
