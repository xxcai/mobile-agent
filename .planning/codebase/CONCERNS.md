# Codebase Concerns

**Analysis Date:** 2026-03-09

## Tech Debt

**C++ Code Duplication:**
- Issue: C++ source code exists in two locations: `agent/src/main/cpp/src/` and `cxxplatform/src/`
- Files: Multiple duplicate files including `mobile_agent.cpp`, `config.cpp`, `curl_http_client.cpp`, `llm_provider.cpp`, etc.
- Impact: Maintenance burden, potential divergence between implementations
- Fix approach: Consolidate to single source location; use Gradle source sets to include from one location

**API Key Configuration:**
- Issue: API key configuration approach unclear - local.properties mentioned in docs but mechanism unclear
- Files: `agent/src/main/cpp/src/config.cpp`, `agent/build.gradle`
- Impact: Hard to configure for production builds
- Fix approach: Implement secure config loading from Android Keystore or proper asset-based config

**Outdated Target SDK:**
- Issue: targetSdk is 31 while compileSdk is 34
- Files: `app/build.gradle` (line 12)
- Impact: Missing latest Android security updates and features
- Fix approach: Update targetSdk to 34

**Minimal ProGuard Configuration:**
- Issue: ProGuard rules only preserve line numbers, no actual obfuscation
- Files: `app/proguard-rules.pro`
- Impact: App can be easily reverse-engineered
- Fix approach: Add proper obfuscation rules for OkHttp, Gson, and other libraries

**In-Memory Session Storage:**
- Issue: Session data stored in ConcurrentHashMap, lost on app restart
- Files: `agent/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` (line 20)
- Impact: No persistence of conversation history across app restarts
- Fix approach: Implement SQLite or Room database for session persistence

**Missing Test Coverage:**
- Issue: No unit tests for C++ core modules
- Files: `agent/src/main/cpp/src/**/*.cpp`
- Impact: Hard to detect regressions in native code
- Fix approach: Add C++ unit tests using Google Test

---

## Security Considerations

**SSL Verification Disabled:**
- Risk: HTTPS requests skip certificate verification
- Files: `agent/src/main/cpp/src/core/curl_http_client.cpp` (lines 88-89, 193-195)
- Current mitigation: None
- Recommendations: Enable SSL verification for production; only disable for local development with clear comment

**Cleartext Traffic Allowed:**
- Risk: App uses cleartextTraffic="true" in manifest
- Files: `app/src/main/AndroidManifest.xml` (line 13)
- Current mitigation: None
- Recommendations: Set to false; configure networkSecurityConfig for specific domains if needed

**Clipboard Access Tool:**
- Risk: read_clipboard tool can read any clipboard content
- Files: `app/src/main/java/com/hh/agent/tools/ReadClipboardTool.java`
- Current mitigation: None - any app with this tool can read clipboard
- Recommendations: Add user confirmation before reading clipboard; consider if this tool should be available

**Mock IM Message Tool:**
- Risk: send_im_message tool is mock only but appears functional
- Files: `app/src/main/java/com/hh/agent/tools/SendImMessageTool.java`
- Current mitigation: None - returns fake success
- Recommendations: Implement actual IM integration or remove tool; add clear documentation that it's mock

**JNI Bridge Security:**
- Risk: JNI calls from Java to native code have no security boundaries
- Files: `agent/src/main/java/com/hh/agent/library/NativeAgent.java`
- Current mitigation: None
- Recommendations: Validate all input from JNI calls in native code; use JNI_CheckThread

---

## Performance Bottlenecks

