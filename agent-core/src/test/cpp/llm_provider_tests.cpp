#include "icraw/core/http_client.hpp"
#include "icraw/core/llm_provider.hpp"
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
    std::string captured_url;
    std::string captured_method;
    std::string captured_request_body;
    HttpHeaders captured_headers;
    std::string response_body =
        R"({"choices":[{"finish_reason":"stop","message":{"content":"ok"}}]})";

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

void test_profile_resolution() {
    {
        const auto profile = resolve_openai_compatible_profile("https://api.minimax.chat/v1");
        expect(profile.vendor == OpenAICompatibleVendor::MINIMAX, "minimax vendor should resolve");
        expect(profile.profile_name == "minimax", "minimax profile name should resolve");
        expect(profile.enable_reasoning_split, "minimax should enable reasoning_split");
    }

    {
        const auto profile = resolve_openai_compatible_profile("https://dashscope.aliyuncs.com/compatible-mode/v1");
        expect(profile.vendor == OpenAICompatibleVendor::QWEN, "qwen vendor should resolve");
        expect(profile.tool_call_match_mode == OpenAIStreamParser::ToolCallMatchMode::BY_INDEX,
               "qwen should use BY_INDEX parser mode");
    }

    {
        const auto profile = resolve_openai_compatible_profile("https://open.bigmodel.cn/api/paas/v4");
        expect(profile.vendor == OpenAICompatibleVendor::GLM, "glm vendor should resolve");
        expect(profile.profile_name == "glm", "glm profile name should resolve");
        expect(!profile.enable_reasoning_split, "glm should not enable minimax quirks by default");
    }

    {
        const auto profile = resolve_openai_compatible_profile("https://api.openai.com/v1");
        expect(profile.vendor == OpenAICompatibleVendor::GENERIC, "generic vendor should resolve");
        expect(!profile.enable_reasoning_split, "generic should not enable minimax quirks");
    }
}

void test_minimax_request_injects_reasoning_split() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    OpenAICompatibleProvider provider("test-key", "https://api.minimax.chat/v1", "MiniMax-M1");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(body_json.value("reasoning_split", false), "minimax requests should inject reasoning_split");
}

void test_generic_request_does_not_inject_reasoning_split() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    OpenAICompatibleProvider provider("test-key", "https://api.openai.com/v1", "gpt-4o");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    const auto body_json = nlohmann::json::parse(fake_http_raw->captured_request_body);
    expect(!body_json.contains("reasoning_split"), "generic requests should not inject reasoning_split");
}

void test_glm_request_uses_generic_openai_compatible_shape() {
    auto fake_http = std::make_unique<FakeHttpClient>();
    auto* fake_http_raw = fake_http.get();

    OpenAICompatibleProvider provider("glm-key", "https://open.bigmodel.cn/api/paas/v4", "glm-5");
    provider.set_http_client(std::move(fake_http));

    auto response = provider.chat_completion(make_basic_request());
    (void) response;

    expect(provider.get_provider_name() == "OpenAI-Compatible(glm)",
           "glm provider name should expose glm profile");
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

void test_qwen_stream_parser_uses_index_matching() {
    const auto profile = resolve_openai_compatible_profile("https://dashscope.aliyuncs.com/compatible-mode/v1");
    auto parser = create_stream_parser(profile);

    auto* openai_parser = dynamic_cast<OpenAIStreamParser*>(parser.get());
    expect(openai_parser != nullptr, "qwen profile should still use OpenAIStreamParser");

    ChatCompletionResponse first_chunk;
    const std::string first_event =
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"weather","arguments":"{"}}]}}]})";
    expect(parser->parse_chunk(first_event, first_chunk), "qwen first tool_call chunk should parse");

    ChatCompletionResponse second_chunk;
    const std::string second_event =
        R"(data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"city\":\"Hangzhou\"}"}}],"content":null},"finish_reason":"tool_calls"}]})";
    expect(parser->parse_chunk(second_event, second_chunk), "qwen second tool_call chunk should parse");
    expect(second_chunk.is_stream_end, "finish_reason=tool_calls should end stream");
    expect(second_chunk.tool_calls.size() == 1, "qwen chunks should accumulate into one tool call");
    expect(second_chunk.tool_calls[0].name == "weather", "qwen accumulated tool name should match");
    expect(second_chunk.tool_calls[0].index == 0, "qwen accumulated tool index should match");
    expect(second_chunk.tool_calls[0].arguments["city"] == "Hangzhou",
           "qwen accumulated tool arguments should merge by index");
}

void test_glm_stream_parser_supports_reasoning_content() {
    const auto profile = resolve_openai_compatible_profile("https://open.bigmodel.cn/api/paas/v4");
    auto parser = create_stream_parser(profile);

    ChatCompletionResponse first_chunk;
    const std::string first_event =
        R"(data: {"choices":[{"delta":{"reasoning_content":"先分析问题","content":null}}]})";
    expect(parser->parse_chunk(first_event, first_chunk), "glm reasoning chunk should parse");
    expect_equal(first_chunk.reasoning_content, "先分析问题",
                 "glm reasoning_content should be surfaced as reasoning delta");
    expect(first_chunk.content.empty(), "glm reasoning-only chunk should not emit text");

    ChatCompletionResponse second_chunk;
    const std::string second_event =
        R"(data: {"choices":[{"delta":{"reasoning_content":"先分析问题，再回答","content":"答案是 4"},"finish_reason":"stop"}]})";
    expect(parser->parse_chunk(second_event, second_chunk), "glm combined reasoning/text chunk should parse");
    expect_equal(second_chunk.reasoning_content, "，再回答",
                 "glm reasoning snapshot should be normalized to delta");
    expect_equal(second_chunk.content, "答案是 4",
                 "glm text content should still be parsed normally");
    expect(second_chunk.is_stream_end, "glm finish_reason should still end stream");
}

} // namespace
} // namespace icraw

int main() {
    icraw::test_profile_resolution();
    icraw::test_minimax_request_injects_reasoning_split();
    icraw::test_generic_request_does_not_inject_reasoning_split();
    icraw::test_glm_request_uses_generic_openai_compatible_shape();
    icraw::test_qwen_stream_parser_uses_index_matching();
    icraw::test_glm_stream_parser_supports_reasoning_content();
    std::cout << "icraw_llm_provider_tests: PASS" << std::endl;
    return 0;
}
