#pragma once

#include <string>
#include <vector>
#include <optional>
#include <memory>
#include <map>
#include <nlohmann/json.hpp>
#include "icraw/core/http_client.hpp"

namespace icraw {

// ============================================================================
// MCP Types - Based on Model Context Protocol Specification 2025-11-25
// ============================================================================

/// MCP implementation info (client or server)
struct McpImplementationInfo {
    std::string name;
    std::string title;
    std::string version;
    std::string description;
    
    nlohmann::json to_json() const {
        nlohmann::json j;
        j["name"] = name;
        if (!title.empty()) j["title"] = title;
        if (!version.empty()) j["version"] = version;
        if (!description.empty()) j["description"] = description;
        return j;
    }
    
    static McpImplementationInfo from_json(const nlohmann::json& j) {
        McpImplementationInfo info;
        info.name = j.value("name", "");
        info.title = j.value("title", "");
        info.version = j.value("version", "");
        info.description = j.value("description", "");
        return info;
    }
};

/// MCP capabilities
struct McpCapabilities {
    // Tool capabilities
    bool tools_supported = false;
    bool tools_list_changed = false;
    
    // Resource capabilities
    bool resources_supported = false;
    bool resources_subscribe = false;
    bool resources_list_changed = false;
    
    // Prompt capabilities
    bool prompts_supported = false;
    bool prompts_list_changed = false;
    
    // Logging capabilities
    bool logging_supported = false;
    
    nlohmann::json to_json() const {
        nlohmann::json j = nlohmann::json::object();
        
        if (tools_supported) {
            j["tools"] = nlohmann::json::object();
            if (tools_list_changed) {
                j["tools"]["listChanged"] = true;
            }
        }
        
        if (resources_supported) {
            j["resources"] = nlohmann::json::object();
            if (resources_subscribe) {
                j["resources"]["subscribe"] = true;
            }
            if (resources_list_changed) {
                j["resources"]["listChanged"] = true;
            }
        }
        
        if (prompts_supported) {
            j["prompts"] = nlohmann::json::object();
            if (prompts_list_changed) {
                j["prompts"]["listChanged"] = true;
            }
        }
        
        if (logging_supported) {
            j["logging"] = nlohmann::json::object();
        }
        
        return j;
    }
    
    static McpCapabilities from_json(const nlohmann::json& j) {
        McpCapabilities caps;
        
        if (j.contains("tools")) {
            caps.tools_supported = true;
            const auto& tools = j["tools"];
            caps.tools_list_changed = tools.value("listChanged", false);
        }
        
        if (j.contains("resources")) {
            caps.resources_supported = true;
            const auto& resources = j["resources"];
            caps.resources_subscribe = resources.value("subscribe", false);
            caps.resources_list_changed = resources.value("listChanged", false);
        }
        
        if (j.contains("prompts")) {
            caps.prompts_supported = true;
            const auto& prompts = j["prompts"];
            caps.prompts_list_changed = prompts.value("listChanged", false);
        }
        
        if (j.contains("logging")) {
            caps.logging_supported = true;
        }
        
        return caps;
    }
};

/// MCP tool definition
struct McpTool {
    std::string name;
    std::string title;
    std::string description;
    nlohmann::json input_schema;  // JSON Schema for input parameters
    
    nlohmann::json to_json() const {
        nlohmann::json j;
        j["name"] = name;
        if (!title.empty()) j["title"] = title;
        if (!description.empty()) j["description"] = description;
        j["inputSchema"] = input_schema;
        return j;
    }
    
    static McpTool from_json(const nlohmann::json& j) {
        McpTool tool;
        tool.name = j.value("name", "");
        tool.title = j.value("title", "");
        tool.description = j.value("description", "");
        tool.input_schema = j.value("inputSchema", nlohmann::json::object());
        return tool;
    }
};

/// MCP resource definition
struct McpResource {
    std::string uri;
    std::string name;
    std::string description;
    std::string mime_type;
    
