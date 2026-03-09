# Architecture

**Analysis Date:** 2026-03-09

## Pattern Overview

**Overall:** MVP (Model-View-Presenter) + Agent Architecture

This is a hybrid Android application combining traditional MVP UI architecture with a native C++ Agent engine. The app runs entirely on-device, with a Java/Kotlin Android frontend communicating via JNI to a C++ agent core.

**Key Characteristics:**
- **MVP Pattern**: View (MainActivity) and Presenter (MainPresenter) separated by Contract interface
- **JNI Bridge**: Java adapter (NativeMobileAgentApiAdapter) wraps C++ NativeAgent for Android integration
- **Tool System**: C++ ToolRegistry manages tools, with Android tools callable via JNI callback
- **Memory Management**: SQLite-based conversation history and memory consolidation
- **LLM Integration**: OpenAI-compatible API provider with streaming support

## Layers

### View Layer (Android UI)
- **Purpose**: Display chat messages and handle user input
- **Location**: `app/src/main/java/com/hh/agent/`
- **Contains**: MainActivity, MessageAdapter, UI components
- **Depends on**: Presenter (via MainContract.View)
- **Used by**: Android framework (Activity lifecycle)

### Presenter Layer (Business Logic)
- **Purpose**: Coordinate between View and Native Agent, manage threading
- **Location**: `app/src/main/java/com/hh/agent/presenter/`
- **Contains**: MainPresenter, NativeMobileAgentApiAdapter
- **Depends on**: MobileAgentApi interface, AndroidToolManager
- **Used by**: MainActivity

### API Interface Layer
- **Purpose**: Define agent operations abstractly
- **Location**: `agent/src/main/java/com/hh/agent/library/api/`
- **Contains**: MobileAgentApi interface
- **Depends on**: Model layer
- **Used by**: Presenter, any API consumers

### Native Bridge Layer
- **Purpose**: Adapt between Java models and C++ native code
- **Location**: `agent/src/main/java/com/hh/agent/library/api/`
- **Contains**: NativeMobileAgentApi (JNI interface)
- **Depends on**: C++ native library (libicraw)
- **Used by**: NativeMobileAgentApiAdapter

### Native C++ Core Layer
- **Purpose**: Core agent logic, LLM communication, tool execution
- **Location**: `agent/src/main/cpp/src/`
- **Contains**:
  - `mobile_agent.cpp` - Main agent orchestrator
  - `core/agent_loop.cpp` - Agent loop with tool execution
  - `core/llm_provider.cpp` - LLM API client
  - `core/memory_manager.cpp` - SQLite-based storage
  - `tools/tool_registry.cpp` - Tool registration and execution
- **Depends on**: nlohmann-json, curl
- **Used by**: JNI bridge

### Android Tools Layer
- **Purpose**: Android-specific capabilities callable by the agent
- **Location**: `app/src/main/java/com/hh/agent/tools/`
- **Contains**: ShowToastTool, TakeScreenshotTool, SearchContactsTool, etc.
- **Depends on**: Android APIs
- **Used by**: AndroidToolManager, registered via callback

## Data Flow

**Chat Message Flow:**

1. User types message in MainActivity
2. MainPresenter.sendMessage() called on background thread
3. Message sent to NativeMobileAgentApiAdapter
4. JNI call to native_agent.cpp -> MobileAgent::chat()
5. AgentLoop processes message:
   - Builds prompt with system prompt + history
   - Calls LLM via LlmProvider
   - If tool_calls detected, executes via ToolRegistry
   - Returns assistant response
6. Response flows back through JNI -> Java -> Presenter -> View
7. MessageAdapter displays in RecyclerView

**Tool Execution Flow:**

1. LLM returns tool_call in response
2. AgentLoop extracts tool name and arguments
3. ToolRegistry.execute_tool() called
4. For Android tools: JNI callback to Java AndroidToolCallback
5. AndroidToolManager dispatches to appropriate tool class
6. Tool executes (e.g., take screenshot, search contacts)
7. Result JSON returned back through callback chain
8. Tool result added to messages and sent back to LLM

## Key Abstractions

**MobileAgentApi Interface:**
- Purpose: Abstract agent operations for testability
- Examples: `agent/src/main/java/com/hh/agent/library/api/MobileAgentApi.java`
- Pattern: Interface-based abstraction, enables mock implementations

**MainContract:**
- Purpose: MVP contract defining View and Presenter interfaces
- Examples: `app/src/main/java/com/hh/agent/contract/MainContract.java`
- Pattern: Contract pattern, explicit interface definition

**ToolRegistry:**
- Purpose: Central tool registration and execution
- Examples: `agent/src/main/cpp/src/tools/tool_registry.cpp`
- Pattern: Registry pattern with lambda-based tool handlers

**MemoryManager:**
- Purpose: SQLite-based conversation storage and retrieval
- Examples: `agent/src/main/cpp/src/core/memory_manager.cpp`
- Pattern: Repository pattern for message persistence

## Entry Points

**Android Entry Point:**
- Location: `app/src/main/java/com/hh/agent/MainActivity.java`
- Triggers: User launches app, Activity.onCreate()
- Responsibilities: Initialize presenter, load history, display messages

**Native Library Entry Point:**
- Location: `agent/src/main/cpp/native_agent.cpp`
- Triggers: System.loadLibrary("icraw"), JNI calls
- Responsibilities: JNI_OnLoad, initialize MobileAgent, handle all native methods

**Agent Initialization:**
- Location: `agent/src/main/cpp/src/mobile_agent.cpp` (MobileAgent constructor)
- Triggers: nativeInitialize JNI call
- Responsibilities: Create all components (MemoryManager, ToolRegistry, AgentLoop)

## Error Handling

**Strategy:** Layer-specific exception handling with clear propagation

**Patterns:**
- **Java Layer**: RuntimeException for failures, caught and displayed via Toast
- **Presenter**: ExecutorService with mainHandler for thread-safe UI updates
- **Native Layer**: C++ exceptions caught, logged, error strings returned to Java
- **JNI**: UnsatisfiedLinkError caught with descriptive message

**Examples:**
- `MainPresenter.sendMessage()` catches Exception, posts error to View
- `native_agent.cpp` wraps std::exception, returns error string
- Tool execution catches exceptions, returns JSON error response

## Cross-Cutting Concerns

**Logging:**
- **Java**: android.util.Log (logcat)
- **C++**: spdlog with Android log sink
- **Configuration**: Via IcrawConfig, defaults to "debug" level

**Validation:**
- Input validation in Presenter before sending
- JSON schema validation for tool parameters
- Session key validation in API layer

**Authentication:**
- API key stored in config.json (loaded from assets)
- Passed to LLM provider via IcrawConfig
- No user authentication (local-only app)

**Threading:**
- Presenter uses single-threaded ExecutorService
- Native code runs on caller's thread (typically background)
- UI updates via mainHandler.post() to main thread

---

*Architecture analysis: 2026-03-09*
