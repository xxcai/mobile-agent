#include "icraw/core/llm_provider_factory.hpp"

#include "icraw/core/providers/generic_openai_provider.hpp"
#include "icraw/core/providers/glm_provider.hpp"
#include "icraw/core/providers/minimax_provider.hpp"
#include "icraw/core/providers/qwen_provider.hpp"

namespace icraw {

std::shared_ptr<LLMProvider> LLMProviderFactory::create_openai_compatible_provider(
    const std::string& api_key,
    const std::string& base_url,
    const std::string& default_model) {
    const auto vendor = resolve_openai_compatible_vendor(base_url);

    switch (vendor) {
        case OpenAICompatibleVendor::MINIMAX:
            return std::make_shared<MinimaxProvider>(api_key, base_url, default_model);
        case OpenAICompatibleVendor::QWEN:
            return std::make_shared<QwenProvider>(api_key, base_url, default_model);
        case OpenAICompatibleVendor::GLM:
            return std::make_shared<GlmProvider>(api_key, base_url, default_model);
        case OpenAICompatibleVendor::GENERIC:
        default:
            return std::make_shared<GenericOpenAIProvider>(api_key, base_url, default_model);
    }
}

} // namespace icraw
