# Technology Stack

**Analysis Date:** 2026-03-10

## Languages

**Primary:**
- Java 21 - Android application layer (app, agent-android modules)
- C++ 17 - Native agent core (agent-core module with NDK)

**Secondary:**
- Groovy - Gradle build scripts

## Runtime

**Environment:**
- Android (minSdk 24, targetSdk 31, compileSdk 34)
- Android NDK 26.3.11579264

**Build System:**
- Gradle 8.12.1
- Android Gradle Plugin 8.3.2
- CMake 3.22.1 (for native C++ builds)

**Package Management:**
- Gradle with Maven Central repositories
- Conan for native C++ dependencies

## Frameworks

**Core:**
- AndroidX - Android compatibility library (core:1.12.0, appcompat:1.6.1)
- Material Design - UI components (material:1.11.0)
- RecyclerView - List displays (recyclerview:1.3.2)
- Markwon - Markdown rendering (4.6.2)

**Native Agent:**
- nlohmann_json - JSON parsing
- SQLite3 - Local database
- libcurl - HTTP client
- OpenSSL - TLS/SSL
- spdlog - Logging
- fmt - String formatting

**Testing:**
- JUnit 4.13.2 - Unit testing
- AndroidJUnitRunner - Android instrumentation tests

## Key Dependencies

**Android UI:**
- `androidx.appcompat:appcompat:1.6.1` - Backward-compatible ActionBar
- `com.google.android.material:material:1.11.0` - Material Design components
- `androidx.recyclerview:recyclerview:1.3.2` - Efficient list views

**Markdown Rendering:**
- `io.noties.markwon:core:4.6.2` - Markdown parser/renderer
- `io.noties.markwon:ext-strikethrough:4.6.2` - Strikethrough extension
- `io.noties.markwon:ext-tables:4.6.2` - Tables extension
- `io.noties.markwon:ext-tasklist:4.6.2` - Task list extension

**Native Dependencies (via Conan):**
- `nlohmann_json` - JSON library
- `SQLite3` - SQLite database
- `CURL` - HTTP library
- `OpenSSL` - Cryptography
- `spdlog` - Logging library
- `fmt` - Formatting library

## Configuration

**Environment:**
- Configuration via `config.json` file (template at `config.json.template`)
- Runtime config loaded from assets
- Build-time copying via `config-template.gradle`

**Build:**
- Root `build.gradle` - Plugin definitions
- `gradle.properties` - JVM args, AndroidX settings
- `settings.gradle` - Module definitions

## Platform Requirements

**Development:**
- Android SDK 34 (compileSdk)
- NDK 26.3.11579264
- JDK 21
- CMake 3.22.1+

**Production:**
- Android 7.0+ (API 24 minimum)
- Target: Android 12 (API 31)

---

*Stack analysis: 2026-03-10*
