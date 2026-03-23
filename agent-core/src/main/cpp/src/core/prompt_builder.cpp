#include "icraw/core/prompt_builder.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/tools/tool_registry.hpp"
#include <algorithm>
#include <sstream>

namespace icraw {

PromptBuilder::PromptBuilder(std::shared_ptr<MemoryManager> memory_manager,
                             std::shared_ptr<SkillLoader> skill_loader,
                             std::shared_ptr<ToolRegistry> tool_registry)
    : memory_manager_(std::move(memory_manager))
    , skill_loader_(std::move(skill_loader))
    , tool_registry_(std::move(tool_registry)) {
}

std::string PromptBuilder::build_full() const {
    std::ostringstream ss;
    
    // 1. SOUL - Agent identity
    std::string soul = memory_manager_->read_identity_file("SOUL.md");
    if (!soul.empty()) {
        ss << "# Identity\n\n" << soul << "\n\n";
    }

    // 2. USER - User information
    std::string user = memory_manager_->read_identity_file("USER.md");
    if (!user.empty()) {
        ss << "# User Information\n\n" << user << "\n\n";
    }
    
    // 3. AGENTS.md - Behavior instructions
    std::string agents = memory_manager_->read_agents_file();
    if (!agents.empty()) {
        ss << "# Behavior Guidelines\n\n" << agents << "\n\n";
    }
    
    // 4. TOOLS.md - Tool usage guide
    std::string tools = memory_manager_->read_tools_file();
    if (!tools.empty()) {
        ss << "# Tool Usage\n\n" << tools << "\n\n";
    }
    
    // 5. Skills (progressive disclosure)
    auto skills_dir = memory_manager_->get_workspace_path() / "skills";
    if (memory_manager_->file_exists(skills_dir)) {
        SkillsConfig default_config;
        auto skills = skill_loader_->load_skills(default_config, memory_manager_->get_workspace_path());
        if (!skills.empty()) {
            // Level 1: Always skills (full content)
            auto always_skills = skill_loader_->get_always_skills(skills);
            if (!always_skills.empty()) {
                ss << "# Active Skills\n\n";
                ss << skill_loader_->get_skill_context(always_skills) << "\n\n";
                ICRAW_LOG_INFO("[PROMPT] PromptBuilder: Added {} always-skills to prompt", always_skills.size());
            }

            // Level 2: Skills summary (metadata only, on-demand loading)
            ss << "# Available Skills\n\n";
            ss << "The following skills extend your capabilities. ";
            ss << "To use a skill, read its SKILL.md file using read_file tool.\n\n";
            ss << skill_loader_->build_skills_summary(skills);
        }
    }
    
    // 6. MEMORY - Long-term memory (combine file + database summary)
    std::string memory = build_memory_section();
    if (!memory.empty()) {
        ss << "# Memory\n\n" << memory << "\n\n";
    }
    
    // 7. Tool schemas
    auto tool_schemas = tool_registry_->get_tool_schemas();
    if (!tool_schemas.empty()) {
        ss << "# Available Tools\n\n" << format_tool_schemas(tool_schemas) << "\n";
    }
    
    // 8. Runtime info
    ss << get_runtime_info();
    
    return ss.str();
}

std::string PromptBuilder::build_full(const SkillsConfig& skills_config) const {
    std::ostringstream ss;
    
    // 1. SOUL - Agent identity
    std::string soul = memory_manager_->read_identity_file("SOUL.md");
    if (!soul.empty()) {
        ss << "# Identity\n\n" << soul << "\n\n";
    }

    // 2. USER - User information
    std::string user = memory_manager_->read_identity_file("USER.md");
    if (!user.empty()) {
        ss << "# User Information\n\n" << user << "\n\n";
    }
    
    // 3. AGENTS.md - Behavior instructions
    std::string agents = memory_manager_->read_agents_file();
    if (!agents.empty()) {
        ss << "# Behavior Guidelines\n\n" << agents << "\n\n";
    }
    
    // 4. TOOLS.md - Tool usage guide
    std::string tools = memory_manager_->read_tools_file();
    if (!tools.empty()) {
        ss << "# Tool Usage\n\n" << tools << "\n\n";
    }
    
    // 5. Skills (progressive disclosure using provided config)
    auto skills_dir = memory_manager_->get_workspace_path() / "skills";
    if (memory_manager_->file_exists(skills_dir)) {
        auto skills = skill_loader_->load_skills(skills_config, memory_manager_->get_workspace_path());
        if (!skills.empty()) {
            // Level 1: Always skills (full content)
            auto always_skills = skill_loader_->get_always_skills(skills);
            if (!always_skills.empty()) {
                ss << "# Active Skills\n\n";
                ss << skill_loader_->get_skill_context(always_skills) << "\n\n";
                ICRAW_LOG_INFO("[PROMPT] PromptBuilder[ephemeral]: Added {} always-skills to prompt", always_skills.size());
            }

            // Level 2: Skills summary (metadata only, on-demand loading)
            ss << "# Available Skills\n\n";
            ss << "The following skills extend your capabilities. ";
            ss << "To use a skill, read its SKILL.md file using read_file tool.\n\n";
            ss << skill_loader_->build_skills_summary(skills);
        }
    }
    
    // 6. MEMORY - Long-term memory (combine file + database summary)
    std::string memory = build_memory_section();
    if (!memory.empty()) {
        ss << "# Memory\n\n" << memory << "\n\n";
    }
    
    // 7. Tool schemas
    auto tool_schemas = tool_registry_->get_tool_schemas();
    if (!tool_schemas.empty()) {
        ss << "# Available Tools\n\n" << format_tool_schemas(tool_schemas) << "\n";
    }
    
    // 8. Runtime info
    ss << get_runtime_info();
    
    return ss.str();
}

std::string PromptBuilder::build_minimal() const {
    std::ostringstream ss;
    
    // 1. SOUL - Agent identity
    std::string soul = memory_manager_->read_identity_file("SOUL.md");
    if (!soul.empty()) {
        ss << "# Identity\n\n" << soul << "\n\n";
    }
    
    // 2. Tool schemas
    auto tool_schemas = tool_registry_->get_tool_schemas();
    if (!tool_schemas.empty()) {
        ss << "# Available Tools\n\n" << format_tool_schemas(tool_schemas) << "\n";
    }
    
    return ss.str();
}

std::string PromptBuilder::get_section(const std::string& filename) const {
    return memory_manager_->read_identity_file(filename);
}

std::string PromptBuilder::get_runtime_info() const {
    std::ostringstream ss;
    ss << "# Runtime Information\n\n";
    ss << "Platform: ";
    
#ifdef ICRAW_ANDROID
    ss << "Android";
#elif ICRAW_IOS
    ss << "iOS";
#elif _WIN32
    ss << "Windows";
#elif __APPLE__
    ss << "macOS";
#elif __linux__
    ss << "Linux";
#else
    ss << "Unknown";
#endif
    
    ss << "\n";
    return ss.str();
}

std::string PromptBuilder::format_tool_schemas(const std::vector<ToolSchema>& schemas) const {
    std::ostringstream ss;
    
    for (const auto& schema : schemas) {
        ss << "## " << schema.name << "\n";
        ss << "Route role: " << schema.description << "\n\n";

        if (!schema.parameters.empty()) {
            ss << format_parameter_block(schema.parameters, "", true);
        }
        ss << "\n";
    }
    
    return ss.str();
}

std::string PromptBuilder::format_parameter_block(const nlohmann::json& schema,
                                                  const std::string& indent,
                                                  bool include_heading) const {
    std::ostringstream ss;

    if (!schema.is_object()) {
        return "";
    }

    if (include_heading) {
        ss << indent << "Input shape:\n";
    }

    std::vector<std::string> required_fields;
    if (schema.contains("required") && schema["required"].is_array()) {
        for (const auto& item : schema["required"]) {
            if (item.is_string()) {
                required_fields.push_back(item.get<std::string>());
            }
        }
    }

    if (!required_fields.empty()) {
        ss << indent << "Required first:";
        for (size_t i = 0; i < required_fields.size(); ++i) {
            ss << (i == 0 ? " " : ", ") << required_fields[i];
        }
        ss << "\n";
    }

    if (!schema.contains("properties") || !schema["properties"].is_object()) {
        return ss.str();
    }

    for (auto& [key, value] : schema["properties"].items()) {
        const std::string type = value.value("type", "unknown");
        const bool required = std::find(required_fields.begin(), required_fields.end(), key) != required_fields.end();

        ss << indent << "- " << key << ": " << type;
        if (required) {
            ss << " (required)";
        }
        if (value.contains("description") && value["description"].is_string()) {
            ss << " - " << value["description"].get<std::string>();
        }
        ss << "\n";

        if (value.contains("enum") && value["enum"].is_array() && !value["enum"].empty()) {
            ss << indent << "  Choose from: ";
            for (size_t i = 0; i < value["enum"].size(); ++i) {
                if (i > 0) {
                    ss << ", ";
                }
                const auto& enum_value = value["enum"][i];
                if (enum_value.is_string()) {
                    ss << enum_value.get<std::string>();
                } else {
                    ss << enum_value.dump();
                }
            }
            ss << "\n";
        }

        if (value.contains("default")) {
            ss << indent << "  Default value: " << value["default"].dump() << "\n";
        }

        if (type == "object") {
            if (value.contains("properties") && value["properties"].is_object() && !value["properties"].empty()) {
                ss << indent << "  Nested shape:\n";
                ss << format_parameter_block(value, indent + "  ", false);
            } else {
                ss << indent << "  Nested shape is defined by the selected tool or described above.\n";
            }
        } else if (type == "array" && value.contains("items") && value["items"].is_object()) {
            const auto& items = value["items"];
            ss << indent << "  Item shape: " << items.value("type", "unknown");
            if (items.contains("description") && items["description"].is_string()) {
                ss << " - " << items["description"].get<std::string>();
            }
            ss << "\n";
        }
    }

    return ss.str();
}

std::string PromptBuilder::build_memory_section() const {
    std::ostringstream ss;
    
    // Read latest summary from SQLite database
    auto summary = memory_manager_->get_latest_summary("default");
    if (summary.has_value() && !summary->summary.empty()) {
        ss << "## Session Memory\n\n" << summary->summary;
    }
    
    return ss.str();
}

} // namespace icraw
