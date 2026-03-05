// Disable deprecation warnings for Windows CRT functions
#ifdef _WIN32
#ifndef _CRT_SECURE_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS
#endif
#endif

#include "icraw/tools/tool_registry.hpp"
#include "icraw/core/memory_manager.hpp"
#include "icraw/core/logger.hpp"
#include "icraw/android_tools.hpp"
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <algorithm>
#include <iomanip>
#include <chrono>
#include <functional>  // for std::boyer_moore_searcher
#include <regex>       // for std::regex

#ifdef _WIN32
#include <windows.h>
#endif

namespace icraw {

ToolRegistry::ToolRegistry() {
    base_path_ = std::filesystem::current_path().string();
}

void ToolRegistry::register_builtin_tools() {
    // Register read_file tool
    {
        ToolSchema schema;
        schema.name = "read_file";
        schema.description = "Read the contents of a file from the filesystem";
        schema.parameters = nlohmann::json{
            {"type", "object"},
            {"properties", {
                {"path", {
                    {"type", "string"},
                    {"description", "The path to the file to read"}
                }}
            }},
            {"required", {"path"}}
        };
        
        tools_[schema.name] = [this](const nlohmann::json& params) {
            return this->read_file_tool(params);
        };
        tool_schemas_.push_back(std::move(schema));
    }
    
    // Register write_file tool
    {
        ToolSchema schema;
        schema.name = "write_file";
        schema.description = "Write content to a file in the filesystem";
        schema.parameters = nlohmann::json{
            {"type", "object"},
            {"properties", {
                {"path", {
                    {"type", "string"},
                    {"description", "The path to the file to write"}
                }},
                {"content", {
                    {"type", "string"},
                    {"description", "The content to write to the file"}
                }}
            }},
            {"required", {"path", "content"}}
        };
        
        tools_[schema.name] = [this](const nlohmann::json& params) {
            return this->write_file_tool(params);
        };
        tool_schemas_.push_back(std::move(schema));
    }
    
    // Register save_memory tool (for memory consolidation)
    {
        ToolSchema schema;
        schema.name = "save_memory";
        schema.description = "Save the memory consolidation result to persistent storage. "
                            "Use this to save important information from the conversation "
                            "to long-term memory for future reference.";
        schema.parameters = nlohmann::json{
            {"type", "object"},
            {"properties", {
                {"history_entry", {
                    {"type", "string"},
                    {"description", "A paragraph (2-5 sentences) summarizing key events/decisions/topics. Start with [YYYY-MM-DD HH:MM]. Include detail useful for search."}
                }},
                {"memory_update", {
                    {"type", "string"},
                    {"description", "Full updated long-term memory as markdown. Include all existing facts plus new ones. Return unchanged if nothing new."}
                }}
            }},
            {"required", {"history_entry", "memory_update"}}
        };
        
        tools_[schema.name] = [this](const nlohmann::json& params) {
            return this->save_memory_tool(params);
        };
        tool_schemas_.push_back(std::move(schema));
    }
    
    // Register search_memory tool
    {
        ToolSchema schema;
        schema.name = "search_memory";
        schema.description = "Search the conversation history for specific content. "
                            "Use this to find relevant past conversations or information.";
        schema.parameters = nlohmann::json{
            {"type", "object"},
            {"properties", {
                {"query", {
                    {"type", "string"},
                    {"description", "The search query string"}
                }},
                {"limit", {
                    {"type", "integer"},
                    {"description", "Maximum number of results to return (default: 10)"},
                    {"default", 10}
                }}
            }},
            {"required", {"query"}}
        };
        
        tools_[schema.name] = [this](const nlohmann::json& params) {
            return this->search_memory_tool(params);
        };
        tool_schemas_.push_back(std::move(schema));
    }
    
    // Register list_files tool
    {
        ToolSchema schema;
        schema.name = "list_files";
        schema.description = "List files and directories in a given path. "
                            "Returns file names, types, sizes, and modification times.";
        schema.parameters = nlohmann::json{
            {"type", "object"},
            {"properties", {
                {"path", {
                    {"type", "string"},
                    {"description", "The directory path to list (default: current directory)"}
                }},
                {"recursive", {
                    {"type", "boolean"},
                    {"description", "Whether to list files recursively (default: false)"},
                    {"default", false}
                }},
                {"pattern", {
                    {"type", "string"},
                    {"description", "Optional glob pattern to filter files (e.g., '*.cpp')"}
                }}
            }},
            {"required", nlohmann::json::array()}
        };
        
        tools_[schema.name] = [this](const nlohmann::json& params) {
            return this->list_files_tool(params);
        };
        tool_schemas_.push_back(std::move(schema));
    }
    
    // Register grep_files tool
    {
        ToolSchema schema;
        schema.name = "grep_files";
        schema.description = "Search for text patterns in files. "
                            "Supports both literal strings and regular expressions. "
                            "Returns matching lines with file paths and line numbers.";
        schema.parameters = nlohmann::json{
            {"type", "object"},
            {"properties", {
                {"pattern", {
                    {"type", "string"},
                    {"description", "The search pattern (literal string or regex)"}
                }},
                {"path", {
                    {"type", "string"},
                    {"description", "The directory or file to search (default: current directory)"}
                }},
                {"recursive", {
                    {"type", "boolean"},
                    {"description", "Whether to search recursively (default: true)"},
                    {"default", true}
                }},
                {"use_regex", {
                    {"type", "boolean"},
                    {"description", "Whether to treat pattern as regex (default: false)"},
                    {"default", false}
                }},
                {"case_sensitive", {
                    {"type", "boolean"},
                    {"description", "Whether search is case-sensitive (default: true)"},
                    {"default", true}
                }},
                {"max_results", {
                    {"type", "integer"},
                    {"description", "Maximum number of results to return (default: 100)"},
                    {"default", 100}
                }},
                {"include_patterns", {
                    {"type", "array"},
                    {"description", "File patterns to include (e.g., [\"*.cpp\", \"*.hpp\"])"},
                    {"items", {{"type", "string"}}}
                }},
                {"exclude_patterns", {
                    {"type", "array"},
                    {"description", "File/directory patterns to exclude (e.g., [\".git\", \"node_modules\"])"},
                    {"items", {{"type", "string"}}}
                }}
            }},
            {"required", {"pattern"}}
        };
        
        tools_[schema.name] = [this](const nlohmann::json& params) {
            return this->grep_files_tool(params);
        };
        tool_schemas_.push_back(std::move(schema));
    }

    // Register show_toast Android tool
    {
        ToolSchema schema;
        schema.name = "show_toast";
        schema.description = "Display a toast message on the Android device screen";
        schema.parameters = nlohmann::json{
            {"type", "object"},
            {"properties", {
                {"message", {
                    {"type", "string"},
                    {"description", "The message content to display in the toast"}
                }},
                {"duration", {
                    {"type", "integer"},
                    {"description", "Display duration in milliseconds (default: 2000)"},
                    {"default", 2000}
                }}
            }},
            {"required", {"message"}}
        };

        tools_[schema.name] = [](const nlohmann::json& params) {
            // Call Android tool via global AndroidTools instance
            std::string result = g_android_tools.call_tool("show_toast", params);
            return result;
        };
        tool_schemas_.push_back(std::move(schema));
    }
}

std::string ToolRegistry::execute_tool(const std::string& tool_name,
                                        const nlohmann::json& parameters) {
    auto it = tools_.find(tool_name);
    if (it == tools_.end()) {
        nlohmann::json error_result;
        error_result["success"] = false;
        error_result["error"] = "Unknown tool: " + tool_name;
        return error_result.dump();
    }
    
    try {
        return it->second(parameters);
    } catch (const std::exception& e) {
        nlohmann::json error_result;
        error_result["success"] = false;
        error_result["error"] = std::string("Tool execution failed: ") + e.what();
        return error_result.dump();
    }
}

std::vector<ToolSchema> ToolRegistry::get_tool_schemas() const {
    return tool_schemas_;
}

bool ToolRegistry::has_tool(const std::string& tool_name) const {
    return tools_.find(tool_name) != tools_.end();
}

void ToolRegistry::set_base_path(const std::string& path) {
    // Always convert to absolute path to avoid double concatenation bug
    // when relative paths are passed to is_path_allowed
    std::filesystem::path p(path);
    if (p.is_relative()) {
        base_path_ = std::filesystem::absolute(p).string();
    } else {
        base_path_ = path;
    }
}

void ToolRegistry::register_tools_from_schema(const nlohmann::json& schema) {
    if (!schema.contains("tools") || !schema["tools"].is_array()) {
        ICRAW_LOG_WARN("register_tools_from_schema: No 'tools' array in schema");
        return;
    }

    const auto& tools = schema["tools"];
    for (const auto& tool : tools) {
        if (!tool.contains("type") || !tool.contains("function")) {
            ICRAW_LOG_WARN("register_tools_from_schema: Skipping invalid tool entry");
            continue;
        }

        const auto& function = tool["function"];
        if (!function.is_object()) {
            continue;
        }

        ToolSchema tool_schema;
        tool_schema.name = function.value("name", "");
        tool_schema.description = function.value("description", "");

        // Extract parameters from the function's parameters object
        if (function.contains("parameters") && function["parameters"].is_object()) {
            tool_schema.parameters = function["parameters"];
        } else {
            // Default empty parameters
            tool_schema.parameters = nlohmann::json{
                {"type", "object"},
                {"properties", nlohmann::json::object()}
            };
        }

        if (tool_schema.name.empty()) {
            ICRAW_LOG_WARN("register_tools_from_schema: Skipping tool with empty name");
            continue;
        }

        // Register tool with empty function - actual execution goes through Android callback mechanism
        // The tool schema is stored so LLM can see the tool definition
        tools_[tool_schema.name] = [](const nlohmann::json& params) -> std::string {
            // This should not be called - actual tool execution goes through Android callback
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Tool execution should go through Android callback";
            return result.dump();
        };

        tool_schemas_.push_back(std::move(tool_schema));
        ICRAW_LOG_INFO("register_tools_from_schema: Registered tool '{}'", tool_schema.name);
    }
}

std::string ToolRegistry::read_file_tool(const nlohmann::json& params) {
    // Handle both JSON object and string formats
    std::string path;
    
    if (params.is_string()) {
        try {
            auto parsed = nlohmann::json::parse(params.get<std::string>());
            path = parsed.value("path", "");
        } catch (...) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to parse tool arguments";
            return result.dump();
        }
    } else if (params.is_object()) {
        path = params.value("path", "");
    } else {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Invalid tool arguments format";
        return result.dump();
    }
    
