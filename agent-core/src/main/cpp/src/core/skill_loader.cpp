#include "icraw/core/skill_loader.hpp"
#include "icraw/core/logger.hpp"
#include <fstream>
#include <sstream>
#include <regex>
#include <unordered_set>

#ifdef _WIN32
    #ifndef NOMINMAX
    #define NOMINMAX
    #endif
    #include <windows.h>
#endif

namespace icraw {

std::vector<SkillMetadata> SkillLoader::load_skills_from_directory(
    const std::filesystem::path& skills_dir) const {

    std::vector<SkillMetadata> skills;

    if (!std::filesystem::exists(skills_dir) ||
        !std::filesystem::is_directory(skills_dir)) {
        return skills;
    }
    
    for (const auto& entry : std::filesystem::directory_iterator(skills_dir)) {
        if (entry.is_directory()) {
            auto skill_file = entry.path() / "SKILL.md";
            if (std::filesystem::exists(skill_file)) {
                try {
                    auto skill = parse_skill_file(skill_file);
                    skill.name = entry.path().filename().string();
                    skills.push_back(std::move(skill));
                } catch (const std::exception& e) {
                    // Skip malformed skill files
                    continue;
                }
            }
        }
    }
    
    return skills;
}

std::vector<SkillMetadata> SkillLoader::load_skills(
    const SkillsConfig& skills_config,
    const std::filesystem::path& workspace_path) const {

    std::vector<SkillMetadata> all_skills;
    std::unordered_set<std::string> seen_names;

    // Load from workspace skills directory
    auto workspace_skills = workspace_path / "skills";
    if (std::filesystem::exists(workspace_skills)) {
        auto skills = load_skills_from_directory(workspace_skills);
        for (auto& skill : skills) {
            if (seen_names.find(skill.name) == seen_names.end()) {
                seen_names.insert(skill.name);
                all_skills.push_back(std::move(skill));
            }
        }
    }

    // Load from extra directories
    for (const auto& extra_dir : skills_config.extra_dirs) {
        auto extra_path = std::filesystem::path(extra_dir);
        if (!extra_path.is_absolute()) {
            extra_path = workspace_path / extra_path;
        }

        if (std::filesystem::exists(extra_path)) {
            auto skills = load_skills_from_directory(extra_path);
            for (auto& skill : skills) {
                if (seen_names.find(skill.name) == seen_names.end()) {
                    seen_names.insert(skill.name);
                    all_skills.push_back(std::move(skill));
                }
            }
        }
    }

    // Log loaded skills
    ICRAW_LOG_INFO("SkillLoader: Total loaded {} skills", all_skills.size());
    for (const auto& skill : all_skills) {
        ICRAW_LOG_INFO("SkillLoader: Loaded skill: {}", skill.name);
    }

    return all_skills;
}

std::string SkillLoader::get_skill_context(const std::vector<SkillMetadata>& skills) const {
    std::ostringstream ss;

    for (const auto& skill : skills) {
        ss << "## Skill: " << skill.name << "\n";
        if (!skill.description.empty()) {
            ss << "Description: " << skill.description << "\n";
        }
        ss << "\n" << skill.content << "\n\n";
    }

    return ss.str();
}

std::string SkillLoader::build_skills_summary(const std::vector<SkillMetadata>& skills) const {
    std::ostringstream ss;

    ss << "<skills>\n";
    for (const auto& skill : skills) {
        bool available = true;
        std::string missing_reason;

        // Check OS restrictions
        if (!skill.os_restrict.empty()) {
            if (!check_os_restriction(skill.os_restrict)) {
                available = false;
                missing_reason = "OS not supported";
            }
        }

        // Check required binaries
        if (available && !skill.required_bins.empty()) {
            for (const auto& bin : skill.required_bins) {
                // Simple PATH check (platform-independent)
                if (!check_binary_exists(bin)) {
                    available = false;
                    if (missing_reason.empty()) {
                        missing_reason = "Missing binary: " + bin;
                    } else {
                        missing_reason += ", " + bin;
                    }
                }
            }
        }

        // Check anyBins (at least one must exist)
        if (available && !skill.any_bins.empty()) {
            bool any_found = false;
            for (const auto& bin : skill.any_bins) {
                if (check_binary_exists(bin)) {
                    any_found = true;
                    break;
                }
            }
            if (!any_found) {
                available = false;
                if (missing_reason.empty()) {
                    missing_reason = "Missing any binary: " + skill.any_bins[0];
                } else {
                    missing_reason += ", missing binary";
                }
            }
        }

        // Check required environment variables
        if (available && !skill.required_envs.empty()) {
            for (const auto& env : skill.required_envs) {
                if (check_env_var(env).empty()) {
                    available = false;
                    if (missing_reason.empty()) {
                        missing_reason = "Missing ENV: " + env;
                    } else {
                        missing_reason += ", " + env;
                    }
                }
            }
        }

        // Escape XML special characters
        auto escape_xml = [](const std::string& s) -> std::string {
            std::string result;
            for (char c : s) {
                switch (c) {
                    case '&': result += "&amp;"; break;
                    case '<': result += "&lt;"; break;
                    case '>': result += "&gt;"; break;
                    case '"': result += "&quot;"; break;
                    case '\'': result += "&apos;"; break;
                    default: result += c;
                }
            }
            return result;
        };

        ss << "  <skill available=\"" << (available ? "true" : "false") << "\">\n";
        ss << "    <name>" << escape_xml(skill.name) << "</name>\n";
        ss << "    <description>" << escape_xml(skill.description) << "</description>\n";

        if (!available && !missing_reason.empty()) {
            ss << "    <requires>" << escape_xml(missing_reason) << "</requires>\n";
        }

        ss << "  </skill>\n";
    }
    ss << "</skills>";

    return ss.str();
}

std::vector<SkillMetadata> SkillLoader::get_always_skills(const std::vector<SkillMetadata>& skills) const {
    std::vector<SkillMetadata> always_skills;

    for (const auto& skill : skills) {
        if (skill.always) {
            always_skills.push_back(skill);
        }
    }

    return always_skills;
}

bool SkillLoader::check_binary_exists(const std::string& binary) const {
    // Platform-independent binary existence check
    std::string cmd;

#ifdef _WIN32
    cmd = "where " + binary + " >nul 2>&1";
#else
    cmd = "which " + binary + " >/dev/null 2>&1";
#endif

    int result = std::system(cmd.c_str());
    return result == 0;
}

std::string SkillLoader::check_env_var(const std::string& var_name) const {
    // Platform-independent environment variable check
#ifdef _WIN32
    // Windows: use GetEnvironmentVariable
    char buffer[32767];
    DWORD size = GetEnvironmentVariableA(var_name.c_str(), buffer, sizeof(buffer));
    return (size > 0 && size < sizeof(buffer)) ? std::string(buffer, size) : "";
#else
    // Unix-like: use getenv
    const char* val = std::getenv(var_name.c_str());
    return val ? std::string(val) : "";
#endif
}

SkillMetadata SkillLoader::parse_skill_file(const std::filesystem::path& skill_file) const {
    SkillMetadata skill;
    std::string content = read_file_content(skill_file);
    
    // Parse YAML frontmatter
    // Format: ---\nYAML\n---\nContent
    static std::regex frontmatter_regex("^---\\s*\n([\\s\\S]*?)\n---\\s*\n([\\s\\S]*)$");
    std::smatch match;
    
    if (std::regex_match(content, match, frontmatter_regex)) {
        std::string yaml_str = match[1].str();
        skill.content = match[2].str();
        
        auto yaml_json = parse_yaml_frontmatter(yaml_str);
        
        if (yaml_json.contains("description")) {
            skill.description = yaml_json["description"].get<std::string>();
            // Strip trailing \r (Windows line ending)
            while (!skill.description.empty() && skill.description.back() == '\r') {
                skill.description.pop_back();
            }
        }
        if (yaml_json.contains("requiredBins")) {
            for (const auto& bin : yaml_json["requiredBins"]) {
                skill.required_bins.push_back(bin.get<std::string>());
            }
        }
        if (yaml_json.contains("requiredEnvs")) {
            for (const auto& env : yaml_json["requiredEnvs"]) {
                skill.required_envs.push_back(env.get<std::string>());
            }
        }
        if (yaml_json.contains("anyBins")) {
            for (const auto& bin : yaml_json["anyBins"]) {
                skill.any_bins.push_back(bin.get<std::string>());
            }
        }
        if (yaml_json.contains("os")) {
            for (const auto& os : yaml_json["os"]) {
                skill.os_restrict.push_back(os.get<std::string>());
            }
        }
        if (yaml_json.contains("always")) {
            skill.always = yaml_json["always"].get<bool>();
        }
        if (yaml_json.contains("emoji")) {
            skill.emoji = yaml_json["emoji"].get<std::string>();
            // Strip trailing \r (Windows line ending)
            while (!skill.emoji.empty() && skill.emoji.back() == '\r') {
                skill.emoji.pop_back();
            }
        }
    } else {
        // No frontmatter, entire content is the skill
        skill.content = content;
    }
    
    return skill;
}

nlohmann::json SkillLoader::parse_yaml_frontmatter(const std::string& yaml_str) const {
    nlohmann::json result = nlohmann::json::object();

    std::istringstream ss(yaml_str);
    std::string line;

    // Helper functions
    auto trim = [](const std::string& s) -> std::string {
        size_t start = s.find_first_not_of(" \t\r\n");
        if (start == std::string::npos) return "";
        size_t end = s.find_last_not_of(" \t\r\n");
        return s.substr(start, end - start + 1);
    };

    auto get_indent = [](const std::string& s) -> int {
        int count = 0;
        for (char c : s) {
            if (c == ' ' || c == '\t') {
                count++;
            } else {
                break;
            }
        }
        return count;
    };

    auto parse_value = [&](const std::string& s) -> nlohmann::json {
        std::string trimmed = trim(s);
        if (trimmed.empty()) {
            return nullptr;
        }
        // Check for boolean
        if (trimmed == "true") return true;
        if (trimmed == "false") return false;
        // Check for quoted string
        if (trimmed.size() >= 2 &&
            ((trimmed.front() == '"' && trimmed.back() == '"') ||
             (trimmed.front() == '\'' && trimmed.back() == '\''))) {
            return trimmed.substr(1, trimmed.size() - 2);
        }
        // Check for number
        bool is_number = true;
        bool has_dot = false;
        for (size_t i = 0; i < trimmed.size(); ++i) {
            char c = trimmed[i];
            if (i == 0 && (c == '-' || c == '+')) continue;
            if (c == '.') {
                if (has_dot) { is_number = false; break; }
                has_dot = true;
            } else if (c < '0' || c > '9') {
                is_number = false; break;
            }
        }
        if (is_number && !trimmed.empty()) {
            if (has_dot) return std::stod(trimmed);
            return std::stoll(trimmed);
        }
        // Default: string
        return trimmed;
    };

    // Parse line by line
    std::vector<std::pair<int, nlohmann::json*>> stack;  // (indent, pointer to JSON object)
    stack.push_back({0, &result});

    while (std::getline(ss, line)) {
        // Skip empty lines and comments
        if (line.empty() || line.find_first_not_of(" \t\r\n") == std::string::npos || line[0] == '#') {
            continue;
        }

        int indent = get_indent(line);

        // Find the correct parent level based on indentation
        while (stack.size() > 1 && indent <= stack.back().first) {
            stack.pop_back();
        }

        nlohmann::json* parent = stack.back().second;

        size_t colon_pos = line.find(':');
        size_t dash_pos = line.find('-');

        // Check for array item
        if (dash_pos != std::string::npos && (colon_pos == std::string::npos || dash_pos < colon_pos)) {
            size_t actual_dash = line.find('-', dash_pos);
            std::string item = line.substr(actual_dash + 1);
            nlohmann::json value = parse_value(item);

            if (parent->is_array()) {
                parent->push_back(value);
            } else {
                // Convert current parent to array
                nlohmann::json arr = nlohmann::json::array();
                arr.push_back(value);
                *parent = arr;
            }
        } else if (colon_pos != std::string::npos) {
            std::string key = trim(line.substr(0, colon_pos));
            std::string value = line.substr(colon_pos + 1);

            nlohmann::json parsed_value = parse_value(value);

            if (parent->is_null() || parent->is_string() || parent->is_number() || parent->is_boolean()) {
                // Parent was a scalar, replace with object
                nlohmann::json obj = nlohmann::json::object();
                obj[key] = parsed_value;
                *parent = obj;
            } else if (parent->is_array()) {
                // Can't add key to array
                continue;
            } else {
                // Parent is object, add key-value
                (*parent)[key] = parsed_value;
            }

            // Push to stack for nested items
            stack.push_back({indent, &(*parent)[key]});
        }
    }

    return result;
}

bool SkillLoader::check_os_restriction(const std::vector<std::string>& os_list) const {
    if (os_list.empty()) {
        return true;  // No restriction
    }
    
    std::string current_os = get_current_os();
    for (const auto& os : os_list) {
        if (os == current_os) {
            return true;
        }
    }
    
    return false;
}

std::string SkillLoader::get_current_os() const {
#ifdef _WIN32
    return "win32";
#elif __APPLE__
    #include "TargetConditionals.h"
    #if TARGET_OS_IPHONE
    return "ios";
    #else
    return "darwin";
    #endif
#elif __ANDROID__
    return "android";
#elif __linux__
    return "linux";
#else
    return "unknown";
#endif
}

std::string SkillLoader::read_file_content(const std::filesystem::path& filepath) const {
    std::ifstream file(filepath, std::ios::binary);
    if (!file.is_open()) {
        return "";
    }

    std::ostringstream ss;
    ss << file.rdbuf();
    return ss.str();
}

bool SkillLoader::validate_name(const std::string& name) const {
    // AgentSkills spec: 1-64 chars, lowercase a-z, 0-9, hyphens only
    // No leading/trailing hyphens, no consecutive hyphens

    // Check length
    if (name.empty() || name.length() > 64) {
        return false;
    }

    // Check for invalid characters
    for (char c : name) {
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
            return false;
        }
    }