**Single Thread Executor:**
- Problem: MainPresenter uses Executors.newSingleThreadExecutor() for all background work
- Files: `app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (line 35)
- Cause: Sequential processing of messages
- Improvement path: Use thread pool for parallel tool execution; consider coroutines

**No HTTP Connection Pooling:**
- Problem: Each HTTP request may create new connection
- Files: `agent/src/main/cpp/src/core/curl_http_client.cpp`
- Cause: CurlHttpClient creates new CURL handle per request (though reuses handle internally)
- Improvement path: Review curl_easy_reset behavior; consider adding keep-alive

**Unbounded History Loading:**
- Problem: load_history_from_memory loads all messages into vector
- Files: `agent/src/main/cpp/src/mobile_agent.cpp` (lines 79-102)
- Cause: No pagination; memory grows with conversation length
- Improvement path: Add configurable message limit; implement sliding window

**Missing Executor Shutdown:**
- Problem: executor.shutdown() called but no awaitTermination
- Files: `app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (line 168)
- Cause: Incomplete resource cleanup
- Improvement path: Use executor.shutdownNow() with awaitTermination in destroy()

---

## Fragile Areas

**JNI Callback Registration:**
- Why fragile: Static callback reference may become stale if class is unloaded
- Files: `agent/src/main/java/com/hh/agent/library/NativeAgent.java` (line 10)
- Safe modification: Ensure Activity context outlives native calls; consider WeakReference
- Test coverage: No tests for JNI lifecycle

**Activity Context in Tools:**
- Why fragile: Tools hold Activity context which becomes invalid after activity destruction
- Files: `app/src/main/java/com/hh/agent/AndroidToolManager.java` (lines 44-49)
- Safe modification: Use getApplicationContext() for tools that don't need Activity; null-check before use
- Test coverage: No runtime tests

**Native MobileAgentApi Singleton:**
- Why fragile: Singleton pattern with static instance; potential race conditions
- Files: `agent/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` (lines 19-35)
- Safe modification: Review synchronized blocks; add proper initialization guards
- Test coverage: No tests for concurrent access

**Config Fallback Behavior:**
- Why fragile: Silently falls back to defaults on config parse errors
- Files: `agent/src/main/cpp/src/config.cpp` (lines 309-323)
- Safe modification: Log warnings when falling back; surface errors to caller
- Test coverage: Limited

---

## Known Issues

**sendMessage Session Key Ignored:**
- Symptom: All messages go to "default" channel regardless of sessionKey parameter
- Files: `agent/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` (lines 103-132)
- Trigger: Call sendMessage with any sessionKey other than "default:*"
- Workaround: Pass sessionKey as "default:something" or fix createSession channel logic

**JSON Escaping in ReadClipboardTool:**
- Symptom: JSON may be malformed for certain clipboard content
- Files: `app/src/main/java/com/hh/agent/tools/ReadClipboardTool.java` (lines 54-61)
- Trigger: Clipboard contains special characters like backspace, bell, etc.
- Workaround: Use proper JSON library for escaping instead of manual replacement

---

## Scaling Limits

**Memory Management:**
- Current capacity: Configurable via memory_window (default 50 messages)
- Limit: Compaction triggers at context_window_tokens (default 128000)
- Scaling path: Increase context_window_tokens for longer conversations; implement summarization

**Tool Execution:**
- Current capacity: Sequential execution in single thread
- Limit: Tool calls block agent response
- Scaling path: Implement parallel tool execution with proper result aggregation

---

## Test Coverage Gaps

**Android UI Tests:**
- What's not tested: MainActivity, MessageAdapter UI interactions
- Files: `app/src/main/java/com/hh/agent/ui/MessageAdapter.java`, `app/src/main/java/com/hh/agent/MainActivity.java`
- Risk: UI crashes, adapter bugs go unnoticed
- Priority: High

**Native C++ Integration:**
- What's not tested: JNI bridge, native initialization, tool callback invocation
- Files: `agent/src/main/cpp/src/mobile_agent.cpp`, `agent/src/main/java/com/hh/agent/library/NativeAgent.java`
- Risk: Native crashes, memory leaks
- Priority: High

**Tool Implementations:**
- What's not tested: Tool execution, error handling, permission checks
- Files: `app/src/main/java/com/hh/agent/tools/*.java`
- Risk: Tool failures not handled gracefully
- Priority: Medium

---

*Concerns audit: 2026-03-09*