    if (path.empty()) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Path parameter is required";
        return result.dump();
    }
    
    // Security check
    if (!is_path_allowed(path)) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Access denied: path is outside allowed directory";
        return result.dump();
    }
    
#ifdef _WIN32
    // Windows: Use proper UTF-8 to wide string conversion
    auto utf8_to_wide = [](const std::string& utf8_str) -> std::wstring {
        int wide_size = MultiByteToWideChar(
            CP_UTF8, 0,
            utf8_str.c_str(), static_cast<int>(utf8_str.length()),
            NULL, 0
        );
        if (wide_size == 0) return L"";
        std::wstring wide_str(wide_size, 0);
        MultiByteToWideChar(
            CP_UTF8, 0,
            utf8_str.c_str(), static_cast<int>(utf8_str.length()),
            &wide_str[0], wide_size
        );
        return wide_str;
    };
    
    std::wstring wide_input_path = utf8_to_wide(path);
    std::wstring wide_base_path = utf8_to_wide(base_path_);
    
    std::filesystem::path filepath(wide_input_path);
    if (filepath.is_relative()) {
        filepath = std::filesystem::path(wide_base_path) / filepath;
    }
#else
    // Unix-like: direct path handling
    std::filesystem::path filepath(path);
    if (filepath.is_relative()) {
        filepath = std::filesystem::path(base_path_) / filepath;
    }
