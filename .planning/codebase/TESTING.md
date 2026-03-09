# Testing Patterns

**Analysis Date:** 2026-03-09

## Test Framework

**Runner:**
- JUnit 4.13.2
- Config: Defined in `app/build.gradle` with `testImplementation 'junit:junit:4.13.2'`

**Assertion Library:**
- Standard JUnit assertions: `org.junit.Assert.*`

**Run Commands:**
```bash
./gradlew test                    # Run all unit tests
./gradlew testDebugUnitTest       # Run debug variant tests
```

## Test File Organization

**Location:**
- Tests co-located in `app/src/test/java/` directory
- Mirror source package structure: `com/hh/agent/ui/MessageAdapterTest.java` tests `com/hh/agent/ui/MessageAdapter.java`

**Naming:**
- Test class uses suffix `Test`: `MessageAdapterTest`, `MainContractTest`, `MainPresenterTest`
- Test methods use prefix `test` or descriptive names: `testSetMessages()`, `testMessageRoles()`

**Structure:**
```
app/src/test/java/com/hh/agent/
├── ui/
│   └── MessageAdapterTest.java
├── contract/
│   └── MainContractTest.java
└── presenter/
    └── MainPresenterTest.java
```

## Test Structure

**Suite Organization:**
```java
// From MessageAdapterTest.java
public class MessageAdapterTest {

    @Test
    public void testSetMessages() {
        // Test implementation
        assertEquals(2, messages.size());
    }

    @Test
    public void testMessageRoles() {
        // Test implementation
        assertEquals("user", userMsg.getRole());
    }
}
```

**Patterns:**
- Each `@Test` method tests a single behavior
- Setup in test method (no `@Before` setup methods found)
- Teardown not needed for simple unit tests

**Assertion Pattern:**
```java
// Direct assertions using JUnit
assertEquals(expected, actual);
assertNotNull(object);
assertTrue(condition);
assertFalse(condition);
```

## Mocking

**Framework:** No mocking framework (Mockito, etc.) detected

**Patterns:**
- Manual anonymous class implementations for interface mocking
- Simple inline mock objects created in test methods

**Example from `MainContractTest.java`:**
```java
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
        // empty implementation
    }

    @Override
    public void showLoading() {
    }

    @Override
    public void hideLoading() {
    }
};

// Verify Mock View works
Message testMsg = new Message();
testMsg.setContent("Test");
mockView.onMessageReceived(testMsg);
assertEquals(1, receivedMessages.size());
```

**What to Mock:**
- View interfaces in MVP pattern tests
- API interfaces for presenter tests

**What NOT to Mock:**
- Model classes (Message, Session) - tested directly
- Simple data objects without dependencies

## Fixtures and Factories

**Test Data:**
```java
// Inline fixture creation from MessageAdapterTest.java
List<Message> messages = new ArrayList<>();
Message msg1 = new Message();
msg1.setId("1");
msg1.setRole("user");
msg1.setContent("Hello");

Message msg2 = new Message();
msg2.setId("2");
msg2.setRole("assistant");
msg2.setContent("Hi there");

messages.add(msg1);
messages.add(msg2);
```

**Location:**
- Fixtures created inline in test methods
- No separate fixture files found in codebase

## Coverage

**Requirements:** None enforced

**View Coverage:**
```bash
./gradlew testDebugUnitTestCoverage  # If configured
# Or manually via Android Studio
```

## Test Types

**Unit Tests:**
- Test individual classes in isolation
- Use manual mocks for dependencies
- Focus on model classes and MVP contracts

**Integration Tests:**
- Not detected in this codebase
- Android instrumentation tests not present

**E2E Tests:**
- Not used in this project

## Common Patterns

**Async Testing:**
- Not commonly used in current tests
- Presenters use `Handler` and `ExecutorService` for threading
- Tests verify class structure rather than runtime behavior

**Error Testing:**
- Not extensively tested
- Error handling tested via View callback verification

**View Interface Testing:**
- Tests verify View interface methods can be called
- Uses anonymous class implementation to simulate View

---

*Testing analysis: 2026-03-09*
