# Codebase Structure

**Analysis Date:** 2026-03-09

## Directory Layout

```
mobile-agent/
├── app/                         # Android Application Module
│   ├── src/main/
│   │   ├── java/com/hh/agent/
│   │   │   ├── MainActivity.java        # Main UI Activity
│   │   │   ├── LauncherActivity.java   # Launch Activity
│   │   │   ├── contract/
│   │   │   │   └── MainContract.java   # MVP Contract Interface
│   │   │   ├── presenter/
│   │   │   │   ├── MainPresenter.java  # Main Presenter
│   │   │   │   └── NativeMobileAgentApiAdapter.java  # Native Bridge
│   │   │   ├── ui/
│   │   │   │   └── MessageAdapter.java  # RecyclerView Adapter
│   │   │   ├── tools/                  # Android Tools
│   │   │   │   ├── ShowToastTool.java
│   │   │   │   ├── TakeScreenshotTool.java
│   │   │   │   ├── SearchContactsTool.java
│   │   │   │   ├── ReadClipboardTool.java
│   │   │   │   ├── SendImMessageTool.java
│   │   │   │   └── DisplayNotificationTool.java
│   │   │   ├── AndroidToolManager.java # Tool Manager
│   │   │   └── WorkspaceManager.java   # Workspace Management
│   │   ├── res/                        # Android Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── agent/                        # Android Library Module (Native)
│   ├── src/main/
│   │   ├── java/com/hh/agent/library/
│   │   │   ├── api/
│   │   │   │   ├── MobileAgentApi.java      # API Interface
│   │   │   │   └── NativeMobileAgentApi.java # JNI Interface
│   │   │   ├── model/
│   │   │   │   ├── Message.java
│   │   │   │   └── Session.java
│   │   │   ├── NativeAgent.java             # Native Agent Wrapper
│   │   │   ├── AndroidToolCallback.java     # Tool Callback Interface
│   │   │   ├── ToolExecutor.java
│   │   │   └── NativeAgent.java
│   │   ├── cpp/                        # C++ Native Code
│   │   │   ├── native_agent.cpp          # JNI Entry Point
│   │   │   ├── android_tools.cpp        # Android Tool JNI
│   │   │   ├── include/                 # Header Files
│   │   │   │   ├── icraw/
│   │   │   │   │   ├── mobile_agent.hpp
│   │   │   │   │   ├── config.hpp
│   │   │   │   │   ├── android_tools.hpp
│   │   │   │   │   ├── types.hpp
│   │   │   │   │   └── core/            # Core Headers
│   │   │   │   │       ├── agent_loop.hpp
│   │   │   │   │       ├── llm_provider.hpp
│   │   │   │   │       ├── memory_manager.hpp
│   │   │   │   │       └── tool_registry.hpp
│   │   │   │   └── tools/tool_registry.hpp
│   │   │   └── src/                    # Implementation
│   │   │       ├── mobile_agent.cpp
│   │   │       ├── config.cpp
│   │   │       ├── logger.cpp
│   │   │       ├── core/
│   │   │       │   ├── agent_loop.cpp
│   │   │       │   ├── llm_provider.cpp
│   │   │       │   ├── curl_http_client.cpp
│   │   │       │   ├── memory_manager.cpp
│   │   │       │   ├── prompt_builder.cpp
│   │   │       │   ├── skill_loader.cpp
│   │   │       │   ├── token_utils.cpp
│   │   │       │   └── content_block.cpp
│   │   │       └── tools/
│   │   │           └── tool_registry.cpp
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── cxxplatform/                   # Standalone C++ Platform (demo/reference)
│   ├── include/                   # Headers (mirrors agent)
│   ├── src/                       # Implementation (mirrors agent)
│   └── tests/                     # C++ Unit Tests
├── build.gradle                   # Root Build Config
├── settings.gradle                # Module Settings
├── config-template.gradle          # Gradle Template Config
├── config.json.template           # Agent Config Template
└── local.properties               # Local Config (API keys)
```

## Directory Purposes

