#include "icraw/core/providers/qwen_provider.hpp"
#include "icraw/log/logger.hpp"

namespace icraw {

QwenProvider::QwenProvider(const std::string& api_key,
                           const std::string& base_url,
                           const std::string& default_model)
    : OpenAICompatibleProviderBase(api_key, base_url, default_model) {
    set_provider_name("qwen");
    refresh_stream_parser();
}

std::string QwenProvider::get_provider_name() const {
    return "QwenProvider";
}

void QwenProvider::apply_request_quirks(const ChatCompletionRequest& request,
                                        nlohmann::json& body) const {
    if (request.enable_thinking.has_value()) {
        body["enable_thinking"] = *request.enable_thinking;
    }
}

std::unique_ptr<StreamParser> QwenProvider::create_provider_stream_parser() const {
    ICRAW_LOG_DEBUG("[LlmProvider][profile_debug] profile={} parser_mode=by_index",
            provider_name_);
    return std::make_unique<OpenAIStreamParser>(OpenAIStreamParser::ToolCallMatchMode::BY_INDEX);
}

} // namespace icraw
