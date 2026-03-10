#include "icraw/core/mcp_client.hpp"
#include "icraw/core/logger.hpp"
#include <sstream>

namespace icraw {

// ============================================================================
// MCP Client Implementation
// ============================================================================

McpClient::McpClient(const McpClientConfig& config)
    : config_(config)
    , http_client_(std::make_unique<CurlHttpClient>())
{
    // Ensure URL doesn't end with slash
    if (!config_.server_url.empty() && config_.server_url.back() == '/') {
        config_.server_url.pop_back();
    }
}

void McpClient::set_http_client(std::unique_ptr<HttpClient> client) {
    http_client_ = std::move(client);
}

// === Lifecycle ===

std::optional<McpInitializeResult> McpClient::initialize() {
    if (initialized_) {
        ICRAW_LOG_WARN("[MCP] Already initialized");
        return std::nullopt;
    }
    
    // Build initialize request
    nlohmann::json params;
    params["protocolVersion"] = config_.protocol_version;
    
    // Client capabilities
    nlohmann::json capabilities = nlohmann::json::object();
    params["capabilities"] = capabilities;
    
    // Client info
    nlohmann::json client_info;
    client_info["name"] = config_.client_name;
    client_info["version"] = config_.client_version;
    params["clientInfo"] = client_info;
    
    nlohmann::json result;
    if (!send_request("initialize", params, result)) {
        ICRAW_LOG_ERROR("[MCP] Initialize request failed: {}", last_error_.message);
        return std::nullopt;
    }
    
    // Parse initialization result
    McpInitializeResult init_result = McpInitializeResult::from_json(result);
    
    // Store server info
    server_capabilities_ = init_result.capabilities;
    server_info_ = init_result.server_info;
    initialized_ = true;
    
    ICRAW_LOG_INFO("[MCP] Connected to server: {} v{}", 
                   server_info_.name, server_info_.version);
    ICRAW_LOG_DEBUG("[MCP] Server capabilities: tools={}, resources={}, prompts={}",
                    server_capabilities_.tools_supported,
                    server_capabilities_.resources_supported,
                    server_capabilities_.prompts_supported);
    
    return init_result;
}

// === Tools ===

std::optional<McpListToolsResult> McpClient::list_tools(const std::string& cursor) {
    if (!initialized_) {
        last_error_.code = -1;
        last_error_.message = "Client not initialized";
        return std::nullopt;
    }
    
    if (!server_capabilities_.tools_supported) {
        last_error_.code = -1;
        last_error_.message = "Server does not support tools";
        return std::nullopt;
    }
    
    nlohmann::json params = nlohmann::json::object();
    if (!cursor.empty()) {
        params["cursor"] = cursor;
    }
    
    nlohmann::json result;
    if (!send_request("tools/list", params, result)) {
        return std::nullopt;
    }
    
    return McpListToolsResult::from_json(result);
}

std::optional<McpToolResult> McpClient::call_tool(const std::string& name, 
                                                   const nlohmann::json& arguments) {
    if (!initialized_) {
        last_error_.code = -1;
        last_error_.message = "Client not initialized";
        return std::nullopt;
    }
    
    if (!server_capabilities_.tools_supported) {
        last_error_.code = -1;
        last_error_.message = "Server does not support tools";
        return std::nullopt;
    }
    
    nlohmann::json params;
    params["name"] = name;
    params["arguments"] = arguments;
    
    nlohmann::json result;
    if (!send_request("tools/call", params, result)) {
        return std::nullopt;
    }
    
    return McpToolResult::from_json(result);
}

// === Resources ===

std::optional<McpListResourcesResult> McpClient::list_resources(const std::string& cursor) {
    if (!initialized_) {
        last_error_.code = -1;
        last_error_.message = "Client not initialized";
        return std::nullopt;
    }
    
    if (!server_capabilities_.resources_supported) {
        last_error_.code = -1;
        last_error_.message = "Server does not support resources";
        return std::nullopt;
    }
    
    nlohmann::json params = nlohmann::json::object();
    if (!cursor.empty()) {
        params["cursor"] = cursor;
    }
    
    nlohmann::json result;
    if (!send_request("resources/list", params, result)) {
        return std::nullopt;
    }
    
    return McpListResourcesResult::from_json(result);
}

std::optional<McpReadResourceResult> McpClient::read_resource(const std::string& uri) {
    if (!initialized_) {
        last_error_.code = -1;
        last_error_.message = "Client not initialized";
        return std::nullopt;
    }
    
    if (!server_capabilities_.resources_supported) {
        last_error_.code = -1;
        last_error_.message = "Server does not support resources";
        return std::nullopt;
    }
    
    nlohmann::json params;
    params["uri"] = uri;
    
    nlohmann::json result;
    if (!send_request("resources/read", params, result)) {
        return std::nullopt;
    }
    
    return McpReadResourceResult::from_json(result);
}

// === Prompts ===

std::optional<McpListPromptsResult> McpClient::list_prompts(const std::string& cursor) {
    if (!initialized_) {
        last_error_.code = -1;
        last_error_.message = "Client not initialized";
        return std::nullopt;
    }
    
    if (!server_capabilities_.prompts_supported) {
        last_error_.code = -1;
        last_error_.message = "Server does not support prompts";
        return std::nullopt;
    }
    
    nlohmann::json params = nlohmann::json::object();
    if (!cursor.empty()) {
        params["cursor"] = cursor;
    }
    
    nlohmann::json result;
    if (!send_request("prompts/list", params, result)) {
        return std::nullopt;
    }
    
    return McpListPromptsResult::from_json(result);
}

// === Private Methods ===

bool McpClient::send_request(const std::string& method, 
                              const nlohmann::json& params,
                              nlohmann::json& result) {
    if (!http_client_) {
        last_error_.code = -1;
        last_error_.message = "HTTP client not initialized";
        return false;
    }
    
    // Build JSON-RPC request
    nlohmann::json request = build_request(method, params);
    std::string request_body = request.dump();
    
    // Build URL - MCP uses POST to /mcp endpoint
    std::string url = config_.server_url + "/mcp";
    
    // Build headers
    HttpHeaders headers;
    headers["Accept"] = "application/json, text/event-stream";
    
    if (!config_.auth_token.empty()) {
        headers["Authorization"] = "Bearer " + config_.auth_token;
    }
    
    ICRAW_LOG_DEBUG("[MCP] Sending request: {} (id={})", method, request["id"].get<int>());
    ICRAW_LOG_TRACE("[MCP] Request body: {}", request_body);
    
    // Send request
    std::string response_body;
    std::map<std::string, std::string> response_headers;
    HttpError http_error;
    
    bool success = http_client_->perform_request(
        url, "POST", request_body, 
        response_body, response_headers, http_error, headers
    );
    
    if (!success) {
        last_error_.code = http_error.code;
        last_error_.message = http_error.message;
        ICRAW_LOG_ERROR("[MCP] HTTP request failed: {} - {}", http_error.code, http_error.message);
        
        // Try to parse error response
        if (!response_body.empty()) {
            try {
                nlohmann::json error_response = nlohmann::json::parse(response_body);
                if (error_response.contains("error")) {
                    McpError mcp_error = McpError::from_json(error_response["error"]);
                    last_error_ = mcp_error;
                    ICRAW_LOG_ERROR("[MCP] MCP error: {} - {}", mcp_error.code, mcp_error.message);
                }
            } catch (...) {
                // Ignore JSON parse errors
            }
        }
        
        return false;
    }
    
    ICRAW_LOG_TRACE("[MCP] Response body: {}", response_body);
    
    // Parse JSON-RPC response
    return parse_response(response_body, result);
}

nlohmann::json McpClient::build_request(const std::string& method, 
                                          const nlohmann::json& params) {
    nlohmann::json request;
    request["jsonrpc"] = "2.0";
    request["id"] = next_request_id_++;
    request["method"] = method;
    request["params"] = params;
    return request;
}

bool McpClient::parse_response(const std::string& response_body,
                                nlohmann::json& result) {
    if (response_body.empty()) {
        // Empty response body is valid for notifications (202 Accepted)
        // But for requests, we expect a result
        last_error_.code = -1;
        last_error_.message = "Empty response body";
        return false;
    }
    
    try {
        nlohmann::json response = nlohmann::json::parse(response_body);
        
        // Check for JSON-RPC error
        if (response.contains("error")) {
            last_error_ = McpError::from_json(response["error"]);
            ICRAW_LOG_ERROR("[MCP] JSON-RPC error: {} - {}", 
                           last_error_.code, last_error_.message);
            return false;
        }
        
        // Get result
        if (response.contains("result")) {
            result = response["result"];
            clear_error();
            return true;
        }
        
        // No result and no error - invalid response
        last_error_.code = -1;
        last_error_.message = "Invalid JSON-RPC response: missing result";
        return false;
        
    } catch (const nlohmann::json::parse_error& e) {
        last_error_.code = -1;
        last_error_.message = std::string("JSON parse error: ") + e.what();
        ICRAW_LOG_ERROR("[MCP] {}", last_error_.message);
        return false;
    }
}

} // namespace icraw