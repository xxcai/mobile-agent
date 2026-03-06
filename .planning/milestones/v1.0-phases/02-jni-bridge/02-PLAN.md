---
phase: 02-jni-bridge
plan: 01
subsystem: jni
tags: [jni, android, native-bridge]

# Dependency graph
requires: [01-SUMMARY]
provides:
  - Java ↔ C++ bidirectional communication working
  - nativeInitialize creates MobileAgent instance
  - nativeSendMessage calls MobileAgent::chat()
affects: [03-PLAN]

# Tech tracking
tech-stack:
  added: [JNI, global references]
  patterns: [Java-C++ interop]

key-files:
  created:
    - agent/src/main/cpp/native_agent.cpp - Updated with MobileAgent integration
  modified:
    - agent/src/main/java/com/hh/agent/library/NativeAgent.java - May add helper methods

must_haves:
  - MobileAgent instance stored globally in native code
  - nativeInitialize creates MobileAgent with workspace path
  - nativeSendMessage calls MobileAgent::chat() and returns result
  - Proper JNI reference management (no memory leaks)
  - Build succeeds for arm64-v8a

# Verification
verification:
  - Gradle build succeeds
  - nativeInitialize logs show agent initialization
  - nativeSendMessage returns non-echo response
---

# Phase 2 Plan 1: JNI Bridge Implementation

**Implement Java ↔ C++ bidirectional communication with MobileAgent integration**

## Tasks

### W2-1: Add MobileAgent global state management
**Requirement:** JNI-01
**Type:** feature

- Create global pointer to store MobileAgent instance
- Add JNI_NATIVE_AGENT field to native_agent.cpp
- Initialize in JNI_OnLoad or first native call
**Files:** agent/src/main/cpp/native_agent.cpp
**Verification:** Compiles without errors

### W2-2: Implement nativeInitialize with MobileAgent creation
**Requirement:** JNI-01
**Type:** feature

- Parse config path from Java parameter
- Call MobileAgent::create() to instantiate agent
- Store agent pointer in global state
- Log initialization success/failure
**Files:** agent/src/main/cpp/native_agent.cpp
**Verification:** Agent created and logged

### W2-3: Replace nativeSendMessage with MobileAgent::chat() call
**Requirement:** JNI-02
**Type:** feature

- Get MobileAgent pointer from global state
- Call agent->chat(message) instead of echo
- Return response string to Java
- Handle null agent case
**Files:** agent/src/main/cpp/native_agent.cpp
**Verification:** Non-echo response returned

### W2-4: Add error handling and nativeShutdown cleanup
**Requirement:** JNI-01, JNI-02
**Type:** feature

- Implement nativeShutdown to delete MobileAgent
- Add null checks in all JNI methods
- Ensure proper cleanup on errors
**Files:** agent/src/main/cpp/native_agent.cpp
**Verification:** Clean shutdown without leaks

### W2-5: Build and verify
**Requirement:** All JNI requirements
**Type:** verification

- Run ./gradlew assembleDebug
- Verify libicraw.so generated
- Review build logs for errors
**Files:** Build output
**Verification:** Build succeeds

## Implementation Notes

- MobileAgent::create() takes workspace_path parameter
- IcrawConfig::load_default() provides default config
- chat() method returns std::string response
- Need to ensure proper JNI string conversion with GetStringUTFChars/ReleaseStringUTFChars
