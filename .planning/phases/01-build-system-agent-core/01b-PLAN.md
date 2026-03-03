---
wave: 3
depends_on:
  - 01a-PLAN.md
files_modified:
  - agent/src/main/cpp/android_log_sink.hpp
  - agent/src/main/cpp/native_agent.cpp
  - agent/src/main/java/com/hh/agent/library/NativeAgent.java
autonomous: true
---

# Phase 1: Build System & Agent Core - Plan 01b

**Phase:** 01-build-system-agent-core
**Wave:** 2b (Android Log Sink + JNI + Build Test)
**Dependencies:** Wave 2a (01a-PLAN.md - Gradle + CMake + Copy Sources)

## Goal (Wave 2b)

Create Android log sink, JNI bindings, NativeAgent Java class, and verify build succeeds.

## Requirements Coverage (Wave 2b)

| Requirement | Description | Status |
|-------------|-------------|--------|
| AGEN-01 | C++ Agent engine can compile in Android NDK environment | Must Have |
| AGEN-02 | Agent engine supports basic conversation loop (input -> process -> output) | Must Have |
| AGEN-03 | Agent engine can communicate with Java via JNI | Must Have |

---

### Task W2-4: Create Android log sink for spdlog

**Description:** Create custom spdlog sink that redirects to Android logcat

**Files:**
- agent/src/main/cpp/android_log_sink.hpp

**Actions:**
- Create android_log_sink.hpp with custom sink class
- Inherit from spdlog::sinks::base_sink<std::mutex>
- Implement sink_it_ to use __android_log_print
- Implement flush_ (empty)

**Verification:**
- android_log_sink.hpp exists
- Contains class that extends spdlog::sinks::base_sink
- Uses ANDROID_LOG_DEBUG level

---

### Task W2-5: Update logger.cpp for Android

**Description:** Modify logger implementation to use Android log sink

**Files:**
- agent/src/main/cpp/src/logger.cpp

**Actions:**
- Update logger initialization to use android_log_sink on Android
- Keep file sink as fallback if needed
- Include android/log.h header

**Verification:**
- logger.cpp uses android_log_sink when ICRAW_ANDROID is defined
- Log output goes to Android logcat

---

### Task W2-6: Create minimal JNI entry point

**Description:** Create JNI binding layer with basic initialization

**Files:**
- agent/src/main/cpp/native_agent.cpp

**Actions:**
- Create native_agent.cpp with JNI exports
- Implement JNI_OnLoad for library initialization
- Create nativeGetVersion function returning version string
- Create nativeInitialize function for agent initialization

**Verification:**
- native_agent.cpp contains JNIEXPORT functions
- Functions follow Java_com_hh_agent_library_NativeAgent naming convention

---

### Task W2-7: Create NativeAgent Java class

**Description:** Create Java JNI wrapper class

**Files:**
- agent/src/main/java/com/hh/agent/library/NativeAgent.java

**Actions:**
- Create agent/src/main/java/com/hh/agent/library/ directory
- Create NativeAgent.java with native method declarations
- Add static System.loadLibrary() call

**Verification:**
- NativeAgent.java exists with native methods
- Package matches com.hh.agent.library

---

### Task W2-8: Test Gradle build

**Description:** Verify the project builds successfully with C++ compilation

**Actions:**
- Run ./gradlew :agent:assembleDebug
- Verify libicraw.so is generated in build/intermediates/cmake/

**Verification:**
- Build succeeds without errors
- .so file generated for arm64-v8a

---

## Wave 2b Verification Criteria

The following must be TRUE for Wave 2b completion:

1. **Gradle build succeeds** - ./gradlew :agent:assembleDebug completes without errors
2. **C++ .so library generated** - libicraw.so exists in build output
3. **arm64-v8a ABI compiles** - Build succeeds for arm64-v8a
4. **Agent can initialize** - JNI nativeInitialize method callable from Java
5. **CMake integrated** - externalNativeBuild block in agent/build.gradle

---

## Wave 2b Must Haves

| Must Have | Verification Method |
|-----------|---------------------|
| C++ compiles in NDK | Gradle build succeeds |
| CMake used | CMakeLists.txt present in cpp directory |
| arm64-v8a ABI supported | ndk.abiFilters contains arm64-v8a |
| Basic conversation loop | Agent core code present (from cxxplatform) |
| JNI callable | NativeAgent.java with native methods exists |
| Log output to logcat | android_log_sink.hpp implemented |

---

## Implementation Notes

### Dependencies
- Use header-only for nlohmann-json and spdlog (no .so needed)
- sqlite3 and curl must be compiled as shared libraries via Conan
- Android log library (log) is required for logcat output

### ABI Support
- Only arm64-v8a ABI required
- ndk.abiFilters must include only arm64-v8a

### Build Output
- Library name: libicraw.so
- Location: agent/build/intermediates/cmake/debug/obj/{abi}/libicraw.so

---

## Critical Path

```
WAVE 1 (Conan)                    WAVE 2a (Gradle+CMake)      WAVE 2b (Log+JNI+Build)
     |                                  |                              |
     v                                  v                              v
[W1-1] -> [W1-2] -> [W1-3] -> [W1-4] |                              |
    (conanfile.py)   (install)   (verify) |                              |
                                          v                              v
                    [W2-1] -> [W2-2] -> [W2-3] -> [W2-4] -> [W2-5] -> [W2-6] -> [W2-7] -> [W2-8]
                    (gradle)   (cmake)   (copy)    (log)     (update)  (JNI)    (java)   (build)
```

**BLOCKER CONDITION:** Do not proceed to Wave 2b tasks until Wave 2a verification passes.

---

*Plan created for Phase 1: Build System & Agent Core - Wave 2b (Android Log Sink + JNI + Build Test)*
*Updated: 2026-03-03*
