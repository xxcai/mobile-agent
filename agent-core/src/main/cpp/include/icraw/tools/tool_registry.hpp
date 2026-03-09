#pragma once

#include <string>
#include <vector>
#include <unordered_map>
#include <functional>
#include <memory>
#include "icraw/types.hpp"
#include "icraw/config.hpp"
#include "icraw/core/memory_manager.hpp"

namespace icraw {

class ToolRegistry {
public:
    explicit ToolRegistry();
    ~ToolRegistry() = default;
    
    // Set memory manager for memory tools
    void set_memory_manager(MemoryManager* mem_mgr) { memory_manager_ = mem_mgr; }

    // Register built-in tools (fileread, filewrite only for mobile)
    void register_builtin_tools();

    // Execute a tool by name
    std::string execute_tool(const std::string& tool_name,
                             const nlohmann::json& parameters);

    // Get tool schemas for LLM function calling
    std::vector<ToolSchema> get_tool_schemas() const;

    // Check if tool is available
    bool has_tool(const std::string& tool_name) const;

    // Set allowed base path for file operations
    void set_base_path(const std::string& path);

    // Register tools from external JSON schema (e.g., tools.json from assets)
    void register_tools_from_schema(const nlohmann::json& schema);

private:
    // Built-in tool implementations
    std::string read_file_tool(const nlohmann::json& params);
    std::string write_file_tool(const nlohmann::json& params);
    std::string list_files_tool(const nlohmann::json& params);
    std::string save_memory_tool(const nlohmann::json& params);
    std::string search_memory_tool(const nlohmann::json& params);
    std::string grep_files_tool(const nlohmann::json& params);

    // Security: validate path is within base_path
    bool is_path_allowed(const std::string& path) const;
    
    // Memory manager reference
    MemoryManager* memory_manager_ = nullptr;

    std::unordered_map<std::string, std::function<std::string(const nlohmann::json&)>> tools_;
    std::vector<ToolSchema> tool_schemas_;
    std::string base_path_;
};

} // namespace icraw
