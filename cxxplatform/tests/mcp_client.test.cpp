#include <catch2/catch_test_macros.hpp>
#include "icraw/core/mcp_client.hpp"

// Mock HTTP client for testing
class MockHttpClient : public icraw::HttpClient {
public:
    // Response to return
    std::string mock_response;
    bool mock_success = true;
    icraw::HttpError mock_error;
    
    // Track last request
    std::string last_url;
    std::string last_method;
    std::string last_body;
    icraw::HttpHeaders last_headers;
    
    bool perform_request(const std::string& url,
                         const std::string& method,
                         const std::string& request_body,
                         std::string& response_body,
                         std::map<std::string, std::string>& response_headers,
                         icraw::HttpError& error,
                         const icraw::HttpHeaders& headers) override {
        last_url = url;
        last_method = method;
        last_body = request_body;
        last_headers = headers;
        
        if (!mock_success) {
            error = mock_error;
            return false;
        }
        
        response_body = mock_response;
        return true;
    }
};

// ============================================================================
// MCP Types Tests
// ============================================================================

TEST_CASE("McpImplementationInfo serialization", "[mcp_client][types]") {
    icraw::McpImplementationInfo info;
    info.name = "test-client";
    info.title = "Test Client";
    info.version = "1.0.0";
    info.description = "A test client";
    
    nlohmann::json j = info.to_json();
    
    REQUIRE(j["name"] == "test-client");
    REQUIRE(j["title"] == "Test Client");
    REQUIRE(j["version"] == "1.0.0");
    REQUIRE(j["description"] == "A test client");
    
    // Round-trip
    auto info2 = icraw::McpImplementationInfo::from_json(j);
    REQUIRE(info2.name == info.name);
    REQUIRE(info2.title == info.title);
    REQUIRE(info2.version == info.version);
    REQUIRE(info2.description == info.description);
}

TEST_CASE("McpCapabilities serialization", "[mcp_client][types]") {
    icraw::McpCapabilities caps;
    caps.tools_supported = true;
    caps.tools_list_changed = true;
    caps.resources_supported = true;
    caps.resources_subscribe = true;
    caps.prompts_supported = true;
    caps.logging_supported = true;
    
    nlohmann::json j = caps.to_json();
    
    REQUIRE(j.contains("tools"));
    REQUIRE(j["tools"]["listChanged"] == true);
    REQUIRE(j.contains("resources"));
    REQUIRE(j["resources"]["subscribe"] == true);
    REQUIRE(j.contains("prompts"));
    REQUIRE(j.contains("logging"));
    
    // Round-trip
    auto caps2 = icraw::McpCapabilities::from_json(j);
    REQUIRE(caps2.tools_supported == true);
    REQUIRE(caps2.tools_list_changed == true);
    REQUIRE(caps2.resources_supported == true);
    REQUIRE(caps2.resources_subscribe == true);
    REQUIRE(caps2.prompts_supported == true);
    REQUIRE(caps2.logging_supported == true);
}

TEST_CASE("McpTool serialization", "[mcp_client][types]") {
    icraw::McpTool tool;
    tool.name = "get_weather";
    tool.title = "Weather Tool";
    tool.description = "Get weather info";
    tool.input_schema = R"({
        "type": "object",
        "properties": {
            "location": {"type": "string"}
        },
        "required": ["location"]
    })"_json;
    
    nlohmann::json j = tool.to_json();
    
    REQUIRE(j["name"] == "get_weather");
    REQUIRE(j["title"] == "Weather Tool");
    REQUIRE(j["description"] == "Get weather info");
    REQUIRE(j["inputSchema"]["type"] == "object");
    
    // Round-trip
    auto tool2 = icraw::McpTool::from_json(j);
    REQUIRE(tool2.name == tool.name);
    REQUIRE(tool2.title == tool.title);
    REQUIRE(tool2.description == tool.description);
}

TEST_CASE("McpToolResult parsing", "[mcp_client][types]") {
    nlohmann::json j = R"({
        "isError": false,
        "content": [
            {"type": "text", "text": "Hello, world!"}
        ]
    })"_json;
    
    auto result = icraw::McpToolResult::from_json(j);
    
    REQUIRE(result.is_error == false);
    REQUIRE(result.content == "Hello, world!");
}

TEST_CASE("McpInitializeResult parsing", "[mcp_client][types]") {
    nlohmann::json j = R"({
        "protocolVersion": "2025-11-25",
        "capabilities": {
            "tools": {"listChanged": true},
            "resources": {"subscribe": true, "listChanged": true}
        },
        "serverInfo": {
            "name": "test-server",
            "version": "1.0.0"
        },
        "instructions": "Welcome!"
    })"_json;
    
    auto result = icraw::McpInitializeResult::from_json(j);
    
    REQUIRE(result.protocol_version == "2025-11-25");
    REQUIRE(result.capabilities.tools_supported == true);
    REQUIRE(result.capabilities.tools_list_changed == true);
    REQUIRE(result.capabilities.resources_supported == true);
    REQUIRE(result.capabilities.resources_subscribe == true);
    REQUIRE(result.server_info.name == "test-server");
    REQUIRE(result.server_info.version == "1.0.0");
    REQUIRE(result.instructions == "Welcome!");
}

