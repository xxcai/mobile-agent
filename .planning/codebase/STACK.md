# Technology Stack

**Analysis Date:** 2026-## Languages

**03-12

Primary:**
- Java 21 - Android app and library modules
- C++17 - Native core agent library
- Kotlin - Not currently used

**Secondary:**
- CMake - Native build configuration
- Gradle - Android build system

## Runtime

**Environment:**
- Android (minSdk 24, targetSdk 31-34)
- Native C++ library via JNI

**Package Manager:**
- Gradle 8.3.2 (Android Gradle Plugin)
- vcpkg (C++ dependencies management)
- Lockfile: Not committed (vcpkg.json specifies versions)

## Frameworks

**Core:**
- Android SDK 34 - Mobile app framework
- AndroidX libraries - Modern Android components

**Native C++ Libraries:**
- nlohmann-json - JSON parsing
- SQLite3 (with FTS5, JSON1 features) - Local storage
- curl - HTTP client
- spdlog - Logging
- Catch2 - Unit testing

**Android Dependencies:**
- androidx.appcompat:appcompat:1.6.1
- com.google.android.material:material:1.11.0
- androidx.core:core:1.12.0
- androidx.recyclerview:recyclerview:1.3.2

**Markdown Rendering:**
- io.noties.markwon:4.6.2 - Markdown rendering in Android

## Key Dependencies

**Core Agent (C++):**
- nlohmann-json - JSON serialization/deserialization
- sqlite3 - Agent memory and skill storage
- curl - LLM API communication
- spdlog - Structured logging

**Android Modules:**
- Markwon - Markdown UI rendering

## Configuration

**Environment:**
- JSON config file: `config.json.template` (or config.json at runtime)
- Configuration loaded via `IcrawConfig::load_from_file()`

**Key configs in config.json:**
```json
{
  "provider": {
    "apiKey": "API key for LLM service",
    "baseUrl": "https://api.minimaxi.com/v1"
  },
  "agent": {
    "model": "MiniMax-M2.5-highspeed"
  }
}
```

**Build:**
- `build.gradle` - Root project configuration
- `app/build.gradle` - Main app module
- `agent-core/build.gradle` - Native core library
- `agent-android/build.gradle` - Android bindings
- `floating-ball/build.gradle` - Floating window module

## Platform Requirements

**Development:**
- Android SDK 34
- NDK 26.3.11579264
- CMake 3.22.1
- Java 21

**Production:**
- Android APK deployment
- arm64-v8a native library support

---

*Stack analysis: 2026-03-12*
