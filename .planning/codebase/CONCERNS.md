# Codebase Concerns

**Analysis Date:** 2026-03-03

## Tech Debt

### Hardcoded Session Key
- Issue: Default constructor uses hardcoded `"http:default"` as session key
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (line 39)
- Impact: All users share the same session, messages from different users appear in same conversation
- Fix approach: Generate unique session key per user/session, or allow session key configuration

### In-Memory Session Storage
- Issue: Sessions stored in ConcurrentHashMap with no persistence
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java` (line 47)
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/impl/MockNanobotApi.java` (line 18)
- Impact: All chat history lost on app restart; unbounded memory growth with extended use
- Fix approach: Implement local persistence (Room database) or limit session cache size with LRU eviction

### HTTP Client Resource Leak
- Issue: OkHttpClient instance is created but never closed/managed properly
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java` (lines 42-45)
- Impact: Potential resource leak; connection pools not released
- Fix approach: Make HttpNanobotApi implement Closeable or use dependency injection for shared client

### Magic String for Thinking Role
- Issue: `"thinking"` role used as magic string without constants or enum
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/MainActivity.java` (line 109)
  - `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/ui/MessageAdapter.java` (lines 75, 111)
- Impact: Easy to introduce typos; inconsistent usage across codebase
- Fix approach: Add role constants to Message model or create ThinkingMessage subclass

### Missing Async Cancellation Support
- Issue: No way to cancel in-flight requests when user navigates away
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (lines 87-108, 140-161)
- Impact: User may see stale responses after leaving screen; wasted network bandwidth
- Fix approach: Store Future references and call cancel() in detachView()

### ExecutorService Graceful Shutdown
- Issue: executor.shutdown() called without waiting for tasks to complete
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (line 178)
- Impact: In-flight requests may be abruptly terminated
- Fix approach: Use shutdownNow() with timeout or awaitTermination()

### No HTTP Request Retry Logic
- Issue: Failed HTTP requests fail immediately without retry attempts
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java` (lines 69-113)
- Impact: Poor user experience on transient network failures
- Fix approach: Add OkHttp interceptor with retry logic or use Retrofit with retry mechanism


## Known Bugs

### Error Messages Displayed as Regular Chat Messages
- Symptoms: Network errors return Message with role "assistant" and content starting with "Error:"
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java` (lines 116-121)
- Trigger: When HTTP request fails or returns non-success status
- Workaround: None - errors appear as bot responses

### Empty Session Returns Empty Array Without Auto-Create
- Symptoms: getHistory() returns empty list when session doesn't exist instead of creating one
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java` (line 127-130)
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/impl/MockNanobotApi.java` (lines 70-74)
- Workaround: Caller must ensure session exists before calling getHistory()

### Multiple Loading State Calls
- Symptoms: Both showLoading() and showThinking() called sequentially in sendMessage()
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (lines 131-138)
- Trigger: Every time user sends a message

## Security Considerations

### Cleartext Traffic Allowed to Localhost
- Risk: Network security config permits cleartext HTTP to localhost/10.0.2.2
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/res/xml/network_security_config.xml`
- Current mitigation: Restricted to localhost only
- Recommendations: Consider removing in production; use HTTPS with self-signed cert if local dev needed

### Hardcoded Base URL
- Risk: Server endpoint hardcoded in NanobotConfig
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/config/NanobotConfig.java` (line 13)
- Recommendations: Move to BuildConfig or remote config; add environment-based URL selection

### No Authentication
- Risk: No authentication mechanism for API calls
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java`
- Recommendations: Add API key header or OAuth token support

### No Input Sanitization
- Risk: User content sent directly to API without sanitization
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (line 123)
- Recommendations: Add basic XSS prevention for displayed content

## Performance Bottlenecks

### Single Thread Executor for Network Operations
- Problem: All HTTP requests queued on single thread
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (line 50)
- Cause: newSingleThreadExecutor() limits concurrency
- Improvement path: Use thread pool (newFixedThreadPool) or OkHttp's internal async mechanisms

### Unbounded Session Cache Growth
- Problem: Sessions accumulate indefinitely in memory
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java` (line 47)
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/impl/MockNanobotApi.java` (line 18)
- Cause: No eviction policy; no size limits
- Improvement path: Implement LRU cache or periodic cleanup of old sessions

### RecyclerView notifyDataSetChanged on Every Update
- Problem: Full list re-render instead of incremental updates
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/ui/MessageAdapter.java` (line 94)
- Improvement path: Use DiffUtil for efficient updates

## Fragile Areas

### View Null Checks Throughout Presenter
- Why fragile: 12+ null checks on view field; easy to miss one and get NPE
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java`
- Safe modification: Extract view calls to helper method with null check
- Test coverage: None for view detachment timing

### String Comparison for Role Checking
- Why fragile: Uses string literals instead of constants
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/ui/MessageAdapter.java` (lines 75, 77, 111, 151)
- Safe modification: Use Message.ROLE_USER constants
- Test coverage: No unit tests for role logic

### Broad Exception Catching
- Why fragile: Catches Exception without specific handling
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java` (lines 100, 152)
- Safe modification: Catch specific exceptions (IOException, TimeoutException)

## Scaling Limits

### In-Memory Sessions:
- Current capacity: Limited by device RAM
- Limit: Thousands of messages per session; dozens of sessions before OOM
- Scaling path: Move to Room database for persistence

### Network Connection:
- Current capacity: Single connection per OkHttpClient instance
- Limit: Connection pool limits; no concurrent request handling
- Scaling path: Increase connection pool size; implement request queuing

## Dependencies at Risk

### Markwon 4.6.2 (End-of-Life)
- Risk: Last release was 2022; may have unfixed vulnerabilities
- Impact: Security vulnerabilities may never be patched
- Migration plan: Consider migrating to markdown-it.js for web or Jetpack Compose Markdown

### OkHttp 4.12.0
- Risk: Stable but may need updates for newer Android versions
- Impact: Compatibility issues with Android 14+ background restrictions
- Migration plan: Keep updated; monitor Android release notes

### Gson 2.10.1
- Risk: Adequate for current needs
- Impact: Low risk
- Migration plan: Consider Kotlinx Serialization for new code

## Missing Critical Features

### Message Persistence
- Problem: No local storage for messages
- Blocks: Offline reading; message search; multi-device sync

### Real-Time Updates
- Problem: Polling or request-response only
- Blocks: Push notifications; live collaboration features

### User Authentication
- Problem: No user identity system
- Blocks: Personalization; secure multi-user support

### Error Recovery
- Problem: No automatic retry or offline queue
- Blocks: Reliable message delivery on poor networks

## Test Coverage Gaps

### Presenter Lifecycle Tests
- What's not tested: View attachment/detachment timing; presenter destruction during async operation
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/presenter/MainPresenter.java`
- Risk: Race conditions may cause crashes or memory leaks
- Priority: High

### Session Management Tests
- What's not tested: Concurrent session access; session cleanup; max session limits
- Files:
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java`
  - `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/impl/MockNanobotApi.java`
- Risk: Thread safety issues may cause data corruption
- Priority: High

### Error Handling Tests
- What's not tested: Network timeouts; malformed responses; service unavailable
- Files: `/Users/caixiao/Workspace/projects/mobile-agent/lib/src/main/java/com/hh/agent/lib/http/HttpNanobotApi.java`
- Risk: Unhandled edge cases cause crashes
- Priority: Medium

---

*Concerns audit: 2026-03-03*
