# Architecture

**Analysis Date:** 2026-03-10

## Pattern Overview

**Overall:** Layered Architecture with MVP Pattern

**Key Characteristics:**
- Native C++ Core: The agent engine is implemented in C++ (cxxplatform) providing the core reasoning loop, LLM integration, memory management, and skill/tool system
- Java/JNI Bridge: Android-specific layer (agent-core) provides JNI wrapper to expose native C++ functionality to Java
- Dynamic Tool Injection: Tools can be dynamically registered at runtime through AndroidToolManager
- Skill-based Workflow: Skills (SKILL.md format) define reusable workflows with tool calls

## Layers

**Native Core Layer (cxxplatform):**
- Purpose: Core agent engine implementation in C++
- Location: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/`
- Contains:
  - Core agent loop (`src/core/agent_loop.cpp`)
  - LLM provider abstraction (`src/core/llm_provider.cpp`)
  - Memory management with SQLite (`src/core/memory_manager.cpp`)
  - Skill loader (`src/core/skill_loader.cpp`)
  - Tool registry (`src/tools/tool_registry.cpp`)
  - HTTP client for API calls (`src/core/curl_http_client.cpp`)
- Depends on: nlohmann-json, curl, sqlite3
- Used by: agent-core (via JNI)

**Native Bridge Layer (agent-core):**
- Purpose: Java JNI bindings to native C++ library
- Location: `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/library/`
- Contains:
  - `NativeAgent.java` - JNI wrapper class loading libicraw.so
  - `AndroidToolCallback.java` - Interface for Android tool callbacks
  - `ToolExecutor.java` - Base interface for tool implementations
  - `api/MobileAgentApi.java` - High-level API abstraction
  - `api/NativeMobileAgentApi.java` - Native API singleton
  - `model/Message.java`, `model/Session.java` - Data models
- Depends on: cxxplatform native library
- Used by: agent-android, app

**Android Tool Layer (agent-android):**
- Purpose: Built-in Android tools and UI foundation
- Location: `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/`
- Contains:
  - `AndroidToolManager.java` - Dynamic tool registration and routing
  - `AgentActivity.java` - Main UI entry (MVP View)
  - `presenter/MainPresenter.java` - MVP Presenter
  - `contract/MainContract.java` - MVP contract
  - `tool/*.java` - Built-in tools (ShowToastTool, DisplayNotificationTool, etc.)
- Depends on: agent-core
- Used by: app

**Application Layer (app):**
- Purpose: Application-specific tools and launcher
- Location: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/`
- Contains:
  - `LauncherActivity.java` - App entry, registers tools to AndroidToolManager
  - `tool/SearchContactsTool.java` - App-specific tool
  - `tool/SendImMessageTool.java` - App-specific tool
- Depends on: agent-android
- Provides: Custom tools that can be dynamically injected

**Assets/Skills Layer:**
- Purpose: Skill definitions loaded at runtime
- Locations:
  - `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/assets/workspace/skills/`
  - `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/`
- Contains: SKILL.md files defining workflows

## Data Flow

**User Message Flow:**

1. User types message in AgentActivity (UI Layer)
2. MainPresenter sends message via NativeMobileAgentApi
3. NativeMobileAgentApi calls NativeAgent.nativeSendMessage (JNI)
4. C++ MobileAgent.chat() receives message
5. AgentLoop.process_message() orchestrates the turn:
   - Builds prompts with SkillLoader
   - Calls LLMProvider for completion
   - Handles tool calls via ToolRegistry
   - Updates MemoryManager with conversation
6. Response flows back through JNI to Java
7. MainPresenter updates MessageAdapter
8. AgentActivity displays message in RecyclerView

**Tool Execution Flow:**

1. LLM returns tool_call in response
2. AgentLoop.handle_tool_calls() processes tool_calls
3. If Android tool: calls back through AndroidToolCallback JNI
4. AndroidToolManager.callTool() routes to registered ToolExecutor
5. ToolExecutor.execute() performs the action
6. Result returned to AgentLoop
7. Tool result added to conversation context
8. LLM receives tool result and continues

## Key Abstractions

**MobileAgent (Facade):**
- Purpose: Simplified entry point for mobile platforms
- Examples: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/include/icraw/mobile_agent.hpp`
- Pattern: Facade pattern - wraps all core components

**AgentLoop (Orchestration):**
- Purpose: Main agent reasoning loop implementation
- Examples: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/include/icraw/core/agent_loop.hpp`
- Pattern: Loop/Reactor pattern - handles message processing iterations

**SkillLoader (Dynamic Loading):**
- Purpose: Loads and parses SKILL.md files at runtime
- Examples: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/include/icraw/core/skill_loader.hpp`
- Pattern: Plugin/Extension pattern

**ToolRegistry (Tool Management):**
- Purpose: Manages available tools and executes them
- Examples: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/include/icraw/tools/tool_registry.hpp`
- Pattern: Registry pattern

**AndroidToolManager (Dynamic Tool Injection):**
- Purpose: Runtime tool registration and JSON schema generation
- Examples: `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`
- Pattern: Service Locator / Dynamic Registry

**LLMProvider (Abstraction):**
- Purpose: Abstracts LLM API calls with support for multiple providers
- Examples: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/include/icraw/core/llm_provider.hpp`
- Pattern: Strategy pattern - supports OpenAI-compatible APIs

## Entry Points

**App Launch Entry:**
- Location: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/LauncherActivity.java`
- Triggers: App launch (intent from launcher or splash)
- Responsibilities: Initialize AndroidToolManager, register tools, navigate to AgentActivity

**Agent Entry:**
- Location: `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentActivity.java`
- Triggers: User opens agent interface
- Responsibilities: Load config, initialize presenter, display messages

**Native Agent Entry:**
- Location: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/src/mobile_agent.cpp`
- Triggers: First JNI call (nativeInitialize)
- Responsibilities: Initialize all core components, load skills, prepare agent

## Error Handling

**Strategy:** Result objects and exception propagation

**Patterns:**
- C++: Exceptions for critical errors, error codes for recoverable errors
- Java: Try-catch in UI layer, error callbacks to View
- Tool execution: JSON error responses with success=false

## Cross-Cutting Concerns

**Logging:** Android Log (Java), custom logger (C++)

**Validation:** Path validation in ToolRegistry, JSON schema for tools

**Authentication:** API key passed through config, not hardcoded

---

*Architecture analysis: 2026-03-10*
