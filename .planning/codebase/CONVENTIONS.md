# Coding Conventions

**Analysis Date:** 2026-03-12

## Naming Patterns

**Files:**
- Java classes: PascalCase (e.g., `MainPresenter.java`, `MessageAdapter.java`)
- Interfaces: PascalCase with common suffixes (e.g., `ToolExecutor`, `IVoiceRecognizer`)
- Test classes: Same as source with `Test` suffix (e.g., `MainPresenterTest.java`)

**Functions:**
- Methods: camelCase (e.g., `loadMessages()`, `sendMessage()`, `attachView()`)
- Interface method names: Descriptive with standard prefixes (e.g., `getName()`, `execute()`, `getDescription()`)

**Variables:**
- Instance variables: camelCase (e.g., `mobileAgentApi`, `mainHandler`, `sessionKey`)
- Local variables: camelCase (e.g., `receivedMessages`, `testMsg`)
- Constants: UPPER_SNAKE_CASE (e.g., `VIEW_TYPE_USER`, `VIEW_TYPE_ASSISTANT`)

**Types:**
- Classes: PascalCase (e.g., `Message`, `Session`, `MainContract`)
- Interfaces: PascalCase (e.g., `ToolExecutor`, `MobileAgentApi`)
- Enums: Not used in current codebase

## Code Style

**Formatting:**
- Indentation: 4 spaces
- Braces: Same-line opening brace (K&R style)
- Line length: Not explicitly enforced
- No specific formatter config file found

**Linting:**
- No checkstyle, PMD, or Android lint configuration files detected
- Android Lint is implicit via Android Gradle Plugin 8.3.2

**Java Version:**
- Source compatibility: Java 21
- Target compatibility: Java 21

## Import Organization

**Order:**
1. Android framework imports (`android.*`)
2. Third-party library imports (`androidx.*`, `io.noties.*`)
3. Project imports (`com.hh.agent.*`)
4. Java standard library (`java.*`, `org.json.*`)

**Example:**
```java
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.library.model.Message;
import java.util.List;
import java.util.concurrent.ExecutorService;
```

**Path Aliases:**
- No path aliases configured (no .editorconfig or import order rules)

## Error Handling

**Patterns:**
- Exceptions caught and handled with try-catch blocks
- Error messages returned as JSON strings in tool execution results
- UI errors passed to View via callback methods (e.g., `onError(String error)`)
- RuntimeExceptions thrown for critical initialization failures

**Tool Error Response Pattern:**
```java
try {
    // execution logic
    return "{\"success\": true, \"result\": ...}";
} catch (Exception e) {
    return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
}
```

**Presenter Error Handling:**
```java
catch (Exception e) {
    if (view != null) {
        mainHandler.post(() -> {
            view.hideLoading();
            view.onError("发送消息失败: " + e.getMessage());
        });
    }
}
```

## Logging

**Framework:** Android Log API

**Usage:**
- `Log.d()` - Debug logs
- `Log.i()` - Info logs
- `Log.e()` - Error logs

**Patterns:**
```java
private static final String TAG = "ClassName";
Log.d(TAG, "methodName: description");
Log.i("AndroidToolManager", "Initializing AndroidToolManager");
Log.e(TAG, "Failed to initialize workspace from assets");
```

**When to Log:**
- Initialization events
- Registration/unregistration of tools
- Error conditions with context

## Comments

**When to Comment:**
- Class-level Javadoc for public APIs and contracts
- Method Javadoc for interface implementations
- Implementation comments for complex logic
- Mock data explanations

**Javadoc Usage:**
```java
/**
 * MainActivity 的 MVP 契约接口
 */
public interface MainContract {}

/**
 * 加载历史消息
 */
void loadMessages();
```

**Implementation Comments:**
```java
// 创建用户消息
Message userMessage = new Message();

// 先通知 View 显示用户消息
if (view != null) { ... }
```

## Function Design

**Size:**
- Methods typically under 50 lines
- Complex methods separated into private helper methods

**Parameters:**
- Context parameters for Android-specific operations
- Single responsibility - one method does one thing

**Return Values:**
- void for View callbacks
- String (JSON) for tool execution results
- Object/Collection for data queries

## Module Design

**Architecture Pattern:** MVP (Model-View-Presenter)

**Exports:**
- Public classes explicitly defined
- No explicit package-info.java or barrel files

**Directory Structure:**
- `contract/` - MVP Contract interfaces
- `presenter/` - Presenter implementations
- `ui/` - UI components (Adapters, Activities)
- `tool/` - Tool implementations
- `model/` - Data models
- `api/` - API interfaces and adapters
- `library/` - Core library classes
- `floating/` - Floating ball feature

---

*Convention analysis: 2026-03-12*