#endif
    
    std::ifstream file(filepath, std::ios::binary);
    if (!file.is_open()) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Failed to open file: " + path;
        return result.dump();
    }
    
    std::ostringstream ss;
    ss << file.rdbuf();
    
    nlohmann::json result;
    result["success"] = true;
    result["content"] = ss.str();
    result["path"] = path;
    return result.dump();
}

std::string ToolRegistry::write_file_tool(const nlohmann::json& params) {
    // Handle both JSON object and string formats
    std::string path;
    std::string content;
    
    if (params.is_string()) {
        // If params is a string, try to parse it as JSON
        try {
            auto parsed = nlohmann::json::parse(params.get<std::string>());
            path = parsed.value("path", "");
            content = parsed.value("content", "");
        } catch (...) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to parse tool arguments";
            return result.dump();
        }
    } else if (params.is_object()) {
        path = params.value("path", "");
        content = params.value("content", "");
    } else {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Invalid tool arguments format";
        return result.dump();
    }
    
    if (path.empty()) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Path parameter is required";
        return result.dump();
    }
    
    // Security check
    if (!is_path_allowed(path)) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Access denied: path is outside allowed directory";
        return result.dump();
    }
    
#ifdef _WIN32
    // Windows: Use Windows API for best UTF-8/Unicode support
    try {
        // Helper lambda to convert UTF-8 string to wide string
        auto utf8_to_wide = [](const std::string& utf8_str) -> std::wstring {
            int wide_size = MultiByteToWideChar(
                CP_UTF8, 0,
                utf8_str.c_str(), static_cast<int>(utf8_str.length()),
                NULL, 0
            );
            if (wide_size == 0) return L"";
            std::wstring wide_str(wide_size, 0);
            MultiByteToWideChar(
                CP_UTF8, 0,
                utf8_str.c_str(), static_cast<int>(utf8_str.length()),
                &wide_str[0], wide_size
            );
            return wide_str;
        };
        
        // Convert input path to wide string
        std::wstring wide_input_path = utf8_to_wide(path);
        std::wstring wide_base_path = utf8_to_wide(base_path_);
        
        // Create paths from wide strings (preserves Unicode correctly)
        std::filesystem::path filepath(wide_input_path);
        std::filesystem::path base_filepath(wide_base_path);
        
        if (filepath.is_relative()) {
            filepath = base_filepath / filepath;
        }
        
        // Get the final wide path for Windows API
        std::wstring wide_path = filepath.wstring();
        
        // Create parent directories using Windows API if needed
        if (filepath.has_parent_path()) {
            std::wstring parent_path = filepath.parent_path().wstring();
            // Use CreateDirectoryW for wide character support (ignore errors if exists)
            CreateDirectoryW(parent_path.c_str(), NULL);
        }
        
        // Use CreateFileW for wide character path support
        HANDLE hFile = CreateFileW(
            wide_path.c_str(),
            GENERIC_WRITE,
            0,  // No sharing
            NULL,
            CREATE_ALWAYS,
            FILE_ATTRIBUTE_NORMAL,
            NULL
        );
        
        if (hFile == INVALID_HANDLE_VALUE) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to create file";
            return result.dump();
        }
        
        // Write UTF-8 content as binary
        DWORD bytes_written;
        BOOL write_result = WriteFile(
            hFile,
            content.c_str(),
            static_cast<DWORD>(content.size()),
            &bytes_written,
            NULL
        );
        
        CloseHandle(hFile);
        
        if (!write_result || bytes_written != content.size()) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to write file";
            return result.dump();
        }
        
        // Use nlohmann::json to properly escape Windows paths (backslashes)
        nlohmann::json result;
        result["success"] = true;
        result["path"] = path;
        result["bytes_written"] = content.size();
        return result.dump();
    } catch (...) {
        // Catch ALL exceptions to avoid any Unicode conversion errors escaping
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Windows file operation failed";
        return result.dump();
    }
