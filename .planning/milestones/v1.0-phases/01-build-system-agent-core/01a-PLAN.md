---
wave: 2
depends_on:
  - 01-PLAN.md
files_modified:
  - agent/build.gradle
  - agent/src/main/cpp/CMakeLists.txt
  - agent/src/main/cpp/include/icraw/*.hpp
  - agent/src/main/cpp/include/icraw/core/*.hpp
  - agent/src/main/cpp/src/*.cpp
  - agent/src/main/cpp/src/core/*.cpp
autonomous: true
---

# Phase 1: Build System & Agent Core - Plan 01a

**Phase:** 01-build-system-agent-core
**Wave:** 2a (Gradle + CMake + Copy Sources)
**Dependencies:** Wave 1 (01-PLAN.md - Conan Dependencies)

## Goal (Wave 2a)

After Conan dependencies are verified working, configure Gradle build system, create CMakeLists.txt, and copy cxxplatform source files.

## Requirements Coverage (Wave 2a)

| Requirement | Description | Status |
|-------------|-------------|--------|
| SYS-01 | Gradle build scripts include C++ compilation tasks | Must Have |
| SYS-02 | C++ code uses CMake build | Must Have |
| SYS-03 | Support arm64-v8a architecture | Must Have |

---

### Task W2-1: Configure agent/build.gradle with NDK/CMake support

**Description:** Add NDK configuration and externalNativeBuild to agent/build.gradle

**Files:**
- agent/build.gradle

**Actions:**
- Add ndk block with abiFilters for arm64-v8a
- Add externalNativeBuild block with cmake configuration
- Set ndkVersion to "26.3.11579264"
- Add compileOptions for Java 21

**Verification:**
- agent/build.gradle contains ndk {} block
- agent/build.gradle contains externalNativeBuild {} block

---

### Task W2-2: Create agent CMakeLists.txt

**Description:** Create CMakeLists.txt in agent/src/main/cpp/ based on cxxplatform CMakeLists.txt

**Files:**
- agent/src/main/cpp/CMakeLists.txt

**Actions:**
- Create directory agent/src/main/cpp/
- Create CMakeLists.txt with cmake_minimum_required(VERSION 3.22.1)
- Configure project with C++17
- Set up find_package for dependencies (nlohmann_json, ZLIB, unofficial-sqlite3, CURL, spdlog)
- Define ICRAW_SOURCES with all source files
- Create shared library icraw
- Add Android-specific definitions (ICRAW_ANDROID)
- Link required libraries including log (Android logcat)

**Verification:**
- CMakeLists.txt exists at agent/src/main/cpp/CMakeLists.txt
- CMakeLists.txt contains all required dependencies
- CMakeLists.txt links to Android log library

---

### Task W2-3: Copy cxxplatform source files

**Description:** Copy source and header files from cxxplatform to agent module

**Files:**
- agent/src/main/cpp/include/icraw/*.hpp
- agent/src/main/cpp/include/icraw/core/*.hpp
- agent/src/main/cpp/src/*.cpp
- agent/src/main/cpp/src/core/*.cpp

**Actions:**
- Create agent/src/main/cpp/include/icraw/ directory
- Create agent/src/main/cpp/include/icraw/core/ directory
- Create agent/src/main/cpp/src/ directory
- Create agent/src/main/cpp/src/core/ directory
- Copy all header files from cxxplatform/include/icraw/
- Copy all source files from cxxplatform/src/

**Verification:**
- All header files copied to agent/src/main/cpp/include/
- All source files copied to agent/src/main/cpp/src/
- Directory structure matches cxxplatform

---

## Wave 2a Verification Criteria

The following must be TRUE for Wave 2a completion:

1. **Gradle configured** - agent/build.gradle contains NDK and CMake configuration
2. **CMakeLists.txt created** - CMake configuration present in agent/src/main/cpp/
3. **Source files copied** - All cxxplatform headers and sources in agent module

---

## Wave 2a Must Haves

| Must Have | Verification Method |
|-----------|---------------------|
| NDK configured | ndk {} block in build.gradle |
| CMake configured | externalNativeBuild {} in build.gradle |
| CMakeLists.txt exists | File at agent/src/main/cpp/CMakeLists.txt |
| Headers copied | icraw headers in include/icraw/ |
| Sources copied | icraw sources in src/ |

---

## Critical Path

```
WAVE 1 (Conan)                    WAVE 2a (Gradle+CMake+Copy)
     |                                  |
     v                                  v
[W1-1] -> [W1-2] -> [W1-3] -> [W1-4] |
    (conanfile.py)   (install)   (verify) |
                                          v
                    [W2-1] -> [W2-2] -> [W2-3]
                    (gradle)   (cmake)   (copy)
```

**BLOCKER CONDITION:** Do not proceed to Wave 2a tasks until Wave 1 verification passes.

---

*Plan created for Phase 1: Build System & Agent Core - Wave 2a (Gradle + CMake + Copy Sources)*
*Updated: 2026-03-03*
