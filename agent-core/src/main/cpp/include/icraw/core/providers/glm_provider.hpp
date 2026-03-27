#pragma once

#include "icraw/core/openai_compatible_provider_base.hpp"

namespace icraw {

class GlmProvider : public OpenAICompatibleProviderBase {
public:
    GlmProvider(const std::string& api_key,
                const std::string& base_url,
                const std::string& default_model);
    ~GlmProvider() override = default;

    std::string get_provider_name() const override;

protected:
    void apply_request_quirks(const ChatCompletionRequest& request,
                              nlohmann::json& body) const override;
};

} // namespace icraw
