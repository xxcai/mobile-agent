# External Integrations

**Analysis Date:** 2026-03-10

## APIs & External Services

**AI/LLM Provider:**
- MiniMax API - Primary AI model provider
  - SDK: Direct HTTP via libcurl (C++ native layer)
  - Endpoint: `https://api.minimaxi.com/v1`
  - Model: `MiniMax-M2.5-highspeed`
  - Auth: API key via `config.json` (provider.apiKey)

## Data Storage

**Local Database:**
- SQLite3 - Embedded database (native layer)
  - Used for memory management and session storage
  - Library: `SQLite::SQLite3` (Conan package)
  - Location: App-specific internal storage

**File Storage:**
- Android internal storage - Config files, assets
- MediaStore API - Screenshots (TakeScreenshotTool)

## Authentication & Identity

**API Authentication:**
- API Key based (provider.apiKey in config.json)
- Bearer token in HTTP Authorization header

## Monitoring & Observability

**Logging:**
- Android Log API (android.util.Log)
- Native logging: spdlog with Android log sink
- Log tag: Configurable per component

**Error Handling:**
- Java: try-catch with Toast/Notification user feedback
- C++: Exception handling with native crash logging

## Build & Dependencies

**Dependency Management:**
- Maven Central - Android/Java dependencies
- Conan - Native C++ dependencies
- Gradle - Build orchestration

## Configuration

**Required config.json fields:**
```json
{
  "provider": {
    "apiKey": "<MiniMax API key>",
    "baseUrl": "https://api.minimaxi.com/v1"
  },
  "agent": {
    "model": "MiniMax-M2.5-highspeed"
  }
}
```

**Config location:**
- Template: `/config.json.template`
- Runtime: App assets `config.json`
- Copied at build time via `config-template.gradle`

## Webhooks & Callbacks

**Tool Callbacks:**
- AndroidToolCallback interface - Native to Java callback
- ToolExecutor - Executes tools and returns results
- Implemented tools:
  - DisplayNotificationTool - System notifications
  - TakeScreenshotTool - Screen capture
  - ReadClipboardTool - Clipboard access
  - ShowToastTool - Toast messages
  - SearchContactsTool - Contact search (app module)
  - SendImMessageTool - IM messaging (app module)

**Android Integration:**
- AgentActivity - Main UI activity
- NativeMobileAgentApi - Bridge between Java and native
- AndroidToolManager - Tool registration and execution

---

*Integration audit: 2026-03-10*