**app/src/main/java/com/hh/agent/**
- Purpose: Android UI layer implementation
- Contains: Activities, Presenters, Adapters, Tool implementations
- Key files: `MainActivity.java`, `MainPresenter.java`, `MessageAdapter.java`

**app/src/main/java/com/hh/agent/contract/**
- Purpose: MVP contract interfaces
- Contains: MainContract.java defining View and Presenter interfaces

**app/src/main/java/com/hh/agent/presenter/**
- Purpose: Business logic and native agent coordination
- Contains: MainPresenter, NativeMobileAgentApiAdapter

**app/src/main/java/com/hh/agent/tools/**
- Purpose: Android tool implementations callable by agent
- Contains: Tool implementations (Toast, Screenshot, Contacts, etc.)

**agent/src/main/java/com/hh/agent/library/**
- Purpose: Library module for agent core
- Contains: API interfaces, JNI bindings, model classes

**agent/src/main/cpp/**
- Purpose: Native C++ implementation
- Contains: JNI bridge, mobile agent core, tools system

**cxxplatform/**
- Purpose: Standalone C++ implementation for testing/reference
- Contains: Same code as agent/src/main/cpp but with tests

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/hh/agent/MainActivity.java`: Android app launch
- `agent/src/main/cpp/native_agent.cpp`: Native library entry (JNI_OnLoad)

**Configuration:**
- `config.json.template`: Agent configuration template
- `local.properties`: Local overrides (API keys)
- `app/build.gradle`: App module build config
- `agent/build.gradle`: Library module with NDK config

**Core Logic:**
- `agent/src/main/cpp/src/mobile_agent.cpp`: Agent orchestration
- `agent/src/main/cpp/src/core/agent_loop.cpp`: Agent loop with tool execution
- `agent/src/main/cpp/src/core/memory_manager.cpp`: SQLite storage
- `agent/src/main/cpp/src/core/llm_provider.cpp`: LLM API client

**Testing:**
- `app/src/test/java/com/hh/agent/`: Java unit tests
- `cxxplatform/tests/`: C++ unit tests

## Naming Conventions

**Java Files:**
- Pattern: `PascalCase.java`
- Example: `MainActivity.java`, `MessageAdapter.java`, `MainContract.java`

**Java Classes:**
- Pattern: `PascalCase`
- Example: `MainPresenter`, `NativeMobileAgentApiAdapter`, `AndroidToolManager`

**Java Methods:**
- Pattern: `camelCase`
- Example: `sendMessage()`, `loadMessages()`, `attachView()`

**C++ Files:**
- Pattern: `snake_case.cpp`, `snake_case.hpp`
- Example: `mobile_agent.cpp`, `agent_loop.hpp`

**C++ Classes/Namespaces:**
- Pattern: `PascalCase` for classes, `snake_case` for functions
- Example: `MobileAgent`, `AgentLoop`, `tool_registry`

**C++ Variables:**
- Pattern: `snake_case_` with trailing underscore for members
- Example: `memory_manager_`, `llm_provider_`, `agent_config_`

**Directories:**
- Pattern: `lowercase/` for most, `camelCase/` for Java packages
- Example: `src/main/cpp/src/core/`, `com/hh/agent/library/`

## Where to Add New Code

**New Android Tool:**
- Implementation: `app/src/main/java/com/hh/agent/tools/NewToolName.java`
- Register in: `AndroidToolManager.initialize()`
- Test: `app/src/test/java/com/hh/agent/tools/`

**New Native Tool:**
- Implementation: `agent/src/main/cpp/src/tools/tool_registry.cpp` (in register_builtin_tools)
- Schema: Add in tool schema registration
- Test: `cxxplatform/tests/tool_registry.test.cpp`

**New C++ Core Component:**
- Header: `agent/src/main/cpp/include/icraw/core/component_name.hpp`
- Implementation: `agent/src/main/cpp/src/core/component_name.cpp`

**New UI Feature:**
- Layout: `app/src/main/res/layout/feature_layout.xml`
- Activity/Fragment: `app/src/main/java/com/hh/agent/ui/`
- Test: `app/src/test/java/com/hh/agent/ui/`

**Configuration:**
- Agent Config: Modify `config.json.template`
- Gradle Config: Modify root `build.gradle` or module `build.gradle`

## Special Directories

**agent/src/main/cpp/include/**
- Purpose: Public C++ headers
- Generated: No
- Committed: Yes

**agent/src/main/cpp/src/core/**
- Purpose: Core agent implementation files
- Generated: No
- Committed: Yes

**cxxplatform/**
- Purpose: Standalone C++ with tests
- Generated: No
- Committed: Yes (mirrors agent/cpp)

**app/src/main/res/**
- Purpose: Android resources (layouts, drawables, values)
- Generated: No
- Committed: Yes

**gradle/**
- Purpose: Gradle wrapper files
- Generated: Yes (wrapper download)
- Committed: Yes (wrapper jar and properties)

**.gradle/**
- Purpose: Gradle cache
- Generated: Yes (build cache)
- Committed: No (in .gitignore)

---

*Structure analysis: 2026-03-09*
