#pragma once

#include "icraw/core/openai_compatible_provider_base.hpp"

namespace icraw {

class GenericOpenAIProvider : public OpenAICompatibleProviderBase {
public:
    GenericOpenAIProvider(const std::string& api_key,
                          const std::string& base_url = "https://api.openai.com/v1",
                          const std::string& default_model = "gpt-4o");
    ~GenericOpenAIProvider() override = default;

    std::string get_provider_name() const override;

protected:
    void apply_request_quirks(const ChatCompletionRequest& request,
                              nlohmann::json& body) const override;
};

} // namespace icraw