TEST_CASE("McpListToolsResult parsing", "[mcp_client][types]") {
    nlohmann::json j = R"({
        "tools": [
            {
                "name": "tool1",
                "description": "First tool",
                "inputSchema": {"type": "object"}
            },
            {
                "name": "tool2",
                "description": "Second tool",
                "inputSchema": {"type": "object"}
            }
        ],
        "nextCursor": "cursor123"
    })"_json;
    
    auto result = icraw::McpListToolsResult::from_json(j);
    
    REQUIRE(result.tools.size() == 2);
    REQUIRE(result.tools[0].name == "tool1");
    REQUIRE(result.tools[1].name == "tool2");
    REQUIRE(result.next_cursor == "cursor123");
}

TEST_CASE("McpError parsing", "[mcp_client][types]") {
    nlohmann::json j = R"({
        "code": -32600,
        "message": "Invalid Request",
        "data": {"field": "id"}
    })"_json;
    
    auto error = icraw::McpError::from_json(j);
    
    REQUIRE(error.code == -32600);
    REQUIRE(error.message == "Invalid Request");
    REQUIRE(error.data["field"] == "id");
}

// ============================================================================
// MCP Client Tests
// ============================================================================

TEST_CASE("McpClient configuration", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com/mcp";
    config.auth_token = "test-token";
    config.client_name = "test-client";
    
    icraw::McpClient client(config);
    
    REQUIRE_FALSE(client.is_initialized());
}

TEST_CASE("McpClient::initialize success", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    // Create mock HTTP client
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {
                "tools": {"listChanged": true},
                "resources": {"subscribe": true}
            },
            "serverInfo": {
                "name": "test-server",
                "version": "1.0.0"
            }
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    
    auto result = client.initialize();
    
    REQUIRE(result.has_value());
    REQUIRE(client.is_initialized());
    REQUIRE(result->protocol_version == "2025-11-25");
    REQUIRE(result->capabilities.tools_supported == true);
    REQUIRE(result->server_info.name == "test-server");
}

TEST_CASE("McpClient::initialize with auth token", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    config.auth_token = "secret-token";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    MockHttpClient* mock_ptr = mock_http.get();
    client.set_http_client(std::move(mock_http));
    
    client.initialize();
    
    // Check that auth header was set
    REQUIRE(mock_ptr->last_headers.count("Authorization") == 1);
    REQUIRE(mock_ptr->last_headers["Authorization"] == "Bearer secret-token");
}

TEST_CASE("McpClient::initialize handles error response", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "error": {
            "code": -32600,
            "message": "Invalid Request"
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    
    auto result = client.initialize();
    
    REQUIRE_FALSE(result.has_value());
    REQUIRE_FALSE(client.is_initialized());
    REQUIRE(client.has_error());
    REQUIRE(client.get_last_error().code == -32600);
}

TEST_CASE("McpClient::list_tools success", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    MockHttpClient* mock_ptr = mock_http.get();
    
    // First call: initialize
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {"tools": {"listChanged": true}},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    // Second call: list_tools
    mock_ptr->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
            "tools": [
                {
                    "name": "echo",
                    "description": "Echo a message",
                    "inputSchema": {"type": "object", "properties": {"message": {"type": "string"}}}
                }
            ]
        }
    })";
    
    auto result = client.list_tools();
    
    REQUIRE(result.has_value());
    REQUIRE(result->tools.size() == 1);
    REQUIRE(result->tools[0].name == "echo");
}

TEST_CASE("McpClient::call_tool success", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    MockHttpClient* mock_ptr = mock_http.get();
    
    // Initialize
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    // Call tool
    mock_ptr->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
            "isError": false,
            "content": [
                {"type": "text", "text": "Tool executed successfully"}
            ]
        }
    })";
    
    auto result = client.call_tool("echo", {{"message", "Hello"}});
    
    REQUIRE(result.has_value());
    REQUIRE(result->is_error == false);
    REQUIRE(result->content == "Tool executed successfully");
}

TEST_CASE("McpClient::list_tools fails when not initialized", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto result = client.list_tools();
    
    REQUIRE_FALSE(result.has_value());
    REQUIRE(client.has_error());
    REQUIRE(client.get_last_error().message.find("not initialized") != std::string::npos);
}

TEST_CASE("McpClient::list_tools fails when server doesn't support tools", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    auto result = client.list_tools();
    
    REQUIRE_FALSE(result.has_value());
    REQUIRE(client.get_last_error().message.find("does not support tools") != std::string::npos);
}

