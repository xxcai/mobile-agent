# External Integrations

**Analysis Date:** 2026-03-09

## APIs & External Services

**LLM Provider:**
- **MiniMax** - Primary LLM provider (default)
  - API Client: Custom implementation using libcurl (see `agent/src/main/cpp/src/core/curl_http_client.cpp`)
  - Endpoint: Configurable via `provider.baseUrl` (default: `https://api.minimaxi.com/v1`)
  - Auth: Bearer token in `provider.apiKey` (config.json)
  - Model: Configurable via `agent.model` (default: `MiniMax-M2.5-highspeed`)
  - Implementation: OpenAI-compatible API format (see `agent/src/main/cpp/src/core/llm_provider.cpp`)

**Supported LLM Integrations:**
- OpenAI-compatible APIs (configurable base URL)
- MiniMax (tested and working)
- Any OpenAI-compatible endpoint

## Data Storage

**Databases:**
- **SQLite 3.45.3** - Local memory/conversation storage
  - Used by: `agent/src/main/cpp/src/core/memory_manager.cpp`
  - Purpose: Persistent conversation history and context

**File Storage:**
- **App Internal Storage** - Android app private directory
  - Path: `/data/data/com.hh.agent/files/`
  - Workspace: `.icraw/workspace/` for agent files

**Configuration Files:**
- `config.json` - Runtime configuration (bundled in assets)
- Template: `config.json.template` (copied during build)

## Authentication & Identity

**LLM API Authentication:**
- API Key-based (Bearer token)
- Stored in `config.json` (provider.apiKey)
- Injected from `local.properties` during development
- Template file contains actual API key

## Monitoring & Observability

**Error Tracking:**
- None detected - No external error tracking service integrated

**Logs:**
- **spdlog** - C++ logging framework
  - Output: Android logcat via custom sink (`agent/src/main/cpp/android_log_sink.hpp`)
  - File sink: Rotating file sink for persistent logs
  - Log levels: debug, info, warn, error
- **Android Log** - Java layer logging
  - Uses `android.util.Log`

## CI/CD & Deployment

**Build System:**
- Gradle (local builds)
- No CI/CD detected (.github/workflows not present)

**Deployment:**
- Manual APK installation via `adb install`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Environment Configuration

**Required config.json fields:**
```json
{
  "provider": {
    "apiKey": "<LLM_API_KEY>",
    "baseUrl": "https://api.minimaxi.com/v1"
  },
  "agent": {
    "model": "MiniMax-M2.5-highspeed"
  }
}
```

**Local development (local.properties):**
- SDK path configuration
- API key injection (optional)

**Secrets location:**
- `config.json.template` - Contains embedded API key
- `local.properties` - Local override (git-ignored)

## Webhooks & Callbacks

**Incoming:**
- None - This is a standalone mobile app, not a server

**Outgoing:**
- LLM API calls - HTTP POST to configured baseUrl
- Streaming responses - HTTP chunked transfer for real-time LLM output

## Native Bridge (JNI)

**Purpose:** Java/Kotlin to C++ communication

**Key JNI Functions:**
- `native_init` - Initialize native agent
- `native_chat` - Send chat message
- `native_run` - Run agent with task

**Implementation:**
- `agent/src/main/cpp/native_agent.cpp` - JNI entry points
- `agent/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` - Java interface

---

*Integration audit: 2026-03-09*
