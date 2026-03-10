# Coding Conventions

**Analysis Date:** 2026-03-10

## Naming Patterns

**Files:**
- Classes: PascalCase (e.g., `MainPresenter.java`, `MessageAdapter.java`, `AndroidToolManager.java`)
- Interfaces: PascalCase (e.g., `MainContract.java`, `MobileAgentApi.java`)
- Tests: `*Test.java` suffix (e.g., `MainPresenterTest.java`)

**Packages:**
- All lowercase (e.g., `com.hh.agent.android.tool`, `com.hh.agent.library.model`)

**Functions:**
- camelCase (e.g., `loadMessages()`, `sendMessage()`, `getName()`, `execute()`)
- Action verbs for methods that perform operations (e.g., `initialize()`, `registerTool()`)

**Variables:**
- camelCase (e.g., `mobileAgentApi`, `executor`, `mainHandler`, `sessionKey`)
- Private fields also use camelCase (no Hungarian notation)

**Types:**
- Classes: PascalCase (e.g., `Message`, `Session`, `Context`)
- Interfaces: PascalCase ending with noun (e.g., `ToolExecutor`, `AndroidToolCallback`)
- Interfaces for MVP contracts: `<Feature>Contract` (e.g., `MainContract`)

## Code Style

**Formatting:**
- Uses standard Java formatting with 4-space indentation
- K&R brace style (opening brace on same line)
- Line length: No explicit limit enforced

**Linting:**
- No explicit linting configuration found
- Relies on Android Studio's built-in inspections

**Java Version:**
- Source Compatibility: Java 21
- Target Compatibility: Java 21

**Android Configuration:**
- compileSdk: 34
- minSdk: 24
- targetSdk: 31

## Import Organization

**Order (Standard Java):**
1. `java.*` imports
2. `android.*` imports
3. Third-party imports (e.g., `org.json.*`, `junit.*`)
4. Project imports

**Example from `MainPresenter.java`:**
```java
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.presenter.NativeMobileAgentApiAdapter;
import com.hh.agent.library.api.MobileAgentApi;
import com.hh.agent.library.model.Message;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
```

## Error Handling

**Patterns:**

1. **Tool Execution:**
   - Returns JSON error objects as strings:
   ```java
   return "{\"success\": false, \"error\": \"missing_required_param\", \"param\": \"message\"}";
   ```

2. **Initialization Failures:**
   - Throws `RuntimeException`:
   ```java
   throw new RuntimeException("Failed to initialize Native API: " + e.getMessage(), e);
   ```

3. **Validation Errors:**
   - Throws `IllegalArgumentException`:
   ```java
   throw new IllegalArgumentException("ToolExecutor cannot be null");
   ```

4. **Exception Handling in Async:**
   - Catches exception and notifies UI via callback:
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

**Framework:** Android Log class

**Patterns:**
- Info logs: `Log.i(tag, message)`
- Error logs: `Log.e(tag, message)`

**Example from `AndroidToolManager.java`:**
```java
Log.i("AndroidToolManager", "Initializing AndroidToolManager");
Log.i("AndroidToolManager", "Registered tool: " + toolName);
Log.e("AndroidToolManager", "Failed to generate tools.json: " + e.getMessage());
```

## Comments

**Language:** Chinese (per CLAUDE.md convention)

**When to Comment:**
- Javadoc on public APIs and classes
- Explanatory comments for complex logic
- TODO comments for incomplete features

**Javadoc Format:**
```java
/**
 * MainActivity 的 MVP 契约接口
 */
public interface MainContract {
    /**
     * 加载历史消息
     */
    void loadMessages();
}
```

**Inline Comments:**
```java
// 初始化 AndroidToolManager 并注册内置 Tool
initializeToolManager();

// 跳转到 agent-android 的 AgentActivity
Intent intent = new Intent(this, AgentActivity.class);
```

## Function Design

**Size:** No strict limit, but prefer single-responsibility methods

**Parameters:**
- Simple types passed directly
- Context and callbacks as needed
- Named parameters in Javadoc

**Return Values:**
- Return `null` for not found cases
- Return empty collections for empty lists: `return new ArrayList<>()`
- Return JSON strings for tool execution results

## Module Design

**Architecture:** MVP (Model-View-Presenter)

**Contract Pattern:**
```java
public interface MainContract {
    interface View {
        void onMessagesLoaded(List<Message> messages);
        void onError(String error);
    }

    interface Presenter {
        void loadMessages();
        void attachView(View view);
    }
}
```

**Tool Pattern:**
```java
public class ShowToastTool implements ToolExecutor {
    @Override
    public String getName() { return "show_toast"; }

    @Override
    public String execute(JSONObject args) { /* ... */ }

    @Override
    public String getDescription() { return "显示 Toast 消息"; }

    @Override
    public String getArgsSchema() { return "{\"type\":\"object\",...}"; }
}
```

**Exports:**
- Public classes exposed directly
- Interfaces for abstractions (e.g., `MobileAgentApi`)
- Factory methods where object creation is complex

---

*Convention analysis: 2026-03-10*