TEST_CASE("McpClient::list_resources success", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    MockHttpClient* mock_ptr = mock_http.get();
    
    // Initialize with resources support
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {"resources": {"subscribe": true}},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    // List resources
    mock_ptr->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
            "resources": [
                {
                    "uri": "file:///test.txt",
                    "name": "test.txt",
                    "mimeType": "text/plain"
                }
            ]
        }
    })";
    
    auto result = client.list_resources();
    
    REQUIRE(result.has_value());
    REQUIRE(result->resources.size() == 1);
    REQUIRE(result->resources[0].uri == "file:///test.txt");
}

TEST_CASE("McpClient::list_prompts success", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    MockHttpClient* mock_ptr = mock_http.get();
    
    // Initialize with prompts support
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {"prompts": {}},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    // List prompts
    mock_ptr->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
            "prompts": [
                {
                    "name": "greeting",
                    "description": "Generate a greeting",
                    "arguments": [{"name": "name"}]
                }
            ]
        }
    })";
    
    auto result = client.list_prompts();
    
    REQUIRE(result.has_value());
    REQUIRE(result->prompts.size() == 1);
    REQUIRE(result->prompts[0].name == "greeting");
}

TEST_CASE("McpClient::read_resource success", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    MockHttpClient* mock_ptr = mock_http.get();
    
    // Initialize
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {"resources": {}},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    // Read resource
    mock_ptr->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 2,
        "result": {
            "contents": [
                {
                    "uri": "file:///test.txt",
                    "mimeType": "text/plain",
                    "text": "Hello, world!"
                }
            ]
        }
    })";
    
    auto result = client.read_resource("file:///test.txt");
    
    REQUIRE(result.has_value());
    REQUIRE(result->contents.size() == 1);
    REQUIRE(result->contents[0].text == "Hello, world!");
}

TEST_CASE("McpClient handles HTTP errors", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_success = false;
    mock_http->mock_error.code = 500;
    mock_http->mock_error.message = "Internal Server Error";
    
    client.set_http_client(std::move(mock_http));
    
    auto result = client.initialize();
    
    REQUIRE_FALSE(result.has_value());
    REQUIRE(client.has_error());
    REQUIRE(client.get_last_error().code == 500);
}

TEST_CASE("McpClient JSON-RPC request format", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    MockHttpClient* mock_ptr = mock_http.get();
    client.set_http_client(std::move(mock_http));
    
    client.initialize();
    
    // Check request format
    nlohmann::json request = nlohmann::json::parse(mock_ptr->last_body);
    
    REQUIRE(request["jsonrpc"] == "2.0");
    REQUIRE(request["id"] == 1);
    REQUIRE(request["method"] == "initialize");
    REQUIRE(request["params"].contains("protocolVersion"));
    REQUIRE(request["params"].contains("capabilities"));
    REQUIRE(request["params"].contains("clientInfo"));
}

TEST_CASE("McpClient URL construction", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com/";  // Trailing slash
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    MockHttpClient* mock_ptr = mock_http.get();
    client.set_http_client(std::move(mock_http));
    
    client.initialize();
    
    // URL should not have double slashes
    REQUIRE(mock_ptr->last_url == "https://example.com/mcp");
}

TEST_CASE("McpClient::clear_error", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_success = false;
    mock_http->mock_error.code = 500;
    mock_http->mock_error.message = "Error";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    REQUIRE(client.has_error());
    
    client.clear_error();
    
    REQUIRE_FALSE(client.has_error());
    REQUIRE(client.get_last_error().code == 0);
}

TEST_CASE("McpClient::get_server_capabilities after init", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {
                "tools": {"listChanged": true},
                "resources": {"subscribe": true}
            },
            "serverInfo": {"name": "test-server", "version": "1.0.0"}
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    const auto& caps = client.get_server_capabilities();
    
    REQUIRE(caps.tools_supported == true);
    REQUIRE(caps.tools_list_changed == true);
    REQUIRE(caps.resources_supported == true);
    REQUIRE(caps.resources_subscribe == true);
    REQUIRE(caps.prompts_supported == false);
}

TEST_CASE("McpClient::get_server_info after init", "[mcp_client]") {
    icraw::McpClientConfig config;
    config.server_url = "https://example.com";
    
    icraw::McpClient client(config);
    
    auto mock_http = std::make_unique<MockHttpClient>();
    mock_http->mock_response = R"({
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "serverInfo": {
                "name": "my-server",
                "title": "My Server",
                "version": "2.0.0",
                "description": "Test description"
            }
        }
    })";
    
    client.set_http_client(std::move(mock_http));
    client.initialize();
    
    const auto& info = client.get_server_info();
    
    REQUIRE(info.name == "my-server");
    REQUIRE(info.title == "My Server");
    REQUIRE(info.version == "2.0.0");
    REQUIRE(info.description == "Test description");
}