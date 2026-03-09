# Coding Conventions

**Analysis Date:** 2026-03-09

## Naming Patterns

**Files:**
- Java classes use PascalCase: `MainActivity.java`, `MessageAdapter.java`, `MainPresenter.java`
- Interface files use PascalCase with suffix `Interface` when needed, or just the interface name: `MainContract.java` (contract pattern)
- Test files use suffix `Test.java`: `MessageAdapterTest.java`, `MainPresenterTest.java`

**Functions:**
- Methods use camelCase: `loadMessages()`, `sendMessage()`, `onMessagesLoaded()`
- Private methods follow same convention: `initViews()`, `createApi()`, `loadToolsConfig()`
- Interface method names describe action: `onMessagesLoaded()`, `onError()`, `showThinking()`

**Variables:**
- Instance variables use camelCase with optional prefix `m` (not consistently used):
  - With prefix: `rvMessages`, `etMessage`, `btnSend`, `toolbar`
  - Without prefix: `view`, `mobileAgentApi`, `executor`, `sessionKey`
- Local variables use camelCase: `content`, `messages`, `userMessage`
- Constants use UPPER_SNAKE_CASE: `VIEW_TYPE_USER = 0`, `VIEW_TYPE_ASSISTANT = 1`

**Types:**
- Classes use PascalCase: `Message`, `Session`, `MainPresenter`, `AndroidToolManager`
- Interfaces use PascalCase: `MainContract`, `MobileAgentApi`, `ToolExecutor`
- Packages use lowercase: `com.hh.agent.ui`, `com.hh.agent.presenter`, `com.hh.agent.tools`

## Code Style

**Formatting:**
- No explicit formatting tool configured (no Checkstyle, Spotless, or Google Java Format)
- Uses standard Android Studio/Java conventions
- 4-space indentation for Java code
- Line length typically under 120 characters

**Linting:**
- Android Lint enabled by default in Android Gradle Plugin
- No explicit .editorconfig or lint rules file found
- No FindBugs/ErrorProne configured

**Braces:**
- Opening brace on same line: `public class MainPresenter {`
- Control statements use braces even for single statements

## Import Organization

**Order:**
1. Android framework imports: `android.os.*`, `android.widget.*`, `androidx.*`
2. Java standard library: `java.util.*`, `java.io.*`
3. Third-party libraries: `com.hh.agent.*`, `io.noties.markwon.*`
4. Project-local imports: `com.hh.agent.*`

**Example from `MainActivity.java`:**
```java
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.contract.MainContract;
import com.hh.agent.library.model.Message;
import com.hh.agent.presenter.MainPresenter;
import com.hh.agent.presenter.NativeMobileAgentApiAdapter;
import com.hh.agent.ui.MessageAdapter;
import java.util.List;
```

## Error Handling

**Patterns:**
- Try-catch blocks with generic `Exception` handling
- Errors returned as JSON strings from tool executors
- UI errors shown via `view.onError()` callback
- Presenter catches exceptions and propagates via View callbacks

**Tool Executor Error Pattern:**
```java
// From ShowToastTool.java
try {
    // implementation
    return "{\"success\": true, \"result\": \"toast_shown\"}";
} catch (Exception e) {
    return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
}
```

**Presenter Error Pattern:**
```java
// From MainPresenter.java
try {
    List<Message> messages = mobileAgentApi.getHistory(sessionKey, 50);
    if (view != null) {
        mainHandler.post(() -> {
            view.hideLoading();
            view.onMessagesLoaded(messages);
        });
    }
} catch (Exception e) {
    if (view != null) {
        mainHandler.post(() -> {
            view.hideLoading();
            view.onError("加载消息失败: " + e.getMessage());
        });
    }
}
```

## Logging

**Framework:** Android `Log` class

**Patterns:**
- Use `Log.i()` for info, `Log.e()` for errors
- Tag is class name: `Log.i("AndroidToolManager", "Initializing AndroidToolManager")`
- Log messages in Chinese or English (Chinese preferred in this codebase)

**Example from `AndroidToolManager.java`:**
```java
Log.i("AndroidToolManager", "Initializing AndroidToolManager");
Log.i("AndroidToolManager", "Registered 6 tools: show_toast, display_notification, read_clipboard, take_screenshot, search_contacts, send_im_message");
```

## Comments

**When to Comment:**
- Public API methods always have Javadoc: `@param`, `@return`
- Class-level documentation for public classes
- Inline comments for complex logic or workarounds
- No comments for trivial getter/setter code

**Javadoc Example from `MainContract.java`:**
```java
/**
 * 加载历史消息
 */
void loadMessages();
```

**Class Javadoc Example from `MessageAdapter.java`:**
```java
/**
 * 消息列表的 RecyclerView 适配器
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
```

## Function Design

**Size:** Functions are typically 10-50 lines. Complex functions are broken down into smaller private methods.

**Parameters:**
- Context parameters when Android context is needed
- Single responsibility: each method does one thing
- Input validation at method start

**Return Values:**
- Void for View callbacks
- Return values for API methods
- JSON strings for tool executor results
- Collections for list data

## Module Design

**MVP Pattern Structure:**
```
app/src/main/java/com/hh/agent/
├── contract/
│   └── MainContract.java    # View and Presenter interfaces
├── presenter/
│   └── MainPresenter.java   # Business logic implementation
├── ui/
│   └── MessageAdapter.java  # RecyclerView adapter
├── tools/                   # Tool implementations
└── MainActivity.java        # View implementation
```

**Exports:**
- Public classes: MainActivity, MainPresenter, MessageAdapter, AndroidToolManager
- Package-private: Contract interfaces, tool classes
- No explicit visibility modifiers default to package-private

**Tool Pattern:**
Each tool implements `ToolExecutor` interface:
- `getName()` - returns tool identifier string
- `execute(JSONObject args)` - executes with JSON parameters, returns JSON result string

---

*Convention analysis: 2026-03-09*
