---
phase: 03-show-toast
plan: 01
subsystem: tools
tags: [android, tool, toast]

# Dependency graph
requires: [02]
provides:
  - show_toast tool integrated with C++ agent
  - End-to-end tool call flow working
affects: []

# Tech tracking
tech-stack:
  added: [AndroidToolManager, ShowToastTool]
  patterns: [JNI Callback, Tool Registry]

key-files:
  created:
    - agent/src/main/cpp/src/tools/tool_registry.cpp (added show_toast registration)
    - agent/src/main/java/com/hh/agent/library/NativeAgent.java (added nativeRegisterAndroidToolCallback)

must_haves:
  - show_toast tool registered in C++ ToolRegistry
  - JNI callback properly handles multi-threaded calls
  - AndroidToolManager registers callback after NativeAgent init
  - Toast displays on screen when agent calls show_toast

# Verification
verification:
  - Toast shows on device screen when agent calls show_toast
  - Tool execution returns success result
  - No crashes during tool invocation
---

# Phase 3: show_toast Tool Summary

**Created:** 2026-03-05

## Summary

Successfully implemented end-to-end show_toast tool integration. The C++ agent can now call Android platform functions through JNI.

## Completed Tasks

| Task | Name | Status |
|------|------|--------|
| T1 | Install APK | ✓ Complete |
| T2 | Trigger show_toast | ✓ Complete |
| T3 | Verify Toast displays | ✓ Complete |

## Key Fixes Made

1. **Added show_toast to C++ ToolRegistry** (`tool_registry.cpp`)
   - Registered show_toast tool schema for LLM function calling

2. **Fixed JNI callback thread safety** (`native_agent.cpp`)
   - Changed from caching JNIEnv to using JavaVM AttachCurrentThread
   - Ensures callback works when called from worker threads

3. **Connected Java callback to C++** (`NativeAgent.java`)
   - Added nativeRegisterAndroidToolCallback native method call
   - Previously only stored callback in Java, didn't register with C++

## Decisions Made

- Use JavaVM AttachCurrentThread for thread-safe JNI calls
- Register show_toast as built-in tool in ToolRegistry
- AndroidToolManager initializes after NativeAgent initialization

## Deviation Documentation

None - plan executed with necessary fixes.

## Build Verification

```
BUILD SUCCESSFUL
```

## Self-Check: PASSED

- show_toast tool displays Toast on device: ✓ Verified
- JNI callback thread-safe: ✓ Fixed
- No crashes: ✓ Verified
