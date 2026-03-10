# Android 工具扩展指南

只需 3 步添加新的 Android 工具。

> **提示：** 如果需要添加 Skill（复杂工作流程），请参考 [Android Skill 扩展指南](./android-skill-extension.md)

## 步骤 1: 创建 Tool 类

在 `app/src/main/java/com/hh/agent/tool/` 下创建新类：

```java
package com.hh.agent.tool;

import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

public class MyTool implements ToolExecutor {

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

    @Override
    public String getDescription() {
        return "工具描述";
    }

    @Override
    public String getArgsDescription() {
        return "param1: 参数1说明, param2: 参数2说明";
    }

    @Override
    public String getArgsSchema() {
        return "{\"type\":\"object\",\"properties\":{\"param1\":{\"type\":\"string\",\"description\":\"参数1说明\"}},\"required\":[\"param1\"]}";
    }
}
```

**注意：** v2.2 之后，Tool 类放在 app 层而非 agent-android 层，这样可以支持运行时动态注册。

**返回格式约定：**
- 成功: `{"success": true, "result": "..."}`
- 失败: `{"success": false, "error": "error_code", "message": "..."}`

## 步骤 2: 注册 Tool

在 `app/src/main/java/com/hh/agent/LauncherActivity.java` 的 `initializeToolManager()` 方法中添加注册：

```java
private void initializeToolManager() {
    // 创建 AndroidToolManager 实例
    toolManager = new AndroidToolManager(this);

    // 注册自定义 Tool
    toolManager.registerTool(new MyTool());

    // 初始化 AndroidToolManager
    toolManager.initialize();
}
```

**注册方式说明：**
- 使用 `toolManager.registerTool(ToolExecutor)` 方法注册
- 支持运行时动态注册（在应用运行期间添加新 Tool）
- Tool 注册后会主动推送给 Agent，LLM 可以感知到新工具

## 步骤 3: 运行测试

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 步骤 4: 添加 Skill（可选）

如果你想让 Agent 执行更复杂的工作流程，可以添加 Skill。

详细说明请参考 [Android Skill 扩展指南](./android-skill-extension.md)

## 现有工具参考

| 工具 | 文件 | 说明 |
|------|------|------|
| show_toast | ShowToastTool.java | 显示 Toast 通知 |
| display_notification | DisplayNotificationTool.java | 显示系统通知 |
| read_clipboard | ReadClipboardTool.java | 读取剪贴板内容 |
| take_screenshot | TakeScreenshotTool.java | 截取屏幕截图 |
| search_contacts | SearchContactsTool.java | 搜索联系人 |
| send_im_message | SendImMessageTool.java | 发送即时消息 |

**工具文件位置：** `app/src/main/java/com/hh/agent/tool/`
