# Phase 1: Build System & Agent Core - Research

**Research Date:** 2026-03-03
**Phase:** 01-build-system-agent-core

---

## Executive Summary

This phase focuses on setting up the C++ build environment for Android NDK and ensuring the cxxplatform code compiles into a usable `.so` library. The key requirements are AGEN-01, AGEN-02, AGEN-03, SYS-01, SYS-02, and SYS-03.

**USER-EXPLICIT PRIORITY (2026-03-03):**
1. **First Priority**: Complete Conan dependency installation and compilation, verify build passes
2. **Second Priority**: Perform cxxplatform porting, verify compilation after porting

This priority order must be followed for all implementation activities.

---

## 1. Requirements Analysis

### Phase 1 Requirements

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| AGEN-01 | C++ Agent engine can compile in Android NDK environment | Must Have |
| AGEN-02 | Agent engine supports basic conversation loop (input -> process -> output) | Must Have |
| AGEN-03 | Agent engine can communicate with Java via JNI | Must Have |
| SYS-01 | Gradle build scripts include C++ compilation tasks | Must Have |
| SYS-02 | C++ code uses CMake build | Must Have |
| SYS-03 | Support armeabi-v7a, arm64-v8a, x86, x86_64 architectures | Must Have |

### Dependencies Between Requirements

```
Priority 1: CONAN SETUP (DO FIRST)
    |
    v
SYS-01, SYS-02 (Build System)
    |
    v
Priority 2: PORTING (DO SECOND)
    |
    v
AGEN-01 (Compile in NDK)
    |
    v
AGEN-02 (Basic conversation loop)
    |
    v
AGEN-03 (JNI communication - Phase 2)
```

**Implementation Order Enforcement:**
1. Complete all Conan dependency setup (Phase 1a) before touching cxxplatform code
2. Verify Conan install/build works before porting
3. Only then proceed to cxxplatform porting (Phase 1b)

**Note:** AGEN-03 (JNI) is listed in Phase 1 context but the requirements trace shows it in Phase 2. For Phase 1, focus on building the C++ library that can be called via JNI.

---

## 2. Technical Analysis

### 2.1 Project Structure

#### Existing Modules

| Module | Purpose | Status |
|--------|---------|--------|
| `app/` | Android application with UI | Active |
| `lib/` | Native library with basic JNI | Active |
| `agent/` | Target module for C++ Agent (empty) | Needs configuration |
| `cxxplatform/` | Source code to port (read-only) | Source |

#### Agent Module Status

The `agent/build.gradle` exists but is a basic Android library configuration without NDK/CMake support:

```gradle
// Current agent/build.gradle - needs enhancement
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.hh.agent.library'
    compileSdk 34
    defaultConfig {
        minSdk 24
        // Missing: ndk {} configuration
    }
    // Missing: externalNativeBuild {}
}
```

### 2.2 C++ Code Analysis

#### Source Files to Port

**Headers** (`cxxplatform/include/icraw/`):
- `mobile_agent.hpp` - Main facade class
- `config.hpp` - Configuration structures
- `types.hpp` - Type definitions
- `core/logger.hpp` - Logging interface
- `core/content_block.hpp`, `memory_manager.hpp`, `skill_loader.hpp`, etc.

**Sources** (`cxxplatform/src/`):
- `config.cpp`, `logger.cpp`, `mobile_agent.cpp`
- `core/*.cpp` - Core implementation files

#### Dependencies

From `cxxplatform/CMakeLists.txt`:

```cmake
find_package(nlohmann_json CONFIG REQUIRED)
find_package(ZLIB REQUIRED)
find_package(unofficial-sqlite3 CONFIG REQUIRED)
find_package(CURL CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)
```

#### Platform-Specific Code

The CMakeLists.txt already has Android conditional:

```cmake
if(ANDROID)
    target_compile_definitions(icraw PRIVATE ICRAW_ANDROID)
endif()
```

**Key platform-specific considerations:**
1. **Logger** (`logger.cpp`): Currently uses `spdlog::sinks::rotating_file_sink_mt` - needs Android-specific sink using `__android_log_print`
2. **Config** (`config.hpp`): Uses `std::filesystem::path` - should work on Android with NDK
3. **No other obvious platform-dependent code** in the headers

### 2.3 Dependency Management

#### Reference: android-cxx-thirdpartylib

**conanfile.py** (`/Users/caixiao/Workspace/projects/android-cxx-thirdpartylib/lib/conanfile.py`):

```python
requires = [
    "minizip/1.3.1",
    "zlib/1.3.1",
    "bzip2/1.0.8",
    "openssl/3.6.1",
    "libcurl/8.1.2",
    "nlohmann_json/3.11.3",
    "spdlog/1.15.1", "fmt/11.1.3"
]

def configure(self):
    self.options["spdlog"].header_only = True
    self.options["fmt"].header_only = True
    self.options["libcurl"].shared = True
```