    // Check for leading/trailing hyphens
    if (name[0] == '-' || name.back() == '-') {
        return false;
    }

    // Check for consecutive hyphens
    if (name.find("--") != std::string::npos) {
        return false;
    }

    return true;
}

std::string SkillLoader::normalize_name(const std::string& name) const {
    std::string result;

    // Convert to lowercase and replace spaces with hyphens
    for (char c : name) {
        if (c == ' ') {
            result += '-';
        } else if (c >= 'A' && c <= 'Z') {
            result += (char)(c + 32);  // Convert to lowercase
        } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
            result += c;
        }
        // Skip other characters (underscores, etc.)
    }

    // Remove leading/trailing hyphens
    size_t start = result.find_first_not_of('-');
    if (start != std::string::npos) {
        result = result.substr(start);
    }

    size_t end = result.find_last_not_of('-');
    if (end != std::string::npos) {
        result = result.substr(0, end + 1);
    }

    // Collapse consecutive hyphens
    std::string collapsed;
    bool last_was_hyphen = false;
    for (char c : result) {
        if (c == '-') {
            if (!last_was_hyphen) {
                collapsed += c;
                last_was_hyphen = true;
            }
        } else {
            collapsed += c;
            last_was_hyphen = false;
        }
    }

    return collapsed;
}

} // namespace icraw
