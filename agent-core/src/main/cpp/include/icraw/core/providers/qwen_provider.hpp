#pragma once

#include "icraw/core/openai_compatible_provider_base.hpp"

namespace icraw {

class QwenProvider : public OpenAICompatibleProviderBase {
public:
    QwenProvider(const std::string& api_key,
                 const std::string& base_url,
                 const std::string& default_model);
    ~QwenProvider() override = default;

    std::string get_provider_name() const override;

protected:
    void apply_request_quirks(const ChatCompletionRequest& request,
                              nlohmann::json& body) const override;
    std::unique_ptr<StreamParser> create_provider_stream_parser() const override;
};

} // namespace icraw