**android.profile** (`/Users/caixiao/Workspace/projects/android-cxx-thirdpartylib/lib/android.profile`):

```ini
[settings]
os=Android
os.api_level=26
arch=armv8
compiler=clang
compiler.version=17
compiler.libcxx=c++_shared
compiler.cppstd=17
build_type=Release

[conf]
tools.android:ndk_path=/Users/caixiao/Library/Android/sdk/ndk/26.3.11579264
```

#### Required Dependencies for Phase 1

Based on cxxplatform requirements:

| Dependency | Type | Version | Conan Package |
|------------|------|---------|----------------|
| nlohmann-json | Header-only | 3.11.3 | nlohmann_json/3.11.3 |
| spdlog | Header-only | 1.15.1 | spdlog/1.15.1 |
| sqlite3 | Compiled | - | unofficial-sqlite3 |
| libcurl | Compiled | 8.1.2 | libcurl/8.1.2 |

**Note:** Decision says use arm64-v8a only, but SYS-03 requires all four ABIs. Will need to clarify or implement all four.

---

## 3. Implementation Approach

**IMPORTANT:** Per user instruction, implementation MUST follow this two-phase approach:

1. **Phase 1a (CONAN FIRST)**: Complete all Conan dependency setup, installation, and verification
2. **Phase 1b (PORTING SECOND)**: After Conan works, perform cxxplatform porting

### 3.1 Phase 1a: Conan Dependency Setup (FIRST PRIORITY)

This section covers all tasks required to get Conan dependencies working for Android NDK.

#### Step 1a.1: Create conanfile.py for agent module

```python
from conan import ConanFile
from conan.tools.cmake import cmake_layout

class AgentConan(ConanFile):
    settings = "os", "compiler", "build_type", "arch"
    generators = ["CMakeDeps", "CMakeToolchain"]
    options = {"shared": [True, False]}
    default_options = {"shared": True}
    requires = [
        "libcurl/8.1.2",
        "nlohmann_json/3.11.3",
        "spdlog/1.15.1",
    ]

    def configure(self):
        self.options["spdlog"].header_only = True

    def layout(self):
        cmake_layout(self)
```

#### Step 1a.2: Create android.profile for agent

```ini
[settings]
os=Android
os.api_level=26
arch=armv8
compiler=clang
compiler.version=17
compiler.libcxx=c++_shared
compiler.cppstd=17
build_type=Release

[conf]
tools.android:ndk_path=/Users/caixiao/Library/Android/sdk/ndk/26.3.11579264
```

#### Step 1a.3: Run Conan Install

```bash
cd agent
conan install . --profile=android.profile --build=missing
```

**Verification Criterion:** Conan successfully installs all dependencies for arm64-v8a target.

#### Step 1a.4: Prefab Integration Options

Option A: Use custom prefab plugin from android-cxx-thirdpartylib
- Plugin location: `/Users/caixiao/Workspace/projects/android-cxx-thirdpartylib/prefab-plugin/`
- Plugin ID: `com.thirdlib.prefab`

Option B: Use CMake toolchain directly (reference: android-cxx-thirdpartylib lib/build.gradle)
```gradle
arguments("-DCMAKE_TOOLCHAIN_FILE=${rootProject.projectDir}/lib/conan_android_toolchain.cmake")
```

### 3.2 Phase 1b: cxxplatform Porting (SECOND PRIORITY - AFTER CONAN WORKS)

Only after Conan dependencies are verified working, proceed to port cxxplatform code.

#### Step 1b.1: Copy cxxplatform source files

Copy from:
- `cxxplatform/include/*` -> `agent/src/main/cpp/include/`
- `cxxplatform/src/*` -> `agent/src/main/cpp/src/`

**Key principle:** Do not modify original cxxplatform directory - copy for porting

#### Step 1b.2: Create CMakeLists.txt for agent

```cmake
cmake_minimum_required(VERSION 3.22.1)

project(icraw VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find dependencies (via Conan)
find_package(nlohmann_json CONFIG REQUIRED)
find_package(ZLIB REQUIRED)
find_package(unofficial-sqlite3 CONFIG REQUIRED)
find_package(CURL CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)

# Source files (copied from cxxplatform)
set(ICRAW_SOURCES
    # ... list all source files
)

# Create shared library
add_library(icraw SHARED ${ICRAW_SOURCES})

target_include_directories(icraw PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(icraw PUBLIC
    nlohmann_json::nlohmann_json
    unofficial::sqlite3::sqlite3
    CURL::libcurl
    spdlog::spdlog
    ZLIB::ZLIB
    log  # Android logcat
)

# Android-specific definitions
target_compile_definitions(icraw PRIVATE ICRAW_ANDROID)
```

