#pragma once

#include <string>
#include <memory>
#include "icraw/types.hpp"
#include "icraw/config.hpp"

namespace icraw {

class MemoryManager;
class SkillLoader;
class ToolRegistry;

class PromptBuilder {
public:
    PromptBuilder(std::shared_ptr<MemoryManager> memory_manager,
                  std::shared_ptr<SkillLoader> skill_loader,
                  std::shared_ptr<ToolRegistry> tool_registry);

    // Full system prompt: SOUL + AGENTS + TOOLS + skills + memory.
    std::string build_full() const;
    std::string build_full(const SkillsConfig& skills_config) const;
    std::string build_full(const SkillsConfig& skills_config,
                           const std::string& session_id) const;

    // Minimal system prompt: identity + tools only.
    std::string build_minimal() const;

private:
    std::shared_ptr<MemoryManager> memory_manager_;
    std::shared_ptr<SkillLoader> skill_loader_;
    std::shared_ptr<ToolRegistry> tool_registry_;

    std::string get_section(const std::string& filename) const;
    std::string get_runtime_info() const;
    std::string format_tool_schemas(const std::vector<ToolSchema>& schemas) const;
    std::string format_parameter_block(const nlohmann::json& schema,
                                       const std::string& indent,
                                       bool include_heading) const;
    std::string build_memory_section(const std::string& session_id) const;
};

} // namespace icraw