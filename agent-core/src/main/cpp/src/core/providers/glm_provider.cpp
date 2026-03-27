#include "icraw/core/providers/glm_provider.hpp"

namespace icraw {

GlmProvider::GlmProvider(const std::string& api_key,
                         const std::string& base_url,
                         const std::string& default_model)
    : OpenAICompatibleProviderBase(api_key, base_url, default_model) {
    set_provider_name("glm");
    refresh_stream_parser();
}

std::string GlmProvider::get_provider_name() const {
    return "GlmProvider";
}

void GlmProvider::apply_request_quirks(const ChatCompletionRequest& request,
                                       nlohmann::json& body) const {
    if (request.enable_thinking.has_value()) {
        body["thinking"]["type"] = *request.enable_thinking ? "enabled" : "disabled";
    }
}

} // namespace icraw
