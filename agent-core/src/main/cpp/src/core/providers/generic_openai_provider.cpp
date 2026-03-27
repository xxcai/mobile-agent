#include "icraw/core/providers/generic_openai_provider.hpp"

namespace icraw {

GenericOpenAIProvider::GenericOpenAIProvider(const std::string& api_key,
                                             const std::string& base_url,
                                             const std::string& default_model)
    : OpenAICompatibleProviderBase(api_key, base_url, default_model) {
    set_provider_name("generic");
    refresh_stream_parser();
}

std::string GenericOpenAIProvider::get_provider_name() const {
    return "GenericOpenAIProvider";
}

void GenericOpenAIProvider::apply_request_quirks(const ChatCompletionRequest& request,
                                                 nlohmann::json& body) const {
    (void) request;
    (void) body;
}

} // namespace icraw