#else
    // Unix-like systems: direct UTF-8 write
    try {
        // Resolve to absolute path first (relative to base_path_)
        std::filesystem::path filepath(path);
        if (filepath.is_relative()) {
            filepath = std::filesystem::path(base_path_) / filepath;
        }
        
        // Create parent directories if needed
        if (filepath.has_parent_path()) {
            std::filesystem::create_directories(filepath.parent_path());
        }
        
        std::ofstream file(filepath, std::ios::binary);
        if (!file.is_open()) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to create file: " + path;
            return result.dump();
        }
        
        file << content;
        file.close();
        
        nlohmann::json result;
        result["success"] = true;
        result["path"] = path;
        result["bytes_written"] = content.size();
        return result.dump();
    } catch (const std::exception& e) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = std::string("Write failed: ") + e.what();
        return result.dump();
    }
#endif
}

bool ToolRegistry::is_path_allowed(const std::string& path) const {
    if (base_path_.empty()) {
        return true;  // No restriction
    }
    
    try {
#ifdef _WIN32
        // Windows: Use proper UTF-8 to wide string conversion
        auto utf8_to_wide = [](const std::string& utf8_str) -> std::wstring {
            int wide_size = MultiByteToWideChar(
                CP_UTF8, 0,
                utf8_str.c_str(), static_cast<int>(utf8_str.length()),
                NULL, 0
            );
            if (wide_size == 0) return L"";
            std::wstring wide_str(wide_size, 0);
            MultiByteToWideChar(
                CP_UTF8, 0,
                utf8_str.c_str(), static_cast<int>(utf8_str.length()),
                &wide_str[0], wide_size
            );
            return wide_str;
        };
        
        auto wide_to_utf8 = [](const std::wstring& wide_str) -> std::string {
            if (wide_str.empty()) return "";
            int utf8_size = WideCharToMultiByte(
                CP_UTF8, 0,
                wide_str.c_str(), static_cast<int>(wide_str.length()),
                NULL, 0, NULL, NULL
            );
            if (utf8_size == 0) return "";
            std::string utf8_str(utf8_size, 0);
            WideCharToMultiByte(
                CP_UTF8, 0,
                wide_str.c_str(), static_cast<int>(wide_str.length()),
                &utf8_str[0], utf8_size, NULL, NULL
            );
            return utf8_str;
        };
        
        // Resolve to absolute paths using wide strings
        std::filesystem::path input_path(utf8_to_wide(path));
        std::filesystem::path base_path_wide(utf8_to_wide(base_path_));
        std::filesystem::path abs_path;
        
        // If path is not absolute, resolve it relative to base_path
        if (input_path.is_absolute()) {
            abs_path = input_path;
        } else {
            // Relative path - resolve relative to base_path
            abs_path = base_path_wide / input_path;
        }
        
        // Normalize both paths - use canonical for base (must exist)
        // and absolute for input path (may not exist yet)
        std::filesystem::path abs_base = std::filesystem::canonical(base_path_wide);
        abs_path = std::filesystem::absolute(abs_path);
        
        // Get normalized strings as UTF-8
        std::string path_str = wide_to_utf8(abs_path.wstring());
        std::string base_str = wide_to_utf8(abs_base.wstring());
#else
        // Unix-like: direct path handling
        std::filesystem::path input_path(path);
        std::filesystem::path abs_path;

        // On Android, paths starting with "/" should be treated as relative to workspace
        // This is because the AI may pass "/skills/xxx" meaning "skills/xxx" relative to workspace
        std::string path_to_use = path;
        bool starts_with_slash = !path.empty() && path[0] == '/';

        if (starts_with_slash) {
            // Remove leading slash - treat as relative to workspace
            // "/skills/xxx" -> "skills/xxx"
            path_to_use = path.substr(1);
        }

        if (input_path.is_absolute() && !starts_with_slash) {
            abs_path = input_path;
        } else {
            abs_path = std::filesystem::path(base_path_) / path_to_use;
        }
        
        std::filesystem::path abs_base = std::filesystem::canonical(base_path_);
        abs_path = std::filesystem::absolute(abs_path);
        
        std::string path_str = abs_path.string();
        std::string base_str = abs_base.string();
#endif
        
        // Debug logging
        ICRAW_LOG_DEBUG("is_path_allowed: input='{}', base_path_='{}'", path, base_path_);
        ICRAW_LOG_DEBUG("is_path_allowed: resolved path='{}', base='{}'", path_str, base_str);
        
        // Ensure base_str ends with separator for prefix matching
        if (base_str.back() != std::filesystem::path::preferred_separator) {
            base_str += std::filesystem::path::preferred_separator;
        }
        
        // Convert to lowercase for case-insensitive comparison on Windows
#ifdef _WIN32
        std::transform(path_str.begin(), path_str.end(), path_str.begin(), ::tolower);
        std::transform(base_str.begin(), base_str.end(), base_str.begin(), ::tolower);
#endif
        
        ICRAW_LOG_DEBUG("is_path_allowed: normalized path='{}', base='{}'", path_str, base_str);
        
        // Check if path starts with base directory
        // path_str must be either:
        // 1. Exactly equal to base_str without trailing separator (the base directory itself)
        // 2. Start with base_str (a file/subdirectory within base)
        
        // Remove trailing separator from base_str for comparison
        std::string base_without_sep = base_str;
        if (!base_without_sep.empty() && base_without_sep.back() == std::filesystem::path::preferred_separator) {
            base_without_sep.pop_back();
        }
        
        bool is_base_dir = (path_str == base_without_sep);
        bool is_within_base = (path_str.length() > base_str.length()) && 
                             (path_str.compare(0, base_str.length(), base_str) == 0);
        bool allowed = is_base_dir || is_within_base;
        
        ICRAW_LOG_DEBUG("is_path_allowed: path_str='{}', base_without_sep='{}', base_str='{}'", 
                        path_str, base_without_sep, base_str);
        ICRAW_LOG_DEBUG("is_path_allowed: is_base_dir={}, is_within_base={}, result={}", 
                        is_base_dir, is_within_base, allowed);
        return allowed;
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("is_path_allowed exception: {}", e.what());
        return false;
    } catch (...) {
        ICRAW_LOG_ERROR("is_path_allowed unknown exception");
        return false;
    }
}

