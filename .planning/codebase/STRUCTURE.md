# Codebase Structure

**Analysis Date:** 2026-03-10

## Directory Layout

```
mobile-agent/
├── cxxplatform/           # Native C++ core library
│   ├── include/icraw/     # Public headers
│   ├── src/               # Implementation
│   ├── tests/             # Unit tests
│   ├── demo/              # Demo application
│   ├── workspace/         # Default skill workspace
│   └── test_skill_workspace/  # Test skills
├── agent-core/            # Java JNI bridge library
│   └── src/main/
│       ├── java/          # Java sources
│       └── assets/        # Core skills/assets
├── agent-android/         # Android UI and tools
│   └── src/main/
│       ├── java/          # Java sources
│       ├── res/           # Android resources
│       └── assets/        # Android assets
├── app/                  # Application module
│   ├── src/main/
│   │   ├── java/         # Application sources
│   │   ├── res/          # Resources
│   │   └── assets/       # App-specific skills
│   └── src/test/         # Unit tests
└── build.gradle           # Gradle build config
```

## Directory Purposes

**cxxplatform:**
- Purpose: Native C++ agent engine
- Contains: Core reasoning loop, LLM integration, memory, skills, tools
- Key files:
  - `include/icraw/mobile_agent.hpp` - Main facade
  - `include/icraw/core/agent_loop.hpp` - Agent loop
  - `include/icraw/core/llm_provider.hpp` - LLM abstraction
  - `include/icraw/core/memory_manager.hpp` - Memory/SQLite
  - `include/icraw/tools/tool_registry.hpp` - Tool registry
  - `src/mobile_agent.cpp` - Implementation

**agent-core:**
- Purpose: JNI bridge library (AAR)
- Contains: Java bindings for native library
- Key files:
  - `src/main/java/com/hh/agent/library/NativeAgent.java` - JNI wrapper
  - `src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` - API singleton
  - `src/main/assets/workspace/skills/` - Core skills

**agent-android:**
- Purpose: Android UI and built-in tools
- Contains: MVP UI, Android tool implementations
- Key files:
  - `src/main/java/com/hh/agent/android/AgentActivity.java` - Main UI
  - `src/main/java/com/hh/agent/android/AndroidToolManager.java` - Tool manager
  - `src/main/java/com/hh/agent/android/presenter/MainPresenter.java` - MVP Presenter

**app:**
- Purpose: Application module
- Contains: Launcher, app-specific tools
- Key files:
  - `src/main/java/com/hh/agent/LauncherActivity.java` - Entry point
  - `src/main/java/com/hh/agent/tool/SearchContactsTool.java` - Custom tool
  - `src/main/java/com/hh/agent/tool/SendImMessageTool.java` - Custom tool

## Key File Locations

**Entry Points:**
- `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/LauncherActivity.java` - App launch
- `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentActivity.java` - Agent UI
- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/src/mobile_agent.cpp` - Native agent

**Configuration:**
- `/Users/caixiao/Workspace/projects/mobile-agent/config.json.template` - Config template
- `/Users/caixiao/Workspace/projects/mobile-agent/build.gradle` - Build config
- `/Users/caixiao/Workspace/projects/mobile-agent/settings.gradle` - Project settings

**Core Logic:**
- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/src/mobile_agent.cpp` - MobileAgent impl
- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/src/core/agent_loop.cpp` - Agent loop
- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/src/core/llm_provider.cpp` - LLM provider
- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/src/core/memory_manager.cpp` - Memory

**Testing:**
- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/tests/` - C++ tests
- `/Users/caixiao/Workspace/projects/mobile-agent/app/src/test/java/` - Android tests

## Naming Conventions

**Files:**
- Java: PascalCase (e.g., `AgentActivity.java`, `AndroidToolManager.java`)
- C++ Headers: snake_case.hpp (e.g., `mobile_agent.hpp`, `agent_loop.hpp`)
- C++ Sources: snake_case.cpp (e.g., `mobile_agent.cpp`, `agent_loop.cpp`)
- Skills: SKILL.md (uppercase)

**Directories:**
- Java packages: lowercase with dots (e.g., `com/hh/agent/android/tool`)
- C++ modules: snake_case (e.g., `core/`, `tools/`)
- Assets: lowercase (e.g., `workspace/skills/`)

**Functions/Methods:**
- Java: camelCase (e.g., `initializeToolManager()`, `registerTool()`)
- C++: snake_case (e.g., `load_skills_from_directory()`)

## Where to Add New Code

**New Native C++ Feature:**
- Headers: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/include/icraw/`
- Implementation: `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/src/`

**New Android Tool:**
- Implementation: `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/tool/`
- Registration: `AndroidToolManager.java`

**New App Tool:**
- Implementation: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/tool/`
- Registration: `LauncherActivity.java`

**New Skill:**
- Core skills: `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/assets/workspace/skills/`
- App skills: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/`

**New UI Feature:**
- Activity: `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/`
- Presenter: `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/presenter/`
- Contract: `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/contract/`

## Special Directories

**cxxplatform/include/icraw:**
- Purpose: Public C++ API headers
- Generated: No
- Committed: Yes

**cxxplatform/src/core:**
- Purpose: Core agent implementations
- Generated: No
- Committed: Yes

**workspace/skills:**
- Purpose: Skill definitions (SKILL.md files)
- Generated: No
- Committed: Yes

**app/src/main/assets/workspace:**
- Purpose: App-specific skills and resources
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-03-10*
