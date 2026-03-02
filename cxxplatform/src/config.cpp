#include "icraw/config.hpp"
#include <fstream>
#include <sstream>
#include <cstdlib>

#ifdef _WIN32
    #include <shlobj.h>
#else
    #include <unistd.h>
    #include <sys/types.h>
    #include <pwd.h>
#endif

namespace icraw {

// MemoryFlushConfig
MemoryFlushConfig MemoryFlushConfig::from_json(const nlohmann::json& json) {
    MemoryFlushConfig config;
    config.enabled = json.value("enabled", true);
    config.soft_threshold_tokens = json.value("softThresholdTokens", 4000);
    config.system_prompt = json.value("systemPrompt", 
        "Pre-compaction memory flush turn.\n"
        "The session is near auto-compaction; capture durable memories to disk.\n"
        "You may reply, but usually NO_REPLY is correct.");
    config.user_prompt = json.value("userPrompt", 
        "Pre-compaction memory flush.\n"
        "Store durable memories now (use save_memory tool).\n"
        "IMPORTANT: Focus on:\n"
        "- User preferences and decisions\n"
        "- Key facts learned about the user\n"
        "- Important context for future conversations\n"
        "- Pending tasks or reminders\n\n"
        "If nothing important to store, reply with NO_REPLY.\n"
        "Current time: {current_datetime}");
    return config;
}

nlohmann::json MemoryFlushConfig::to_json() const {
    return {
        {"enabled", enabled},
        {"softThresholdTokens", soft_threshold_tokens},
        {"systemPrompt", system_prompt},
        {"userPrompt", user_prompt}
    };
}

// CompactionConfig
CompactionConfig CompactionConfig::from_json(const nlohmann::json& json) {
    CompactionConfig config;
    
    // Mode
    std::string mode_str = json.value("mode", "safeguard");
    config.mode = (mode_str == "safeguard") ? Mode::Safeguard : Mode::Default;
    
    // Token budget
    config.context_window_tokens = json.value("contextWindowTokens", 128000);
    config.reserve_tokens = json.value("reserveTokens", 4000);
    config.reserve_tokens_floor = json.value("reserveTokensFloor", 20000);
    config.keep_recent_tokens = json.value("keepRecentTokens", 8000);
    config.max_history_share = json.value("maxHistoryShare", 0.5);
    
    // Identifier policy
    std::string policy_str = json.value("identifierPolicy", "strict");
    if (policy_str == "strict") {
        config.identifier_policy = IdentifierPolicy::Strict;
    } else if (policy_str == "off") {
        config.identifier_policy = IdentifierPolicy::Off;
    } else {
        config.identifier_policy = IdentifierPolicy::Custom;
    }
    config.identifier_instructions = json.value("identifierInstructions", "");
    
    // Memory flush
    if (json.contains("memoryFlush")) {
        config.memory_flush = MemoryFlushConfig::from_json(json["memoryFlush"]);
    }
    
    // Compaction strategy
    config.max_chunk_tokens = json.value("maxChunkTokens", 32000);
    config.min_messages_for_split = json.value("minMessagesForSplit", 4);
    config.chunk_parts = json.value("chunkParts", 2);
    config.target_compression_ratio = json.value("targetCompressionRatio", 0.15);
    
    return config;
}

nlohmann::json CompactionConfig::to_json() const {
    std::string mode_str = (mode == Mode::Safeguard) ? "safeguard" : "default";
    std::string policy_str;
    switch (identifier_policy) {
        case IdentifierPolicy::Strict: policy_str = "strict"; break;
        case IdentifierPolicy::Off: policy_str = "off"; break;
        case IdentifierPolicy::Custom: policy_str = "custom"; break;
    }
    
    return {
        {"mode", mode_str},
        {"contextWindowTokens", context_window_tokens},
        {"reserveTokens", reserve_tokens},
        {"reserveTokensFloor", reserve_tokens_floor},
        {"keepRecentTokens", keep_recent_tokens},
        {"maxHistoryShare", max_history_share},
        {"identifierPolicy", policy_str},
        {"identifierInstructions", identifier_instructions},
        {"memoryFlush", memory_flush.to_json()},
        {"maxChunkTokens", max_chunk_tokens},
        {"minMessagesForSplit", min_messages_for_split},
        {"chunkParts", chunk_parts},
        {"targetCompressionRatio", target_compression_ratio}
    };
}

// ContextBudget
ContextBudget ContextBudget::from_config(const CompactionConfig& config) {
    ContextBudget budget;
    budget.context_window = config.context_window_tokens;
    
    // Allocate budget based on context window
    // System prompt: ~6% of context
    budget.system_prompt_max = static_cast<int>(config.context_window_tokens * 0.06);
    
    // Summary: ~16% of context
    budget.summary_max = static_cast<int>(config.context_window_tokens * 0.16);
    
    // Recent messages: ~31% of context
    budget.recent_messages_max = static_cast<int>(config.context_window_tokens * 0.31);
    
    // Tool results: ~16% of context
    budget.tool_results_max = static_cast<int>(config.context_window_tokens * 0.16);
    
    // Generation reserve: ~31% of context
    budget.generation_reserve = static_cast<int>(config.context_window_tokens * 0.31);
    
    return budget;
}

nlohmann::json ContextBudget::to_json() const {
    return {
        {"contextWindow", context_window},
        {"systemPromptMax", system_prompt_max},
        {"summaryMax", summary_max},
        {"recentMessagesMax", recent_messages_max},
        {"toolResultsMax", tool_results_max},
        {"generationReserve", generation_reserve}
    };
}

// AgentConfig
AgentConfig AgentConfig::from_json(const nlohmann::json& json) {
    AgentConfig config;
    config.model = json.value("model", "qwen-max");
    config.max_iterations = json.value("maxIterations", 15);
    config.temperature = json.value("temperature", 0.7);
    config.max_tokens = json.value("maxTokens", 4096);
    
    // Legacy memory settings
    config.memory_window = json.value("memoryWindow", 50);
    config.consolidation_threshold = json.value("consolidationThreshold", 30);
    
    // New compaction configuration
    if (json.contains("compaction")) {
        config.compaction = CompactionConfig::from_json(json["compaction"]);
    }
    
    return config;
}

nlohmann::json AgentConfig::to_json() const {
    return {
        {"model", model},
        {"maxIterations", max_iterations},
        {"temperature", temperature},
        {"maxTokens", max_tokens},
        {"memoryWindow", memory_window},
        {"consolidationThreshold", consolidation_threshold},
        {"compaction", compaction.to_json()}
    };
}

// ProviderConfig
ProviderConfig ProviderConfig::from_json(const nlohmann::json& json) {
    ProviderConfig config;
    config.api_key = json.value("apiKey", "");
    config.base_url = json.value("baseUrl", "https://api.openai.com/v1");
    config.timeout = json.value("timeout", 30);
    return config;
}

nlohmann::json ProviderConfig::to_json() const {
    return {
        {"apiKey", api_key},
        {"baseUrl", base_url},
        {"timeout", timeout}
    };
}

// ToolPermissionConfig
ToolPermissionConfig ToolPermissionConfig::from_json(const nlohmann::json& json) {
    ToolPermissionConfig config;
    if (json.contains("allow") && json["allow"].is_array()) {
        for (const auto& item : json["allow"]) {
            config.allow.push_back(item.get<std::string>());
        }
    }
    if (json.contains("deny") && json["deny"].is_array()) {
        for (const auto& item : json["deny"]) {
            config.deny.push_back(item.get<std::string>());
        }
    }
    return config;
}

nlohmann::json ToolPermissionConfig::to_json() const {
    return {
        {"allow", allow},
        {"deny", deny}
    };
}

// SkillsConfig
SkillsConfig SkillsConfig::from_json(const nlohmann::json& json) {
    SkillsConfig config;
    config.path = json.value("path", "");
    
    if (json.contains("autoApprove") && json["autoApprove"].is_array()) {
        for (const auto& item : json["autoApprove"]) {
            config.auto_approve.push_back(item.get<std::string>());
        }
    }
    
    if (json.contains("load") && json["load"].contains("extraDirs")) {
        for (const auto& item : json["load"]["extraDirs"]) {
            config.extra_dirs.push_back(item.get<std::string>());
        }
    }
    
    return config;
}

nlohmann::json SkillsConfig::to_json() const {
    return {
        {"path", path},
        {"autoApprove", auto_approve},
        {"load", {{"extraDirs", extra_dirs}}}
    };
}

// LoggingConfig
LoggingConfig LoggingConfig::from_json(const nlohmann::json& json) {
    LoggingConfig config;
    config.directory = json.value("directory", "");
    config.level = json.value("level", "info");
    config.enabled = json.value("enabled", false);
    return config;
}

nlohmann::json LoggingConfig::to_json() const {
    return {
        {"directory", directory},
        {"level", level},
        {"enabled", enabled}
    };
}

// IcrawConfig
IcrawConfig IcrawConfig::from_json(const nlohmann::json& json) {
    IcrawConfig config;
    
    // Agent config
    if (json.contains("agent")) {
        config.agent = AgentConfig::from_json(json["agent"]);
    } else if (json.contains("llm")) {
        // Legacy format
        config.agent = AgentConfig::from_json(json["llm"]);
    }
    
    // Provider config
    if (json.contains("provider")) {
        config.provider = ProviderConfig::from_json(json["provider"]);
    } else if (json.contains("providers")) {
        // Take first provider
        for (auto& [name, provider_json] : json["providers"].items()) {
            config.provider = ProviderConfig::from_json(provider_json);
            break;
        }
    }
    
    // Tools config
    if (json.contains("tools")) {
        config.tools = ToolPermissionConfig::from_json(json["tools"]);
    }
    
    // Skills config
    if (json.contains("skills")) {
        config.skills = SkillsConfig::from_json(json["skills"]);
    }
    
    // Logging config
    if (json.contains("logging")) {
        config.logging = LoggingConfig::from_json(json["logging"]);
    }
    
    // Workspace path
    config.workspace_path = json.value("workspacePath", default_workspace_path().string());
    
    return config;
}

IcrawConfig IcrawConfig::load_from_file(const std::string& filepath) {
    std::ifstream file(filepath);
    if (!file.is_open()) {
        return load_default();
    }
    
    std::stringstream ss;
    ss << file.rdbuf();
    
    try {
        auto json = nlohmann::json::parse(ss.str());
        return from_json(json);
    } catch (const nlohmann::json::parse_error&) {
        return load_default();
    }
}

IcrawConfig IcrawConfig::load_default() {
    IcrawConfig config;
    config.workspace_path = default_workspace_path();
    return config;
}

nlohmann::json IcrawConfig::to_json() const {
    return {
        {"agent", agent.to_json()},
        {"provider", provider.to_json()},
        {"tools", tools.to_json()},
        {"skills", skills.to_json()},
        {"logging", logging.to_json()},
        {"workspacePath", workspace_path.string()}
    };
}

std::string IcrawConfig::expand_home(const std::string& path) {
    if (path.empty() || path[0] != '~') {
        return path;
    }
    
    std::string home;
    
#ifdef _WIN32
    char buffer[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_PROFILE, NULL, 0, buffer))) {
        home = buffer;
    } else {
        // Fallback to environment variables
        home = std::getenv("USERPROFILE");
        if (home.empty()) {
            home = std::getenv("HOMEDRIVE");
            std::string homepath = std::getenv("HOMEPATH");
            if (!home.empty() && !homepath.empty()) {
                home += homepath;
            }
        }
    }
#else
    const char* home_env = std::getenv("HOME");
    if (home_env) {
        home = home_env;
    } else {
        struct passwd* pw = getpwuid(getuid());
        if (pw) {
            home = pw->pw_dir;
        }
    }
#endif
    
    if (path.size() == 1) {
        return home;
    }
    
    return home + path.substr(1);
}

std::filesystem::path IcrawConfig::default_workspace_path() {
    std::string home;
    
#ifdef _WIN32
    char buffer[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_PROFILE, NULL, 0, buffer))) {
        home = buffer;
    }
#else
    const char* home_env = std::getenv("HOME");
    if (home_env) {
        home = home_env;
    }
#endif
    
    return std::filesystem::path(home) / ".icraw" / "workspace";
}

} // namespace icraw
