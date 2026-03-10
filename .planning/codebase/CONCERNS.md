# Codebase Concerns

**Analysis Date:** 2026-03-10

## Tech Debt

**Code Duplication Between cxxplatform and agent-core:**
- Issue: The same C++ source files exist in both `cxxplatform/src/` and `agent-core/src/main/cpp/src/`. Files differ but contain mostly identical implementations.
- Files: `config.cpp`, `mobile_agent.cpp`, `logger.cpp`, `src/core/llm_provider.cpp`, `src/core/memory_manager.cpp`, `src/core/agent_loop.cpp`, `src/core/skill_loader.cpp`, `src/tools/tool_registry.cpp`
- Impact: Maintenance burden - bug fixes must be applied in two places. Risk of divergence.
- Fix approach: Use a shared library approach or CMake fetch_content to include cxxplatform source in agent-core, or consolidate to a single source location.

**Silent Exception Handling:**
- Issue: Multiple `catch (...)` blocks that silently swallow exceptions without proper error reporting or fallback handling.
- Files: `cxxplatform/src/core/memory_manager.cpp` (lines 579, 951, 1004), `cxxplatform/src/tools/tool_registry.cpp` (lines 481, 648, 722, 791, 1120)
- Impact: Errors are hidden, making debugging difficult. Failures may manifest as unexpected behavior rather than clear errors.
- Fix approach: Replace catch-all blocks with specific exception handling. Log errors before handling. Return meaningful error codes.

**Outdated Target SDK:**
- Issue: Project targets SDK 31 (Android 12) but compiles with SDK 34. Should target modern Android versions.
- Files: `app/build.gradle`, `agent-android/build.gradle`, `agent-core/build.gradle` (all set targetSdk 31)
- Impact: Missing newer Android APIs, security patches, and performance improvements.
- Fix approach: Update targetSdk to 34 or 35 in all build.gradle files.

## Known Bugs

**No specific known bugs identified in current codebase.**

## Security Considerations

**API Key Storage:**
- Risk: API keys are stored in plaintext `config.json` file which gets packaged into the APK assets.
- Files: `config.json.template`, `config.json` (if created)
- Current mitigation: None
- Recommendations:
  1. Use Android's EncryptedSharedPreferences for API keys
  2. Require user to input API key at runtime (first launch)
  3. Store in secure credential storage (Android Keystore)

**No Input Validation on External Data:**
- Risk: JSON parsing with nlohmann-json could fail on malformed input, leading to crashes or unexpected behavior.
- Files: `cxxplatform/src/core/llm_provider.cpp`, `cxxplatform/src/core/mcp_client.cpp`
- Current mitigation: try-catch blocks exist but silently handle errors
- Recommendations: Add explicit input validation before parsing, provide user feedback on malformed data

**File System Path Access:**
- Risk: Tool registry allows file system access with path validation, but relies on regex-based validation.
- Files: `cxxplatform/src/tools/tool_registry.cpp` (is_path_allowed function)
- Current mitigation: Path allowlist patterns
- Recommendations: Consider using canonical path comparison instead of regex patterns

## Performance Bottlenecks

**Large Memory Manager:**
- Problem: `memory_manager.cpp` is 1338 lines with complex SQLite operations. No caching of query results.
- Files: `cxxplatform/src/core/memory_manager.cpp`
- Cause: Every memory query performs full SQLite operations without result caching
- Improvement path: Add in-memory LRU cache for frequent queries, batch similar queries

**Large Tool Registry:**
- Problem: `tool_registry.cpp` is 1447 lines, handling all tool registration and execution. Single monolithic class.
- Files: `cxxplatform/src/tools/tool_registry.cpp`
- Cause: All tool operations (parsing, validation, execution, result formatting) in one file
- Improvement path: Extract into separate classes: ToolValidator, ToolExecutor, ResultFormatter

**JSON Parsing Overhead:**
- Problem: Repeated JSON parsing of tool arguments for each invocation.
- Files: `cxxplatform/src/core/llm_provider.cpp`, `cxxplatform/src/tools/tool_registry.cpp`
- Cause: No caching of parsed tool schemas
- Improvement path: Parse tool schema once at load time, cache structured representation

