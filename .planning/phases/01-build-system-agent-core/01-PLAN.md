---
wave: 1
depends_on: []
files_modified:
  - agent/conanfile.py
  - agent/android.profile
autonomous: true
---

# Phase 1: Build System & Agent Core - Plan

**Phase:** 01-build-system-agent-core
**Wave:** 1 (Conan Dependencies - DO FIRST)
**Dependencies:** None (first phase)

## Goal (Wave 1)

Complete Conan dependency installation and compilation, verify build passes. This is the first priority as per user instruction.

## Requirements Coverage (Wave 1)

| Requirement | Description | Status |
|-------------|-------------|--------|
| SYS-01 | Gradle build scripts include C++ compilation tasks | Must Have |
| SYS-02 | C++ code uses CMake build | Must Have |
| SYS-03 | Support arm64-v8a architecture | Must Have |

## Tasks (Wave 1: Conan Dependencies)

### Task W1-1: Create agent/conanfile.py

**Description:** Create Conan configuration file for agent module dependencies

**Files:**
- agent/conanfile.py

**Actions:**
- Create conanfile.py with required dependencies (nlohmann_json, spdlog, libcurl, unofficial-sqlite3)
- Align versions with android-cxx-thirdpartylib reference
- Set shared=True for curl and sqlite3
- Configure spdlog as header_only

**Verification:**
- conanfile.py exists in agent/ directory
- Contains all required dependencies with correct versions

---

### Task W1-2: Create agent/android.profile

**Description:** Create Conan Android profile for cross-compilation

**Files:**
- agent/android.profile

**Actions:**
- Create android.profile based on android-cxx-thirdpartylib reference
- Set os=Android with api_level=26
- Set arch=armv8 (arm64-v8a)
- Configure compiler=clang version=17
- Set NDK path to /Users/caixiao/Library/Android/sdk/ndk/26.3.11579264

**Verification:**
- android.profile exists in agent/ directory
- Contains correct NDK path

---

### Task W1-3: Run Conan install for dependencies

**Description:** Install Conan dependencies for Android arm64-v8a target

**Actions:**
- Run conan install in agent directory
- Use android.profile
- Verify dependencies download and compile

**Verification:**
- Conan install completes without errors
- Dependencies available in Conan cache

---

### Task W1-4: Verify Conan build

**Description:** Verify Conan dependencies can be built for Android

**Actions:**
- Run conan install with --build=missing flag
- Verify arm64-v8a libraries are built

**Verification:**
- Conan build succeeds for arm64-v8a

---

## Wave 1 Verification Criteria

The following must be TRUE for Wave 1 completion:

1. **Conan dependencies install** - conan install completes without errors
2. **Dependencies available** - All required packages (nlohmann_json, spdlog, libcurl, sqlite3) available
3. **arm64-v8a libraries built** - Native libraries compiled for target architecture

---

## Wave 1 Must Haves

| Must Have | Verification Method |
|-----------|---------------------|
| conanfile.py created | File exists with required dependencies |
| android.profile created | File exists with correct NDK settings |
| Conan install succeeds | conan install runs without errors |
| Dependencies built | Native libraries compiled for arm64-v8a |

---

*Plan created for Phase 1: Build System & Agent Core - Wave 1 (Conan Dependencies)*
*Updated: 2026-03-03*