#### Step 1b.3: Android Log Adaptation

Create Android-specific log sink to replace spdlog file-based logging:

```cpp
// android_log_sink.hpp
#include <android/log.h>
#include <spdlog/sinks/base_sink.h>

class android_log_sink : public spdlog::sinks::base_sink<std::mutex> {
protected:
    void sink_it_(const spdlog::details::log_msg& msg) override {
        __android_log_print(ANDROID_LOG_DEBUG, "icraw", "%s", msg.payload.c_str());
    }
    void flush_() override {}
};
```

### 3.3 Build System Setup

#### Step 1: Configure Agent Module (agent/build.gradle)

```gradle
plugins {
    id 'com.android.library'
}

android {
    namespace 'com.hh.agent.library'
    compileSdk 34

    defaultConfig {
        minSdk 24
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
        }
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }

    ndkVersion "26.3.11579264"
}
```

#### Step 2: Create CMakeLists.txt

Create `agent/src/main/cpp/CMakeLists.txt` based on cxxplatform/CMakeLists.txt:

```cmake
cmake_minimum_required(VERSION 3.22.1)

project(icraw VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find dependencies (via Conan)
find_package(nlohmann_json CONFIG REQUIRED)
find_package(ZLIB REQUIRED)
find_package(unofficial-sqlite3 CONFIG REQUIRED)
find_package(CURL CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)

# Source files (copied from cxxplatform)
set(ICRAW_SOURCES
    # ... list all source files
)

# Create shared library
add_library(icraw SHARED ${ICRAW_SOURCES})

target_include_directories(icraw PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(icraw PUBLIC
    nlohmann_json::nlohmann_json
    unofficial::sqlite3::sqlite3
    CURL::libcurl
    spdlog::spdlog
    ZLIB::ZLIB
    log  # Android logcat
)

# Android-specific definitions
target_compile_definitions(icraw PRIVATE ICRAW_ANDROID)
```

### 3.2 Dependency Installation (Conan)

#### Create conanfile.py for agent module

```python
from conan import ConanFile
from conan.tools.cmake import cmake_layout

class AgentConan(ConanFile):
    settings = "os", "compiler", "build_type", "arch"
    generators = ["CMakeDeps", "CMakeToolchain"]
    options = {"shared": [True, False]}
    default_options = {"shared": True}
    requires = [
        "libcurl/8.1.2",
        "nlohmann_json/3.11.3",
        "spdlog/1.15.1",
    ]

    def configure(self):
        self.options["spdlog"].header_only = True

    def layout(self):
        cmake_layout(self)
```

#### Install Dependencies

```bash
cd agent
conan install .. --profile=../android.profile -s arch=armv8
```

### 3.3 Code Modifications

#### Logger Adaptation

The existing logger uses file-based rotation. For Android, we need to create an Android-specific sink:

**Option A:** Create custom spdlog sink
```cpp
// android_log_sink.hpp
#include <android/log.h>
#include <spdlog/sinks/base_sink.h>

class android_log_sink : public spdlog::sinks::base_sink<std::mutex> {
protected:
    void sink_it_(const spdlog::details::log_msg& msg) override {
        // Convert to Android log and write
        __android_log_print(ANDROID_LOG_DEBUG, "icraw", "%s", msg.payload.c_str());
    }
    void flush_() override {}
};
```

**Option B:** Keep file logging but redirect to app-specific directory
- Use `/data/data/{package}/files/logs/` instead of arbitrary paths
- Requires JNI to get the app's files directory

#### Configuration

- Pass configuration from Java via JNI (programmatic configuration)
- IcrawConfig already supports programmatic creation
- LLM API key managed by Java layer

### 3.4 JNI Preparation (Minimal for Phase 1)

While full JNI is Phase 2, Phase 1 should create the JNI entry point:

```cpp
// JNI entry point - minimal for Phase 1 testing
extern "C" JNIEXPORT jstring JNICALL
Java_com_hh_agent_library_NativeAgent_nativeGetVersion(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("1.0.0");
}
```

---

## 4. Technical Considerations

### 4.1 ABI Support

| ABI | Status | Notes |
|-----|--------|-------|
| armeabi-v7a | Required | 32-bit ARM |
| arm64-v8a | Required | 64-bit ARM |
| x86 | Required | 32-bit x86 (emulator) |
| x86_64 | Required | 64-bit x86 (emulator) |

**Conan note:** Each ABI requires a separate `conan install` with different `-s arch` setting.

### 4.2 NDK Version

- **Project NDK:** 26.3.11579264 (from lib/build.gradle)
- **Conan profile:** Uses same version
- **API Level:** 26 (Android 8.0) - aligned with minSdk 24

### 4.3 Memory Considerations

