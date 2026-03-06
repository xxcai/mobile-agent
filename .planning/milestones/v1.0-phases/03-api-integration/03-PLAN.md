---
phase: 03-api-integration
plan: 01
subsystem: api
tags: [api, native, android]

# Dependency graph
requires: [02-SUMMARY]
provides:
  - NativeNanobotApi implementing NanobotApi interface
  - Java API layer using NativeAgent JNI
affects: []

# Tech tracking
tech-stack:
  added: [Java Interface, NativeWrapper]
  patterns: [Adapter Pattern]

key-files:
  created:
    - agent/src/main/java/com/hh/agent/library/api/NanobotApi.java - API interface
    - agent/src/main/java/com/hh/agent/library/api/NativeNanobotApi.java - Native implementation
    - agent/src/main/java/com/hh/agent/library/model/Message.java - Message model
    - agent/src/main/java/com/hh/agent/library/model/Session.java - Session model

must_haves:
  - NanobotApi interface defined with createSession, getSession, sendMessage, getHistory
  - NativeNanobotApi implements NanobotApi using NativeAgent JNI
  - Compatible with existing HttpNanobotApi interface
  - Proper error handling for native calls

# Verification
verification:
  - NativeNanobotApi compiles without errors
  - Interface matches HttpNanobotApi signature
  - NativeAgent JNI calls work correctly
---

# Phase 3 Plan 1: API Integration Implementation

**Create NativeNanobotApi implementation using NativeAgent JNI wrapper**

## Tasks

### W3-1: Copy NanobotApi interface to agent module
**Requirement:** API-01
**Type:** feature

- Copy interface from lib/src/main/java/com/hh/agent/lib/api/NanobotApi.java
- Place in agent/src/main/java/com/hh/agent/library/api/NanobotApi.java
- Ensure same method signatures
**Files:** agent/src/main/java/com/hh/agent/library/api/NanobotApi.java
**Verification:** Interface compiles

### W3-2: Copy model classes to agent module
**Requirement:** API-01
**Type:** feature

- Copy Message.java from lib to agent module
- Copy Session.java from lib to agent module
- Place in agent/src/main/java/com/hh/agent/library/model/
**Files:**
  - agent/src/main/java/com/hh/agent/library/model/Message.java
  - agent/src/main/java/com/hh/agent/library/model/Session.java
**Verification:** Models compile

### W3-3: Create NativeNanobotApi implementation
**Requirement:** API-02
**Type:** feature

- Implement NanobotApi interface
- Use NativeAgent JNI for native calls
- Implement createSession, getSession, sendMessage, getHistory methods
- Handle native initialization and cleanup
**Files:** agent/src/main/java/com/hh/agent/library/api/NativeNanobotApi.java
**Verification:** Implementation compiles

### W3-4: Verify API compatibility
**Requirement:** API-03
**Type:** verification

- Compare method signatures with HttpNanobotApi
- Ensure NativeNanobotApi can replace HttpNanobotApi
- Test build succeeds
**Files:** All created files
**Verification:** Build succeeds, interface compatible

## Implementation Notes

- NativeNanobotApi uses NativeAgent.initialize() for setup
- sendMessage calls NativeAgent.sendMessage() JNI method
- Session and Message models must match lib module structures
- Consider singleton pattern for NativeAgent instance management
