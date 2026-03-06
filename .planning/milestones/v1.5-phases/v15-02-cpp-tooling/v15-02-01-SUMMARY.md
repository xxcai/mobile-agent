---
phase: v15-02-cpp-tooling
plan: 01
subsystem: native-agent
tags: [jni, tool-registry, android-tools, cpp]

# Dependency graph
requires:
  - phase: v15-01-tools-schema
    provides: tools.json with call_android_tool schema
provides:
  - JNI method nativeSetToolsSchema to receive tools schema from Java
  - ToolRegistry.register_tools_from_schema to parse and register external tools
  - Integration of tools.json into C++ agent initialization flow
affects:
  - v15-03-verification
  - android-tool-callback

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JNI bridge: Java passes JSON schema to C++"
    - "External tool schema registration at runtime"

key-files:
  created: []
  modified:
    - agent/src/main/cpp/native_agent.cpp
    - agent/src/main/cpp/include/icraw/tools/tool_registry.hpp
    - agent/src/main/cpp/src/tools/tool_registry.cpp
    - agent/src/main/java/com/hh/agent/library/NativeAgent.java
    - agent/src/main/java/com/hh/agent/library/api/NativeNanobotApi.java

key-decisions:
  - "Pass tools schema as JSON string via JNI instead of file path"
  - "Execute tools via Android callback mechanism (g_android_tools.call_tool), not via ToolRegistry function"

patterns-established:
  - "Java loads tools.json from assets, passes to native via nativeSetToolsSchema"
  - "C++ ToolRegistry stores tool schema but uses Android callback for execution"

requirements-completed: [PIPE-02, MODE-01]

# Metrics
duration: 2min
completed: 2026-03-05
---

# Phase v15-02 Plan 01: C++ Agent Tool Pipeline

**LLM sees only call_android_tool, Android tools registered from external JSON schema**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-05T08:51:38Z
- **Completed:** 2026-03-05T08:53:13Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Added JNI method nativeSetToolsSchema to receive tools schema from Java
- Added register_tools_from_schema method in ToolRegistry to parse external JSON
- Integrated tools.json loading in NativeNanobotApi initialization flow

## Task Commits

Each task was committed atomically:

1. **Task 1: Add JNI method to receive tools schema** - `5e4af2b` (feat)
2. **Task 2: Modify ToolRegistry to accept external schema** - `c6b5dd6` (feat)
3. **Task 3: Verify tools.json is loaded and passed** - `15ab94d` (feat)

## Files Created/Modified
- `agent/src/main/cpp/native_agent.cpp` - Added nativeSetToolsSchema JNI function
- `agent/src/main/cpp/include/icraw/tools/tool_registry.hpp` - Added register_tools_from_schema declaration
- `agent/src/main/cpp/src/tools/tool_registry.cpp` - Implemented register_tools_from_schema
- `agent/src/main/java/com/hh/agent/library/NativeAgent.java` - Added nativeSetToolsSchema method
- `agent/src/main/java/com/hh/agent/library/api/NativeNanobotApi.java` - Added tools.json loading and passing

## Decisions Made
- Passed tools schema as JSON string via JNI rather than file path - simpler and more flexible
- Tools execute through Android callback mechanism (g_android_tools.call_tool), not via ToolRegistry function - maintains existing architecture

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Tool pipeline foundation complete
- Ready for verification phase to test tool registration and execution
- Android callback mechanism already in place for tool execution

---
*Phase: v15-02-cpp-tooling*
*Completed: 2026-03-05*