## Fragile Areas

**Native Agent Initialization:**
- Why fragile: JNI bridge between Java and C++ is complex. Initialization failures are hard to diagnose.
- Files: `agent-core/src/main/cpp/native_agent.cpp`, `agent-core/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java`
- Safe modification: Add detailed logging at each initialization step. Ensure error messages propagate to Java layer.
- Test coverage: No integration tests for native initialization

**Tool Execution via JNI:**
- Why fragile: Tool results must be converted between C++ JSON and Java objects. Complex type mapping.
- Files: `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`, `agent-core/src/main/cpp/android_tools.cpp`
- Safe modification: Add validation on both sides of JNI boundary. Log raw data before conversion.
- Test coverage: Limited - only tested via manual UI testing

**Config JSON Loading:**
- Why fragile: Config loaded from assets at runtime. If malformed, failure is silent or crashes.
- Files: `cxxplatform/src/config.cpp`, `agent-core/src/main/cpp/src/config.cpp`
- Safe modification: Add config validation on load. Provide clear error messages.
- Test coverage: No tests for config validation

## Scaling Limits

**Single-threaded Agent Loop:**
- Current capacity: One agent loop at a time
- Limit: Cannot process multiple concurrent user requests
- Scaling path: Add request queue, thread pool for parallel processing

**In-Memory Conversation History:**
- Current capacity: Limited by device memory (typically 100-200KB for conversation history)
- Limit: Long conversations will exceed token limits
- Scaling path: Implement conversation summarization, offload older messages to SQLite

**SQLite Database:**
- Current capacity: Single file database
- Limit: No horizontal scaling, single device only
- Scaling path: Not applicable (single-device app)

## Dependencies at Risk

**Conan Packages:**
- Risk: Native dependencies (nlohmann-json, sqlite3, curl, spdlog) managed via Conan. Version locked in vcpkg.json.
- Impact: If Conan package versions become unavailable or incompatible, native build fails.
- Migration plan: Consider vendoring critical dependencies or using Conan remote with specific versions

**Markwon 4.6.2:**
- Risk: Old version (last update 2022). May have unpatched vulnerabilities.
- Impact: Markdown rendering may have security issues
- Migration plan: Evaluate migration to more maintained fork (io.noties.markwon:4.6.2 is deprecated)

**Catch2 (Testing):**
- Risk: Only used in cxxplatform, not integrated into agent-core builds
- Impact: No automated tests run for native code in Android context
- Migration plan: Integrate Catch2 into agent-core CMake build

## Missing Critical Features

**No Automated Tests for Native Android Code:**
- Problem: Native code in agent-core has no automated tests. Tests only exist in cxxplatform.
- Blocks: Safe refactoring, regression detection

**No Logging Infrastructure in Java Layer:**
- Problem: Android Java code lacks structured logging like C++ layer uses spdlog
- Blocks: Production debugging, error tracking

**No Crash Reporting:**
- Problem: No crash reporting service (Firebase Crashlytics, Bugsnag, etc.)
- Blocks: Understanding production failures

## Test Coverage Gaps

**Native Core (agent-core):**
- What's not tested: All native C++ code in agent-core
- Files: `agent-core/src/main/cpp/src/*` - none have tests
- Risk: Native crashes cannot be traced to specific code changes
- Priority: High

**Java Android Tools:**
- What's not tested: Tool implementations (ShowToastTool, TakeScreenshotTool, etc.)
- Files: `agent-android/src/main/java/com/hh/agent/android/tool/*.java`, `app/src/main/java/com/hh/agent/tool/*.java`
- Risk: Tool failures in production without visibility
- Priority: Medium

**JNI Bridge:**
- What's not tested: Java to C++ interface boundary
- Files: `agent-core/src/main/cpp/native_agent.cpp`, `agent-core/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java`
- Risk: Type conversion errors at JNI boundary cause crashes
- Priority: High

**Memory Manager:**
- What's not tested: Memory pruning, token budget calculations
- Files: `cxxplatform/src/core/memory_manager.cpp`
- Risk: Memory overflow or truncation without warning
- Priority: Medium

---

*Concerns audit: 2026-03-10*
