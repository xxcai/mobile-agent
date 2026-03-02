#include "icraw/core/prompt_builder.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/skill_loader.hpp"
#include "icraw/tools/tool_registry.hpp"
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
        ss << schema.description << "\n\n";
        
        if (!schema.parameters.empty() && schema.parameters.contains("properties")) {
            ss << "Parameters:\n";
            for (auto& [key, value] : schema.parameters["properties"].items()) {
                ss << "- " << key << ": ";
                if (value.contains("type")) {
                    ss << value["type"].get<std::string>();
                }
                if (value.contains("description")) {
                    ss << " - " << value["description"].get<std::string>();
                }
                ss << "\n";
            }
        }
        ss << "\n";
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