    static McpResource from_json(const nlohmann::json& j) {
        McpResource res;
        res.uri = j.value("uri", "");
        res.name = j.value("name", "");
        res.description = j.value("description", "");
        res.mime_type = j.value("mimeType", "");
        return res;
    }
};

/// MCP resource content
struct McpResourceContent {
    std::string uri;
    std::string mime_type;
    std::string text;
    std::string blob;  // Base64 encoded binary data
    
    static McpResourceContent from_json(const nlohmann::json& j) {
        McpResourceContent content;
        content.uri = j.value("uri", "");
        content.mime_type = j.value("mimeType", "");
        content.text = j.value("text", "");
        content.blob = j.value("blob", "");
        return content;
    }
};

/// MCP prompt definition
struct McpPrompt {
    std::string name;
    std::string title;
    std::string description;
    std::vector<std::string> arguments;  // Argument names
    
    static McpPrompt from_json(const nlohmann::json& j) {
        McpPrompt prompt;
        prompt.name = j.value("name", "");
        prompt.title = j.value("title", "");
        prompt.description = j.value("description", "");
        
        if (j.contains("arguments") && j["arguments"].is_array()) {
            for (const auto& arg : j["arguments"]) {
                prompt.arguments.push_back(arg.value("name", ""));
            }
        }
        
        return prompt;
    }
};

/// MCP tool call result
struct McpToolResult {
    bool is_error = false;
    std::string content;  // Text content
    nlohmann::json data;   // Structured data if available
    
    static McpToolResult from_json(const nlohmann::json& j) {
        McpToolResult result;
        result.is_error = j.value("isError", false);
        
        if (j.contains("content") && j["content"].is_array()) {
            for (const auto& item : j["content"]) {
                std::string type = item.value("type", "");
                if (type == "text") {
                    result.content = item.value("text", "");
                } else if (type == "resource") {
                    result.data = item;
                }
            }
        }
        
        return result;
    }
};

/// MCP initialization result
struct McpInitializeResult {
    std::string protocol_version;
    McpCapabilities capabilities;
    McpImplementationInfo server_info;
    std::string instructions;
    
    static McpInitializeResult from_json(const nlohmann::json& j) {
        McpInitializeResult result;
        result.protocol_version = j.value("protocolVersion", "");
        
        if (j.contains("capabilities")) {
            result.capabilities = McpCapabilities::from_json(j["capabilities"]);
        }
        
        if (j.contains("serverInfo")) {
            result.server_info = McpImplementationInfo::from_json(j["serverInfo"]);
        }
        
        result.instructions = j.value("instructions", "");
        return result;
    }
};

/// MCP list tools result
struct McpListToolsResult {
    std::vector<McpTool> tools;
    std::string next_cursor;  // For pagination
    
    static McpListToolsResult from_json(const nlohmann::json& j) {
        McpListToolsResult result;
        
        if (j.contains("tools") && j["tools"].is_array()) {
            for (const auto& tool_json : j["tools"]) {
                result.tools.push_back(McpTool::from_json(tool_json));
            }
        }
        
        result.next_cursor = j.value("nextCursor", "");
        return result;
    }
};

/// MCP list resources result
struct McpListResourcesResult {
    std::vector<McpResource> resources;
    std::string next_cursor;
    
    static McpListResourcesResult from_json(const nlohmann::json& j) {
        McpListResourcesResult result;
        
        if (j.contains("resources") && j["resources"].is_array()) {
            for (const auto& res_json : j["resources"]) {
                result.resources.push_back(McpResource::from_json(res_json));
            }
        }
        
        result.next_cursor = j.value("nextCursor", "");
        return result;
    }
};

/// MCP read resource result
struct McpReadResourceResult {
    std::vector<McpResourceContent> contents;
    
    static McpReadResourceResult from_json(const nlohmann::json& j) {
        McpReadResourceResult result;
        
        if (j.contains("contents") && j["contents"].is_array()) {
            for (const auto& content_json : j["contents"]) {
                result.contents.push_back(McpResourceContent::from_json(content_json));
            }
        }
        
        return result;
    }
};

/// MCP list prompts result
struct McpListPromptsResult {
    std::vector<McpPrompt> prompts;
    std::string next_cursor;
    
