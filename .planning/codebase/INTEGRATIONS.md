# External Integrations

**Analysis Date:** 2026-03-12

## APIs & External Services

**LLM Provider (Primary):**
- MiniMax API - Primary LLM service
  - Endpoint: `https://api.minimaxi.com/v1`
  - Model: `MiniMax-M2.5-highspeed`
  - Authentication: API Key in config.json
  - Implementation: `cxxplatform/src/core/llm_provider.cpp`

**OpenAI-Compatible APIs:**
- Support for any OpenAI-compatible API endpoint
- Default: `https://api.openai.com/v1`
- Configurable via `config.json` `provider.baseUrl`
- Implementation: `OpenAICompatibleProvider` class

**Streaming Support:**
- SSE (Server-Sent Events) streaming for LLM responses
- Stream parser supports: OpenAI, Qwen, and other OpenAI-compatible formats
- Implementation: `cxxplatform/include/icraw/core/llm_provider.hpp`

## Data Storage

**Local Database:**
- SQLite3 with FTS5 and JSON1 extensions
- Location: Agent workspace directory
- Used for: Agent memory, conversation history, skill storage
- Implementation: `cxxplatform/src/core/memory_manager.cpp`

**File Storage:**
- Workspace directory for skills, agents, tools definitions
- Assets bundled in Android APK

**Caching:**
- None detected

## Authentication & Identity

**API Key Authentication:**
- Bearer token authentication for LLM APIs
- API key stored in `config.json` (provider.apiKey)
- Loaded via `native_agent.cpp` at initialization

## Monitoring & Observability

**Logging:**
- spdlog for C++ core library
- Android Log (android_log_sink) for native layer
- Log levels: trace, debug, info, warn, error
- Implementation: `cxxplatform/src/logger.cpp`, `agent-core/src/main/cpp/android_log_sink.hpp`

**Error Tracking:**
- Not integrated with external services

## CI/CD & Deployment

**Build System:**
- Gradle for Android builds
- CMake for native C++ builds

**Hosting:**
- Android APK (not deployed to app stores yet)

**CI Pipeline:**
- Not detected

## Environment Configuration

**Required config (config.json):**
- `provider.apiKey` - LLM service API key
- `provider.baseUrl` - LLM API endpoint URL
- `agent.model` - Model name to use

**Runtime Configuration:**
- Configuration loaded from JSON file at app startup
- Default workspace path: app-specific external storage

## MCP (Model Context Protocol) Integration

**MCP Client:**
- Implemented in `cxxplatform/src/core/mcp_client.cpp`
- HTTP transport-based MCP communication
- Protocol version: 2025-11-25

**MCP Capabilities:**
- Tools: List and call remote tools
- Resources: List and read remote resources
- Prompts: List remote prompts
- Logging: Server-side logging support

**Configuration:**
- Server URL: Configured via `McpClientConfig`
- Auth token: Optional Bearer token
- Request timeout: 120 seconds default

## Android-Specific Integrations

**Accessibility Service:**
- Agent can interact with other apps via accessibility
- Implementation: `agent-android` module

**Floating Window:**
- Floating ball UI component
- Implementation: `floating-ball` module

**Native Tools (Android):**
- DisplayNotificationTool - Show notifications
- SearchContactsTool - Access contacts
- SendImMessageTool - Send IM messages
- ReadClipboardTool - Read clipboard
- ShowToastTool - Show toast messages
- TakeScreenshotTool - Take screenshots
- Implementation: `app/src/main/java/com/hh/agent/tool/`

---

*Integration audit: 2026-03-12*
