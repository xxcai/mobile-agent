# Technology Stack

**Analysis Date:** 2026-03-09

## Languages

**Primary:**
- Java 21 - Android application code (app module), targetSdk 31
- C++ (NDK) - Native agent library (agent module)

**Secondary:**
- CMake 3.22.1 - C++ build configuration

## Runtime

**Environment:**
- Android SDK 34 (compileSdk), minSdk 24, targetSdk 31
- NDK 26.3.11579264
- Android Runtime

**Build System:**
- Gradle 8.12.1
- Android Gradle Plugin (AGP) 8.3.2
- CMake for native code

## Frameworks

**Core:**
- AndroidX AppCompat 1.6.1 - Backward compatibility
- AndroidX RecyclerView - Message list display
- Material Design 1.11.0 - UI components
- Markwon 4.6.2 - Markdown rendering for assistant messages

**Architecture:**
- MVP Pattern (Model-View-Presenter) - As documented in CLAUDE.md
- JNI/Native Bridge - Java to C++ communication

**Testing:**
- JUnit 4.13.2 - Unit testing
- AndroidJUnitRunner - Android instrumentation tests

## Key Dependencies

**Android App (app/build.gradle):**
- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `io.noties.markwon:core:4.6.2` - Markdown rendering
- `io.noties.markwon:ext-strikethrough:4.6.2`
- `io.noties.markwon:ext-tables:4.6.2`
- `io.noties.markwon:ext-tasklist:4.6.2`

**Native Agent (agent/conanfile.py):**
- `libcurl/8.1.2` - HTTP client for LLM API calls
- `nlohmann_json/3.11.3` - JSON serialization/deserialization
- `spdlog/1.15.1` - Logging framework (header-only)
- `sqlite3/3.45.3` - Local database for memory management
- `zlib/1.3.1` - Compression

**NDK Configuration:**
- ABI Filters: arm64-v8a
- C++ Standard: C++_shared

## Configuration

**Environment:**
- `local.properties` - Local development configuration (API key, SDK paths)
- `config.json.template` - Runtime configuration template
- `config-template.gradle` - Gradle task to copy template to assets

**Key config.json fields:**
- `provider.apiKey` - LLM API authentication key
- `provider.baseUrl` - LLM API endpoint (default: https://api.minimaxi.com/v1)
- `agent.model` - LLM model name

**Build Configuration:**
- `gradle.properties` - Gradle settings
- `settings.gradle` - Multi-module project setup (app, agent modules)

## Platform Requirements

**Development:**
- Java 21
- Android SDK 34
- NDK 26.3.11579264
- CMake 3.22.1+
- Android device/emulator with API 24+

**Production:**
- Android APK deployment
- Config.json bundled in assets
- Native libraries (.so) bundled in APK

---

*Stack analysis: 2026-03-09*
