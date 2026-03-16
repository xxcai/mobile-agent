# Architecture

**Analysis Date:** 2026-03-12

## Pattern Overview

**Overall:** MVP (Model-View-Presenter) + Clean Architecture with Native C++ Core

**Key Characteristics:**
- MVP pattern in Android UI layer (agent-android module)
- Clean separation between UI, business logic, and native core
- JNI bridge between Java/Kotlin Android layer and C++ native agent engine
- Tool execution pattern: Dynamic registration with callback-based execution
- Session-based conversation management

## Layers

### Layer 1: Android UI (app + agent-android)

**Purpose:** User interface and user interaction handling

**Location:** `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/` and `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/`

**Contains:**
- Activity classes: `LauncherActivity.java`, `AgentActivity.java`
- MVP Contracts: `MainContract.java` (View + Presenter interfaces)
- Presenters: `MainPresenter.java` - business logic
- UI Components: `MessageAdapter.java` - RecyclerView adapter
- Voice recognition: `IVoiceRecognizer.java`, `VoiceRecognizerHolder.java`

**Depends on:** agent-core module for API

**Used by:** Android system (Activities)

---

### Layer 2: Android Tool Layer (app/tools + AndroidToolManager)

**Purpose:** Tool implementation and management for Android-specific capabilities

**Location:** `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/tool/`

**Contains:**
- Tool implementations: `ShowToastTool.java`, `TakeScreenshotTool.java`, `DisplayNotificationTool.java`, `ReadClipboardTool.java`, `SearchContactsTool.java`, `SendImMessageTool.java`
- Tool manager: `AndroidToolManager.java` - registers and routes tool calls

**Depends on:** agent-core (ToolExecutor interface, AndroidToolCallback)

**Used by:** agent-android (via NativeMobileAgentApi callback)

---

### Layer 3: Java API Layer (agent-core Java)

**Purpose:** Java API surface and JNI bridge to native code

**Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/library/`

**Contains:**
- API interfaces: `MobileAgentApi.java` - main API
- API implementation: `NativeMobileAgentApi.java` - JNI bridge
- JNI native binding: `NativeAgent.java` - native method declarations
- Models: `Message.java`, `Session.java`
- Tool callback: `AndroidToolCallback.java`
- Tool executor: `ToolExecutor.java`

**Depends on:** C++ native code (via JNI)

**Used by:** agent-android module

---

### Layer 4: Native C++ Core (agent-core/cpp)

**Purpose:** Agent core logic - LLM interaction, memory management, tool execution

**Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/src/`

**Contains:**
- Core agent: `mobile_agent.cpp` - main agent implementation
- Agent loop: `core/agent_loop.cpp` - conversation loop
- LLM provider: `core/llm_provider.cpp` - OpenAI-compatible API
- HTTP client: `core/curl_http_client.cpp` - network calls
- Memory manager: `core/memory_manager.cpp` - SQLite-based history
- Skill loader: `core/skill_loader.cpp` - skill loading
- Tool registry: `tools/tool_registry.cpp` - tool registration
- Prompt builder: `core/prompt_builder.cpp` - system prompt construction
- Config: `config.cpp` - configuration loading

**Depends on:** None (self-contained)

**Used by:** Java layer via JNI

---

### Layer 5: Floating Ball Module

**Purpose:** Floating window overlay for agent interaction

**Location:** `/Users/caixiao/Workspace/projects/mobile-agent/floating-ball/src/main/java/com/hh/agent/floating/`

**Contains:**
- `FloatingBallManager.java` - floating window management
- `FloatingBallView.java` - floating ball UI
- `FloatingBallReceiver.java` - broadcast receiver
- `ContainerActivity.java` - container activity for overlays

**Depends on:** agent-android

**Used by:** Android system

---

## Data Flow

**User Message Flow:**

1. **User Input** - User types message in `AgentActivity.java`
2. **Presenter** - `MainPresenter.sendMessage()` receives message
3. **API Call** - Calls `NativeMobileAgentApi.sendMessage(content, sessionKey)`
4. **JNI Bridge** - `NativeAgent.nativeSendMessage(content)` invokes C++
5. **C++ Agent** - `MobileAgent::send_message()` processes through `AgentLoop`
6. **LLM Call** - `LlmProvider` sends request to LLM API
7. **Tool Execution** - If LLM returns tool call, `ToolRegistry` routes to `AndroidToolCallback`
8. **Android Tool** - `AndroidToolManager` executes tool, returns result
9. **Response** - Response flows back through JNI to Java, then to UI

**Tool Registration Flow:**

1. **Initialize** - `AndroidToolManager.initialize()` loads tools config
2. **Register** - App calls `registerTool(ToolExecutor)` for each tool
3. **Generate JSON** - `generateToolsJson()` creates tool schema
4. **Push to Native** - `NativeMobileAgentApi.setToolsJson()` passes to C++
5. **Tool Registry** - C++ `ToolRegistry` receives and registers tools

---

## Key Abstractions

### MobileAgentApi (Interface)
- **Purpose:** Abstract API for mobile agent operations
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/library/api/MobileAgentApi.java`
- **Pattern:** Interface + Implementation (Strategy pattern)

### NativeMobileAgentApi (Singleton)
- **Purpose:** Singleton implementation with JNI bridge
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java`
- **Pattern:** Singleton

### ToolExecutor (Interface)
- **Purpose:** Base interface for tool implementations
- **Location:** `/Usersorkspace/projects/mobile/caixiao/W-agent/agent-core/src/main/java/com/hh/agent/library/ToolExecutor.java`
- **Pattern:** Strategy pattern - each tool implements this interface

### AndroidToolManager
- **Purpose:** Tool registry and dispatcher on Android side
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`
- **Pattern:** Registry pattern

### MainContract (MVP)
- **Purpose:** MVP contract defining View and Presenter interfaces
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/contract/MainContract.java`
- **Pattern:** MVP Contract pattern

---

## Entry Points

### Android Entry Point
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/LauncherActivity.java`
- **Triggers:** App launch (cold start)
- **Responsibilities:** Initialize agent, navigate to main UI

### Main Activity Entry
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentActivity.java`
- **Triggers:** User opens chat interface
- **Responsibilities:** Display chat UI, handle user input

### Agent Initialization
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentInitializer.java`
- **Triggers:** App start or first use
- **Responsibilities:** Initialize NativeMobileAgentApi, register tools

### JNI Entry Point
- **Location:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/native_agent.cpp`
- **Triggers:** Java calls NativeAgent.native* methods
- **Responsibilities:** JNI binding, dispatch to C++ MobileAgent

---

## Error Handling

**Strategy:** RuntimeException propagation with error callbacks

**Patterns:**
- Java layer: `RuntimeException` with descriptive messages
- Native layer: C++ exceptions caught and logged, return error codes
- UI layer: `View.onError(String error)` callback pattern for display

---

## Cross-Cutting Concerns

**Logging:**
- Java: `android.util.Log` with tag-based logging
- C++: Custom `Logger` class in `icraw::Logger`

**Validation:**
- Input validation in tool implementations
- Config validation at initialization

**Authentication:**
- API key stored in `config.json` (external configuration)
- Passed to C++ layer at initialization

---

*Architecture analysis: 2026-03-12*
