# Testing Patterns

**Analysis Date:** 2026-03-12

## Test Framework

**Runner:**
- JUnit 4 (version 4.13.2)
- Config: Defined in `app/build.gradle` with `testImplementation 'junit:junit:4.13.2'`

**Assertion Library:**
- JUnit 4 assertions (`org.junit.Assert`)

**Run Commands:**
```bash
# Run all unit tests (via Gradle)
./gradlew test

# Run tests for specific module
./gradlew :app:test
```

## Test File Organization

**Location:**
- Co-located with source modules: `app/src/test/java/com/hh/agent/`
- Same package structure as source

**Naming:**
- Test class: `<SourceClass>Test.java` (e.g., `MainPresenterTest.java`)
- Test method: `test<MethodName>()` or descriptive Chinese names

**Structure:**
```
app/src/test/java/com/hh/agent/
├── contract/
│   └── MainContractTest.java
├── presenter/
│   └── MainPresenterTest.java
└── ui/
    └── MessageAdapterTest.java
```

## Test Structure

**Suite Organization:**
```java
package com.hh.agent.presenter;

import com.hh.agent.contract.MainContract;
import com.hh.agent.library.api.MobileAgentApi;
import com.hh.agent.library.model.Message;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/**
 * MainPresenter 的单元测试
 */
public class MainPresenterTest {

    @Test
    public void testApiTypeEnum() {
        // Test implementation
        assertNotNull(MainPresenter.class);
    }
}
```

**Patterns:**
- `@Test` annotation for each test method
- `assertNotNull()`, `assertEquals()`, `assertTrue()` for assertions
- Descriptive Chinese comments for test purpose

## Mocking

**Framework:** Manual mocking (no Mockito dependency currently)

**Patterns:**
```java
// Manual mock implementation
MainContract.View mockView = new MainContract.View() {
    @Override
    public void onMessagesLoaded(List<Message> messages) {
        receivedMessages.addAll(messages);
    }

    @Override
    public void onMessageReceived(Message message) {
        receivedMessages.add(message);
    }

    @Override
    public void onError(String error) {
        // Mock implementation
    }

    @Override
    public void showLoading() {
        // Mock implementation
    }

    @Override
    public void hideLoading() {
        // Mock implementation
    }
};
```

**What to Mock:**
- View interfaces in MVP pattern
- API interfaces (`MobileAgentApi`)
- Data models with test data

**What NOT to Mock:**
- Simple POJOs (use real objects)
- Well-tested utility classes

## Fixtures and Factories

**Test Data:**
```java
// Simple fixture creation
Message testMsg = new Message();
testMsg.setId("1");
testMsg.setRole("user");
testMsg.setContent("Test");

List<Message> messages = new ArrayList<>();
messages.add(msg1);
messages.add(msg2);
```

**Location:**
- Tests create fixtures inline within test methods
- No dedicated test fixtures directory

## Coverage

**Requirements:** None enforced

**View Coverage:**
```bash
./gradlew test --info  # Run with verbose output
```

## Test Types

**Unit Tests:**
- Focus on Contract interfaces and Presenter logic
- Mock View interface implementations
- Test data model creation
- Verify callback behaviors

**Integration Tests:**
- Not detected in current codebase

**E2E Tests:**
- Not used (Android instrumentation tests not present)

## Common Patterns

**Async Testing Note:**
- MainPresenter uses Handler/Looper for main thread callbacks
- Tests note: "在非 Android 环境下，Handler/Looper 不可用，所以这里只验证类结构"

```java
@Test
public void testPresenterCreation() {
    // Note: In non-Android environment, Handler/Looper is unavailable
    // So we only verify class structure here
    assertNotNull(MainPresenter.class);
}
```

**Error Testing:**
```java
@Test
public void testMockView() {
    // Test that mock view can receive and store messages
    final List<Message> receivedMessages = new ArrayList<>();

    MainContract.View mockView = new MainContract.View() {
        // ... implementation
    };

    Message testMsg = new Message();
    testMsg.setContent("Test");
    mockView.onMessageReceived(testMsg);

    assertEquals(1, receivedMessages.size());
}
```

## Testing Limitations

**Current State:**
- Limited test coverage
- Only 3 test files in `app/src/test/java`
- No Android instrumentation tests
- No Robolectric tests
- Manual mocking without mockito

**Recommended Improvements:**
- Add Mockito for easier mocking
- Add Robolectric for Android component testing
- Add instrumented tests for UI components
- Increase test coverage for presenter logic

---

*Testing analysis: 2026-03-12*