std::string ToolRegistry::save_memory_tool(const nlohmann::json& params) {
    // Handle both JSON object and string formats
    std::string history_entry;
    std::string memory_update;
    
    if (params.is_string()) {
        try {
            auto parsed = nlohmann::json::parse(params.get<std::string>());
            history_entry = parsed.value("history_entry", "");
            memory_update = parsed.value("memory_update", "");
        } catch (...) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to parse tool arguments";
            return result.dump();
        }
    } else if (params.is_object()) {
        history_entry = params.value("history_entry", "");
        memory_update = params.value("memory_update", "");
    } else {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Invalid tool arguments format";
        return result.dump();
    }
    
    if (history_entry.empty() && memory_update.empty()) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Either history_entry or memory_update is required";
        return result.dump();
    }
    
    // Save to memory manager if available
    if (memory_manager_) {
        try {
            // Save summary to database
            if (!memory_update.empty()) {
                memory_manager_->create_summary("default", memory_update, 0);
            }
            
            nlohmann::json result;
            result["success"] = true;
            result["message"] = "Memory saved successfully";
            return result.dump();
        } catch (const std::exception& e) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = std::string("Failed to save memory: ") + e.what();
            return result.dump();
        }
    }
    
    nlohmann::json result;
    result["success"] = false;
    result["error"] = "Memory manager not available";
    return result.dump();
}

std::string ToolRegistry::search_memory_tool(const nlohmann::json& params) {
    std::string query;
    int limit = 10;
    
    if (params.is_string()) {
        try {
            auto parsed = nlohmann::json::parse(params.get<std::string>());
            query = parsed.value("query", "");
            limit = parsed.value("limit", 10);
        } catch (...) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to parse tool arguments";
            return result.dump();
        }
    } else if (params.is_object()) {
        query = params.value("query", "");
        limit = params.value("limit", 10);
    } else {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Invalid tool arguments format";
        return result.dump();
    }
    
    if (query.empty()) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Query is required";
        return result.dump();
    }
    
    // Search using memory manager if available
    if (memory_manager_) {
        try {
            auto results = memory_manager_->search_memory(query, limit);
            
            nlohmann::json result;
            result["success"] = true;
            result["count"] = results.size();
            
            nlohmann::json entries = nlohmann::json::array();
            for (const auto& entry : results) {
                entries.push_back({
                    {"role", entry.role},
                    {"content", entry.content},
                    {"timestamp", entry.timestamp}
                });
            }
            result["results"] = entries;
            
            return result.dump();
        } catch (const std::exception& e) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = std::string("Failed to search memory: ") + e.what();
            return result.dump();
        }
    }
    
    nlohmann::json result;
    result["success"] = false;
    result["error"] = "Memory manager not available";
    return result.dump();
}

std::string ToolRegistry::list_files_tool(const nlohmann::json& params) {
    // Parse parameters
    std::string path;
    bool recursive = false;
    std::string pattern;
    
    if (params.is_string()) {
        try {
            auto parsed = nlohmann::json::parse(params.get<std::string>());
            path = parsed.value("path", ".");
            recursive = parsed.value("recursive", false);
            pattern = parsed.value("pattern", "");
        } catch (...) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to parse tool arguments";
            return result.dump();
        }
    } else if (params.is_object()) {
        path = params.value("path", ".");
        recursive = params.value("recursive", false);
        pattern = params.value("pattern", "");
    } else {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Invalid tool arguments format";
        return result.dump();
    }
    
    // Default to current directory or base_path
    if (path.empty() || path == ".") {
        path = base_path_.empty() ? std::filesystem::current_path().string() : base_path_;
    }
    
    // Security check
    if (!is_path_allowed(path)) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Access denied: path is outside allowed directory";
        return result.dump();
    }
    