    static McpListPromptsResult from_json(const nlohmann::json& j) {
        McpListPromptsResult result;
        
        if (j.contains("prompts") && j["prompts"].is_array()) {
            for (const auto& prompt_json : j["prompts"]) {
                result.prompts.push_back(McpPrompt::from_json(prompt_json));
            }
        }
        
        result.next_cursor = j.value("nextCursor", "");
        return result;
    }
};

/// MCP error
struct McpError {
    int code = 0;
    std::string message;
    nlohmann::json data;
    
    static McpError from_json(const nlohmann::json& j) {
        McpError error;
        error.code = j.value("code", 0);
        error.message = j.value("message", "");
        error.data = j.value("data", nlohmann::json());
        return error;
    }
};

// ============================================================================
// MCP Client Configuration
// ============================================================================

struct McpClientConfig {
    std::string server_url;           // MCP server HTTP endpoint
    std::string auth_token;           // Optional Bearer token
    std::string protocol_version = "2025-11-25";
    int request_timeout_seconds = 120;
    
    // Client info sent during initialization
    std::string client_name = "icraw-mcp-client";
    std::string client_version = "1.0.0";
};

// ============================================================================
// MCP Client - HTTP Transport Only (Non-streaming)
// ============================================================================

class McpClient {
public:
    explicit McpClient(const McpClientConfig& config);
    ~McpClient() = default;
    
    // Prevent copying
    McpClient(const McpClient&) = delete;
    McpClient& operator=(const McpClient&) = delete;
    
    // Set custom HTTP client (for testing or custom configuration)
    void set_http_client(std::unique_ptr<HttpClient> client);
    
    // === Lifecycle ===
    
    /// Initialize connection with MCP server
    /// Must be called before any other operations
    std::optional<McpInitializeResult> initialize();
    
    /// Check if client is initialized
    bool is_initialized() const { return initialized_; }
    
    /// Get server capabilities (available after initialize)
    const McpCapabilities& get_server_capabilities() const { return server_capabilities_; }
    
    /// Get server info (available after initialize)
    const McpImplementationInfo& get_server_info() const { return server_info_; }
    
    // === Tools ===
    
    /// List available tools from the server
    std::optional<McpListToolsResult> list_tools(const std::string& cursor = "");
    
    /// Call a tool on the server
    std::optional<McpToolResult> call_tool(const std::string& name, 
                                            const nlohmann::json& arguments = nlohmann::json::object());
    
    // === Resources ===
    
    /// List available resources from the server
    std::optional<McpListResourcesResult> list_resources(const std::string& cursor = "");
    
    /// Read a specific resource
    std::optional<McpReadResourceResult> read_resource(const std::string& uri);
    
    // === Prompts ===
    
    /// List available prompts from the server
    std::optional<McpListPromptsResult> list_prompts(const std::string& cursor = "");
    
    // === Error Handling ===
    
    /// Get the last error that occurred
    const McpError& get_last_error() const { return last_error_; }
    
    /// Check if last operation had an error
    bool has_error() const { return last_error_.code != 0; }
    
    /// Clear the last error
    void clear_error() { last_error_ = McpError{}; }

private:
    // Send a JSON-RPC request and get response
    bool send_request(const std::string& method, 
                      const nlohmann::json& params,
                      nlohmann::json& result);
    
    // Build JSON-RPC request
    nlohmann::json build_request(const std::string& method, 
                                  const nlohmann::json& params);
    
    // Parse JSON-RPC response
    bool parse_response(const std::string& response_body,
                        nlohmann::json& result);
    
    McpClientConfig config_;
    std::unique_ptr<HttpClient> http_client_;
    
    bool initialized_ = false;
    McpCapabilities server_capabilities_;
    McpImplementationInfo server_info_;
    McpError last_error_;
    int next_request_id_ = 1;
};

} // namespace icraw