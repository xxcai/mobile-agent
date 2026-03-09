#pragma once

#include <string>
#include <vector>
#include <filesystem>
#include <memory>
#include "icraw/types.hpp"
#include "icraw/config.hpp"

namespace icraw {

class SkillLoader {
public:
    SkillLoader() = default;
    ~SkillLoader() = default;

    // Load skills from directory (OpenClaw SKILL.md compatible)
    std::vector<SkillMetadata> load_skills_from_directory(
        const std::filesystem::path& skills_dir) const;

    // Multi-directory loading with dedup
    std::vector<SkillMetadata> load_skills(
        const SkillsConfig& skills_config,
        const std::filesystem::path& workspace_path) const;

    // Get skill content for LLM context
    std::string get_skill_context(const std::vector<SkillMetadata>& skills) const;

    // Build skills summary for progressive disclosure (Level 1)
    std::string build_skills_summary(const std::vector<SkillMetadata>& skills) const;

    // Get skills marked as always=true
    std::vector<SkillMetadata> get_always_skills(const std::vector<SkillMetadata>& skills) const;

    // Validate skill name according to AgentSkills spec (public for testing)
    bool validate_name(const std::string& name) const;

    // Normalize skill name (public for testing)
    std::string normalize_name(const std::string& name) const;

    // Check if environment variable is set (public for testing)
    std::string check_env_var(const std::string& var_name) const;

private:
    // Parse SKILL.md file
    SkillMetadata parse_skill_file(const std::filesystem::path& skill_file) const;

    // Parse YAML frontmatter
    nlohmann::json parse_yaml_frontmatter(const std::string& yaml_str) const;

    // Check current OS against restriction list
    bool check_os_restriction(const std::vector<std::string>& os_list) const;

    // Get current OS identifier
    std::string get_current_os() const;

    // Read file content
    std::string read_file_content(const std::filesystem::path& filepath) const;

    // Check if binary exists in PATH (platform-independent)
    bool check_binary_exists(const std::string& binary) const;
};

} // namespace icraw