#ifdef _WIN32
    // Windows: Use proper UTF-8 to wide string conversion
    auto utf8_to_wide = [](const std::string& utf8_str) -> std::wstring {
        int wide_size = MultiByteToWideChar(
            CP_UTF8, 0,
            utf8_str.c_str(), static_cast<int>(utf8_str.length()),
            NULL, 0
        );
        if (wide_size == 0) return L"";
        std::wstring wide_str(wide_size, 0);
        MultiByteToWideChar(
            CP_UTF8, 0,
            utf8_str.c_str(), static_cast<int>(utf8_str.length()),
            &wide_str[0], wide_size
        );
        return wide_str;
    };
    
    auto wide_to_utf8 = [](const std::wstring& wide_str) -> std::string {
        if (wide_str.empty()) return "";
        int utf8_size = WideCharToMultiByte(
            CP_UTF8, 0,
            wide_str.c_str(), static_cast<int>(wide_str.length()),
            NULL, 0, NULL, NULL
        );
        if (utf8_size == 0) return "";
        std::string utf8_str(utf8_size, 0);
        WideCharToMultiByte(
            CP_UTF8, 0,
            wide_str.c_str(), static_cast<int>(wide_str.length()),
            &utf8_str[0], utf8_size, NULL, NULL
        );
        return utf8_str;
    };
    
    // Resolve relative paths to absolute paths
    std::filesystem::path input_path(utf8_to_wide(path));
    std::filesystem::path resolved_path;
    if (input_path.is_absolute()) {
        resolved_path = input_path;
    } else {
        resolved_path = std::filesystem::path(utf8_to_wide(base_path_)) / input_path;
    }
    resolved_path = std::filesystem::absolute(resolved_path);
    std::string resolved_path_str = wide_to_utf8(resolved_path.wstring());
    std::filesystem::path dir_path(resolved_path.wstring());
#else
    // Unix-like: Resolve relative paths to absolute paths
    std::filesystem::path resolved_path;
    if (std::filesystem::path(path).is_absolute()) {
        resolved_path = path;
    } else {
        resolved_path = std::filesystem::path(base_path_) / path;
    }
    resolved_path = std::filesystem::absolute(resolved_path);
    std::string resolved_path_str = resolved_path.string();
    std::filesystem::path dir_path(resolved_path);
#endif
    
    // Check if path exists and is a directory
    std::error_code ec;
    if (!std::filesystem::exists(dir_path, ec)) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Path does not exist: " + path;
        return result.dump();
    }
    
    if (!std::filesystem::is_directory(dir_path, ec)) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Path is not a directory: " + path;
        return result.dump();
    }
    
    try {
        nlohmann::json result;
        result["success"] = true;
        result["path"] = path;
        result["recursive"] = recursive;
        
        nlohmann::json entries = nlohmann::json::array();
        int file_count = 0;
        int dir_count = 0;
        
        // Simple glob pattern matching
        auto matches_pattern = [&pattern](const std::string& filename) -> bool {
            if (pattern.empty()) return true;
            
            // Simple wildcard matching for patterns like "*.cpp" or "test*"
            size_t wildcard_pos = pattern.find('*');
            if (wildcard_pos == std::string::npos) {
                return filename == pattern;
            }
            
            std::string prefix = pattern.substr(0, wildcard_pos);
            std::string suffix = pattern.substr(wildcard_pos + 1);
            
            if (!prefix.empty() && filename.substr(0, prefix.length()) != prefix) {
                return false;
            }
            if (!suffix.empty() && filename.length() >= suffix.length() &&
                filename.substr(filename.length() - suffix.length()) != suffix) {
                return false;
            }
            return true;
        };
        
#ifdef _WIN32
        // Helper to convert wide string to UTF-8
        auto wide_to_utf8 = [](const std::wstring& wide_str) -> std::string {
            if (wide_str.empty()) return "";
            int utf8_size = WideCharToMultiByte(
                CP_UTF8, 0,
                wide_str.c_str(), static_cast<int>(wide_str.length()),
                NULL, 0, NULL, NULL
            );
            if (utf8_size == 0) return "";
            std::string utf8_str(utf8_size, 0);
            WideCharToMultiByte(
                CP_UTF8, 0,
                wide_str.c_str(), static_cast<int>(wide_str.length()),
                &utf8_str[0], utf8_size, NULL, NULL
            );
            return utf8_str;
        };
#endif
        
        if (recursive) {
            for (const auto& entry : std::filesystem::recursive_directory_iterator(dir_path, ec)) {
                if (ec) continue;
                
#ifdef _WIN32
                std::string filename = wide_to_utf8(entry.path().filename().wstring());
#else
                std::string filename = entry.path().filename().string();
#endif
                
                // Apply pattern filter (only to files, not directories)
                if (!entry.is_directory() && !matches_pattern(filename)) {
                    continue;
                }
                
                nlohmann::json item;
                item["name"] = filename;
#ifdef _WIN32
                item["path"] = wide_to_utf8(entry.path().wstring());
#else
                item["path"] = entry.path().string();
#endif
                
                // Get relative path
#ifdef _WIN32
                std::string relative = wide_to_utf8(std::filesystem::relative(entry.path(), dir_path, ec).wstring());
#else
                std::string relative = std::filesystem::relative(entry.path(), dir_path, ec).string();
#endif
                item["relative_path"] = relative;
                
                if (entry.is_directory()) {
                    item["type"] = "directory";
                    dir_count++;
                } else {
                    item["type"] = "file";
                    file_count++;
                    
                    // File size
                    auto file_size = entry.file_size(ec);
                    if (!ec) {
                        item["size"] = file_size;
                    }
                }
                
                // Modification time
                auto ftime = entry.last_write_time(ec);
                if (!ec) {
                    auto sctp = std::chrono::time_point_cast<std::chrono::seconds>(
                        ftime - std::filesystem::file_time_type::clock::now() + 
                        std::chrono::system_clock::now());
                    auto time_t_val = std::chrono::system_clock::to_time_t(sctp);
                    std::stringstream ss;
#ifdef _WIN32
                    std::tm tm_buf;
                    localtime_s(&tm_buf, &time_t_val);
                    ss << std::put_time(&tm_buf, "%Y-%m-%d %H:%M:%S");
#else
                    ss << std::put_time(std::localtime(&time_t_val), "%Y-%m-%d %H:%M:%S");
#endif
                    item["modified"] = ss.str();
                }
                
                entries.push_back(item);
            }
        } else {
            // Non-recursive listing
            for (const auto& entry : std::filesystem::directory_iterator(dir_path, ec)) {
                if (ec) continue;
                
#ifdef _WIN32
                std::string filename = wide_to_utf8(entry.path().filename().wstring());
#else
                std::string filename = entry.path().filename().string();
#endif
                
                // Apply pattern filter (only to files, not directories)
                if (!entry.is_directory() && !matches_pattern(filename)) {
                    continue;
                }
                
                nlohmann::json item;
                item["name"] = filename;
#ifdef _WIN32
                item["path"] = wide_to_utf8(entry.path().wstring());
#else
                item["path"] = entry.path().string();
#endif
                
                if (entry.is_directory()) {
                    item["type"] = "directory";
                    dir_count++;
                } else {
                    item["type"] = "file";
                    file_count++;
                    
                    // File size
                    auto file_size = entry.file_size(ec);
                    if (!ec) {
                        item["size"] = file_size;
                    }
                }
                
                // Modification time
                auto ftime = entry.last_write_time(ec);
                if (!ec) {
                    auto sctp = std::chrono::time_point_cast<std::chrono::seconds>(
                        ftime - std::filesystem::file_time_type::clock::now() + 
                        std::chrono::system_clock::now());
                    auto time_t_val = std::chrono::system_clock::to_time_t(sctp);
                    std::stringstream ss;
#ifdef _WIN32
                    std::tm tm_buf;
                    localtime_s(&tm_buf, &time_t_val);
                    ss << std::put_time(&tm_buf, "%Y-%m-%d %H:%M:%S");
#else
                    ss << std::put_time(std::localtime(&time_t_val), "%Y-%m-%d %H:%M:%S");
#endif
                    item["modified"] = ss.str();
                }
                
                entries.push_back(item);
            }
        }
        
        result["entries"] = entries;
        result["file_count"] = file_count;
        result["directory_count"] = dir_count;
        result["total_count"] = file_count + dir_count;
        
        return result.dump();
        
    } catch (const std::exception& e) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = std::string("Failed to list directory: ") + e.what();
        return result.dump();
    }
}

