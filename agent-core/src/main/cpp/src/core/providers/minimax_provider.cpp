#include "icraw/core/providers/minimax_provider.hpp"

namespace icraw {

MinimaxProvider::MinimaxProvider(const std::string& api_key,
                                 const std::string& base_url,
                                 const std::string& default_model)
    : OpenAICompatibleProviderBase(api_key, base_url, default_model) {
    set_provider_name("minimax");
    refresh_stream_parser();
}

std::string MinimaxProvider::get_provider_name() const {
    return "MinimaxProvider";
}

void MinimaxProvider::apply_request_quirks(const ChatCompletionRequest& request,
                                           nlohmann::json& body) const {
    (void) request;
    body["reasoning_split"] = true;
}

} // namespace icraw