- **sqlite3**: Compiled as shared library
- **libcurl**: Compiled as shared library
- **spdlog/nlohmann-json**: Header-only, no additional binary size

### 4.4 Build Output

- **Library name:** `libicraw.so` (as per decision)
- **Location:** `agent/build/intermediates/cmake/debug/obj/{abi}/libicraw.so`

---

## 5. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|-------------|
| Conan dependencies fail on M1/M2 Mac | Build blocked | Use Rosetta or conan-center cache |
| Logger file path issues on Android | Runtime crash | Use JNI to get app's files dir |
| ABI filter mismatch with SYS-03 | Requirements gap | Use all 4 ABIs in ndk.abiFilters |
| cxxplatform has unknown dependencies | Build fails | Full code review before porting |

---

## 6. Research Questions

1. **ABI Scope:** The decision says arm64-v8a only, but SYS-03 requires all four. Should we build for all four or clarify?

2. **Conan Integration:** Should the agent module have its own conanfile.py, or should there be a centralized Conan configuration at project root?

3. **Logger Strategy:** Should we create a custom Android sink for spdlog, or use the file-based approach with app-specific directories?

4. **File Organization:** Should cxxplatform files be copied to agent/src/main/cpp/ or should CMake reference the original location?

---

## 7. Next Steps (Priority Ordered)

### Phase 1a: Conan Setup (DO FIRST)
1. **Create agent/conanfile.py** - Copy from android-cxx-thirdpartylib, align versions
2. **Create agent/android.profile** - Based on reference profile
3. **Integrate prefab plugin or CMake toolchain** - For dependency resolution
4. **Run conan install** - Verify dependencies install successfully
5. **Verify Conan build** - Confirm arm64-v8a libraries compile

### Phase 1b: cxxplatform Porting (DO SECOND - ONLY AFTER CONAN WORKS)
6. **Copy cxxplatform sources** - Include and src files to agent module
7. **Create agent CMakeLists.txt** - Based on cxxplatform with Android adaptations
8. **Add NDK configuration to agent/build.gradle** - CMake and abiFilters
9. **Create Android log sink** - Replace spdlog file logging with logcat
10. **Test compilation** - Run assembleDebug, verify libicraw.so generated

### Post-Build Verification
11. **Verify .so output** - Check agent/build for libicraw.so
12. **Test on device/emulator** - Basic load test

---

## 8. Action Items Summary (Priority Ordered)

### Phase 1a: Conan Dependency Setup (FIRST - DO NOT SKIP)

| # | Task | Status |
|---|------|--------|
| 1a-1 | Create agent/conanfile.py with aligned dependencies | Pending |
| 1a-2 | Create agent/android.profile | Pending |
| 1a-3 | Integrate prefab plugin or configure CMake toolchain | Pending |
| 1a-4 | Run conan install for arm64-v8a | Pending |
| 1a-5 | **VERIFY: Conan dependencies install successfully** | Pending |

### Phase 1b: cxxplatform Porting (SECOND - ONLY AFTER 1a COMPLETE)

| # | Task | Status |
|---|------|--------|
| 1b-1 | Copy cxxplatform/include to agent/src/main/cpp/ | Pending |
| 1b-2 | Copy cxxplatform/src to agent/src/main/cpp/ | Pending |
| 1b-3 | Create agent/src/main/cpp/CMakeLists.txt | Pending |
| 1b-4 | Update agent/build.gradle with CMake + NDK config | Pending |
| 1b-5 | Implement Android log sink | Pending |
| 1b-6 | Run assembleDebug | Pending |
| 1b-7 | **VERIFY: libicraw.so generated** | Pending |

### Critical Path

```
[1a-1] -> [1a-2] -> [1a-3] -> [1a-4] -> [1a-5 VERIFY]
                                              |
                                              v
                      [1b-1] -> [1b-2] -> [1b-3] -> [1b-4] -> [1b-5] -> [1b-6] -> [1b-7 VERIFY]
```

**BLOCKER CONDITION:** Do not proceed to Phase 1b tasks until Phase 1a verification passes.

---

## References

- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/CMakeLists.txt` - Source CMake configuration
- `/Users/caixiao/Workspace/projects/mobile-agent/cxxplatform/include/icraw/mobile_agent.hpp` - Main class interface
- `/Users/caixiao/Workspace/projects/android-cxx-thirdpartylib/lib/conanfile.py` - Dependency reference
- `/Users/caixiao/Workspace/projects/android-cxx-thirdpartylib/lib/android.profile` - Android profile reference
- `/Users/caixiao/Workspace/projects/mobile-agent/lib/build.gradle` - Existing NDK module config
- https://docs.conan.io/2/examples/cross_build/android/android_studio.html - Android + Conan integration

---

*Research completed for Phase 1: Build System & Agent Core*
*Updated: 2026-03-03 with user-prioritized implementation order*