std::string ToolRegistry::grep_files_tool(const nlohmann::json& params) {
    // Parse parameters
    std::string pattern;
    std::string path;
    bool recursive = true;
    bool use_regex = false;
    bool case_sensitive = true;
    int max_results = 100;
    std::vector<std::string> include_patterns;
    std::vector<std::string> exclude_patterns;
    
    // Handle both JSON object and string formats
    if (params.is_string()) {
        try {
            auto parsed = nlohmann::json::parse(params.get<std::string>());
            pattern = parsed.value("pattern", "");
            path = parsed.value("path", "");
            recursive = parsed.value("recursive", true);
            use_regex = parsed.value("use_regex", false);
            case_sensitive = parsed.value("case_sensitive", true);
            max_results = parsed.value("max_results", 100);
            if (parsed.contains("include_patterns") && parsed["include_patterns"].is_array()) {
                for (const auto& p : parsed["include_patterns"]) {
                    include_patterns.push_back(p.get<std::string>());
                }
            }
            if (parsed.contains("exclude_patterns") && parsed["exclude_patterns"].is_array()) {
                for (const auto& p : parsed["exclude_patterns"]) {
                    exclude_patterns.push_back(p.get<std::string>());
                }
            }
        } catch (...) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = "Failed to parse tool arguments";
            return result.dump();
        }
    } else if (params.is_object()) {
        pattern = params.value("pattern", "");
        path = params.value("path", "");
        recursive = params.value("recursive", true);
        use_regex = params.value("use_regex", false);
        case_sensitive = params.value("case_sensitive", true);
        max_results = params.value("max_results", 100);
        if (params.contains("include_patterns") && params["include_patterns"].is_array()) {
            for (const auto& p : params["include_patterns"]) {
                include_patterns.push_back(p.get<std::string>());
            }
        }
        if (params.contains("exclude_patterns") && params["exclude_patterns"].is_array()) {
            for (const auto& p : params["exclude_patterns"]) {
                exclude_patterns.push_back(p.get<std::string>());
            }
        }
    } else {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Invalid tool arguments format";
        return result.dump();
    }
    
    // Validate pattern
    if (pattern.empty()) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Pattern parameter is required";
        return result.dump();
    }
    
    // Default to base_path or current directory
    if (path.empty() || path == ".") {
        path = base_path_.empty() ? std::filesystem::current_path().string() : base_path_;
    }
    
    // Security check
    if (!is_path_allowed(path)) {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Access denied: path is outside allowed directory";
        return result.dump();
    }
    
    // Helper function for glob pattern matching
    auto matches_glob = [](const std::string& filename, const std::string& glob_pattern) -> bool {
        if (glob_pattern.empty()) return true;
        
        // Simple wildcard matching for patterns like "*.cpp" or "test*"
        size_t wildcard_pos = glob_pattern.find('*');
        if (wildcard_pos == std::string::npos) {
            return filename == glob_pattern;
        }
        
        std::string prefix = glob_pattern.substr(0, wildcard_pos);
        std::string suffix = glob_pattern.substr(wildcard_pos + 1);
        
        if (!prefix.empty() && filename.length() < prefix.length()) return false;
        if (!suffix.empty() && filename.length() < suffix.length()) return false;
        
        if (!prefix.empty() && filename.substr(0, prefix.length()) != prefix) return false;
        if (!suffix.empty() && filename.substr(filename.length() - suffix.length()) != suffix) return false;
        
        return true;
    };
    
    // Check if a path should be excluded (checks both filename and directory names in path)
    auto should_exclude = [&](const std::filesystem::path& p) -> bool {
        // Check filename
        std::string filename = p.filename().string();
        for (const auto& excl : exclude_patterns) {
            if (matches_glob(filename, excl)) return true;
        }
        
        // Check all parent directory names in the path
        for (const auto& component : p) {
            std::string part = component.string();
            for (const auto& excl : exclude_patterns) {
                // Exact match for directory names (no glob for directories)
                if (part == excl) return true;
            }
        }
        
        return false;
    };
    
    // Check if a file should be included
    auto should_include = [&](const std::filesystem::path& p) -> bool {
        if (include_patterns.empty()) return true;
        std::string filename = p.filename().string();
        for (const auto& incl : include_patterns) {
            if (matches_glob(filename, incl)) return true;
        }
        return false;
    };
    
    // Prepare search pattern
    std::regex regex_pattern;
    std::string search_pattern = pattern;
    
    if (use_regex) {
        try {
            auto flags = std::regex::ECMAScript;
            if (!case_sensitive) {
                flags |= std::regex::icase;
            }
            regex_pattern = std::regex(pattern, flags);
        } catch (const std::regex_error& e) {
            nlohmann::json result;
            result["success"] = false;
            result["error"] = std::string("Invalid regex pattern: ") + e.what();
            return result.dump();
        }
    } else if (!case_sensitive) {
        // For case-insensitive literal search, convert both pattern and content to lower
        std::transform(search_pattern.begin(), search_pattern.end(), search_pattern.begin(), ::tolower);
    }
    
    // Result collection
    nlohmann::json matches = nlohmann::json::array();
    int total_matches = 0;
    int files_searched = 0;
    int files_with_matches = 0;
    
    // Search function for a single file
    auto search_in_file = [&](const std::filesystem::path& file_path) -> void {
        if (total_matches >= max_results) return;
        
        files_searched++;
        
        std::ifstream file(file_path, std::ios::binary);
        if (!file.is_open()) return;
        
        std::string line;
        size_t line_number = 0;
        bool file_has_match = false;
        
        while (std::getline(file, line) && total_matches < max_results) {
            line_number++;
            
            bool found = false;
            size_t column = 0;
            
            if (use_regex) {
                // Regex search
                std::smatch match;
                if (std::regex_search(line, match, regex_pattern)) {
                    found = true;
                    column = match.position();
                }
            } else {
                // Literal string search using Boyer-Moore
                std::string search_line = line;
                if (!case_sensitive) {
                    std::transform(search_line.begin(), search_line.end(), search_line.begin(), ::tolower);
                }
                
                auto searcher = std::boyer_moore_searcher(search_pattern.begin(), search_pattern.end());
                auto [it_begin, it_end] = searcher(search_line.begin(), search_line.end());
                
                if (it_begin != search_line.end()) {
                    found = true;
                    column = std::distance(search_line.begin(), it_begin);
                }
            }
            
            if (found) {
                file_has_match = true;
                total_matches++;
                
                nlohmann::json match;
                match["file"] = file_path.string();
                match["line_number"] = line_number;
                match["column"] = column;
                match["line"] = line;
                matches.push_back(match);
            }
        }
        
        if (file_has_match) files_with_matches++;
    };
    
    // Collect files to search
    std::vector<std::filesystem::path> files_to_search;
    std::error_code ec;
    
    if (std::filesystem::is_regular_file(path, ec)) {
        // Single file search
        if (should_include(path)) {
            files_to_search.push_back(path);
        }
    } else if (std::filesystem::is_directory(path, ec)) {
        // Directory search
        if (recursive) {
            for (const auto& entry : std::filesystem::recursive_directory_iterator(path, ec)) {
                if (ec) continue;
                if (!entry.is_regular_file()) continue;
                if (should_exclude(entry.path())) continue;
                if (!should_include(entry.path())) continue;
                files_to_search.push_back(entry.path());
            }
        } else {
            for (const auto& entry : std::filesystem::directory_iterator(path, ec)) {
                if (ec) continue;
                if (!entry.is_regular_file()) continue;
                if (should_exclude(entry.path())) continue;
                if (!should_include(entry.path())) continue;
                files_to_search.push_back(entry.path());
            }
        }
    } else {
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "Path does not exist: " + path;
        return result.dump();
    }
    
    // Search all collected files
    for (const auto& file : files_to_search) {
        if (total_matches >= max_results) break;
        search_in_file(file);
    }
    
    // Build result
    nlohmann::json result;
    result["success"] = true;
    result["pattern"] = pattern;
    result["path"] = path;
    result["use_regex"] = use_regex;
    result["case_sensitive"] = case_sensitive;
    result["matches"] = matches;
    result["total_matches"] = total_matches;
    result["files_searched"] = files_searched;
    result["files_with_matches"] = files_with_matches;
    result["truncated"] = (total_matches >= max_results);
    
    return result.dump();
}

} // namespace icraw
