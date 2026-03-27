#pragma once

#include "icraw/core/llm_provider.hpp"

namespace icraw {

class LLMProviderFactory {
public:
    static std::shared_ptr<LLMProvider> create_openai_compatible_provider(
        const std::string& api_key,
        const std::string& base_url,
        const std::string& default_model);
};

} // namespace icraw
