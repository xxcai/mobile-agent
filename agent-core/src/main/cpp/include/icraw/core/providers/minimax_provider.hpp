#pragma once

#include "icraw/core/openai_compatible_provider_base.hpp"

namespace icraw {

class MinimaxProvider : public OpenAICompatibleProviderBase {
public:
    MinimaxProvider(const std::string& api_key,
                    const std::string& base_url,
                    const std::string& default_model);
    ~MinimaxProvider() override = default;

    std::string get_provider_name() const override;

protected:
    void apply_request_quirks(const ChatCompletionRequest& request,
                              nlohmann::json& body) const override;
};

} // namespace icraw
