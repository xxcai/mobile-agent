# Android 工具扩展指南

只需 3 步添加新的 Android 工具。

## 步骤 1: 创建 Tool 类

在 `agent-android/src/main/java/com/hh/agent/android/tool/` 下创建新类：

```java
package com.hh.agent.android.tool;

import android.content.Context;
import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

public class MyTool implements ToolExecutor {

    private final Context context;

    public MyTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "my_tool";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            // 你的实现逻辑
            return "{\"success\": true, \"result\": \"done\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

**返回格式约定：**
- 成功: `{"success": true, "result": "..."}`
- 失败: `{"success": false, "error": "error_code", "message": "..."}`

## 步骤 2: 注册工具

在 `AndroidToolManager.initialize()` 中添加：

```java
tools.put("my_tool", new MyTool(getActivity()));
```

## 步骤 3: 运行测试

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 现有工具参考

| 工具 | 文件 |
|------|------|
| show_toast | ShowToastTool.java |
| display_notification | DisplayNotificationTool.java |
| read_clipboard | ReadClipboardTool.java |
| take_screenshot | TakeScreenshotTool.java |
| search_contacts | SearchContactsTool.java |
| send_im_message | SendImMessageTool.java |
