# Codebase Structure

**Analysis Date:** 2026-03-03

## Directory Layout

```
mobile-agent/
├── app/                         # Android Application module
│   └── src/main/
│       ├── java/com/hh/agent/  # Java source code
│       │   ├── contract/        # MVP contract interfaces
│       │   ├── presenter/       # Presenter implementations
│       │   ├── ui/             # UI adapters
│       │   └── MainActivity.java
│       ├── res/                 # Android resources
│       │   ├── layout/          # XML layouts
│       │   ├── drawable/       # Graphics
│       │   ├── values/         # Colors, strings, themes
│       │   └── xml/             # Network security config
│       └── assets/              # Bundled assets
├── lib/                         # Native Library module
│   └── src/main/
│       ├── java/com/hh/agent/lib/
│       │   ├── api/            # NanobotApi interface
│       │   ├── config/         # Configuration classes
│       │   ├── dto/            # Request/Response DTOs
│       │   ├── http/           # HTTP implementation
│       │   ├── impl/           # Mock implementation
│       │   └── model/          # Data models
│       └── cpp/                # C++ JNI code (if needed)
├── agent/                      # Agent module (C++/other)
├── build.gradle                # Root build config
└── settings.gradle
```

## Directory Purposes

**app/src/main/java/com/hh/agent/:**
- Purpose: Android application source code
- Contains: Activities, MVP contracts, presenters, UI adapters

**app/src/main/res/:**
- Purpose: Android UI resources
- Contains: XML layouts, drawables, colors, strings, themes

**app/src/main/assets/:**
- Purpose: Bundled assets
- Contains: Static assets

**lib/src/main/java/com/hh/agent/lib/:**
- Purpose: Reusable library for API communication
- Contains: API interface, implementations, models, DTOs, configuration


## Key File Locations

**Entry Points:**
- `app/src/main/java/com/hh/agent/MainActivity.java`: Native Android chat UI

**Configuration:**
- `lib/src/main/java/com/hh/agent/lib/config/NanobotConfig.java`: HTTP endpoint config

**Core Logic:**
- `app/src/main/java/com/hh/agent/presenter/MainPresenter.java`: Business logic
- `lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java`: HTTP API client

**Testing:**
- `app/src/test/java/com/hh/agent/`: Android unit tests
- `lib/src/test/java/com/hh/agent/lib/`: Library unit tests

## Naming Conventions

**Files:**
- Java: PascalCase (e.g., `MainActivity.java`, `NanobotApi.java`)
- TypeScript: camelCase (e.g., `nanobot.ts`, `chat.ts`)

**Directories:**
- Java packages: lowercase with dots (e.g., `com.hh.agent.lib.api`)

**Classes:**
- Java: PascalCase (e.g., `MainPresenter`, `MessageAdapter`)
- TypeScript: PascalCase (e.g., `useChatStore`)

## Where to Add New Code

**New Feature (Android):**
- UI: Add to `app/src/main/java/com/hh/agent/ui/`
- Logic: Add to `app/src/main/java/com/hh/agent/presenter/`
- Contract: Modify `app/src/main/java/com/hh/agent/contract/MainContract.java`
- Tests: `app/src/test/java/com/hh/agent/`

**New API Implementation:**
- Interface: `lib/src/main/java/com/hh/agent/lib/api/NanobotApi.java`
- Implementation: `lib/src/main/java/com/hh/agent/lib/impl/` or `lib/src/main/java/com/hh/agent/lib/http/`

**New Model/DTO:**
- Model: `lib/src/main/java/com/hh/agent/lib/model/`
- DTO: `lib/src/main/java/com/hh/agent/lib/dto/`

## Special Directories

**app/src/main/assets/:**
- Purpose: Bundled assets
- Generated: No
- Committed: Yes (bundled in APK)

**lib/.cxx/:**
- Purpose: CMake build output for native code
- Generated: Yes
- Committed: No (in .gitignore)

---

*Structure analysis: 2026-03-03*
