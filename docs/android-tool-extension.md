# Android 工具扩展指南

本文档说明如何在 Mobile Agent 中添加新的 Android 工具。

## 架构概览

```
LLM (Nanobot)
    ↓ call_android_tool{function: "xxx", args: {...}}
C++ tool_registry
    ↓ JNI nativeCallAndroidTool()
Java NativeAgent
    ↓ AndroidToolManager.callTool()
Java ToolExecutor (your implementation)
    ↓
Android System APIs
```

## 扩展步骤

### 步骤 1: 创建 ToolExecutor 实现类

在 `agent/src/main/java/com/hh/agent/library/tools/` 目录下创建新类：

```java
package com.hh.agent.library.tools;

import android.content.Context;
import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

/**
 * MyTool description.
 */
public class MyTool implements ToolExecutor {

    private final Context context;

    public MyTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "my_tool";  // 工具名称，用于 function 路由
    }

    @Override
    public String execute(JSONObject args) {
        try {
            // 1. 解析参数
            String param1 = args.optString("param1", "default_value");

            // 2. 调用 Android API
            // ... your implementation

            // 3. 返回成功结果
            return "{\"success\": true, \"result\": \"...\"}";

        } catch (Exception e) {
            // 4. 返回错误结果
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

**重要约定：**
- 返回格式必须是 JSON 字符串
- 成功: `{"success": true, "result": "...", ...}`
- 失败: `{"success": false, "error": "error_code", "message": "..."}`

### 步骤 2: 在 AndroidToolManager 中注册

编辑 `agent/src/main/java/com/hh/agent/library/AndroidToolManager.java`：

在 `initialize()` 方法中添加注册：

```java
public void initialize() {
    // ... existing tools

    // 添加新工具
    tools.put("my_tool", new MyTool(getActivity()));

    Log.i("AndroidToolManager", "Registered N tools");
}
```

### 步骤 3: 更新 tools.json

编辑 `app/src/main/assets/tools.json`：

在 `call_android_tool` 的 function enum 中添加新工具：

```json
{
  "name": "call_android_tool",
  "description": "调用 Android 设备功能...",
  "parameters": {
    "type": "object",
    "properties": {
      "function": {
        "type": "string",
        "description": "要调用的功能名称",
        "enum": [
          "display_notification",
          "show_toast",
          "read_clipboard",
          "take_screenshot",
          "my_tool"  // ← 添加新工具
        ]
      },
      "args": {
        "type": "object",
        "description": "功能参数"
      }
    },
    "required": ["function", "args"]
  }
}
```

### 步骤 4: 添加 Android 权限（如需要）

如果新工具需要特殊权限，在 `app/src/main/AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.YOUR_PERMISSION" />
```

## 示例：添加 VibrateTool

### 1. 创建 VibrateTool.java

```java
package com.hh.agent.library.tools;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import com.hh.agent.library.ToolExecutor;
import org.json.JSONObject;

public class VibrateTool implements ToolExecutor {

    private final Context context;

    public VibrateTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "vibrate";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            int duration = args.optInt("duration", 100); // 默认 100ms
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator == null || !vibrator.hasVibrator()) {
                return "{\"success\": false, \"error\": \"vibrator_unavailable\"}";
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }

            return "{\"success\": true, \"result\": \"vibrated\", \"duration\": " + duration + "}";

        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}
```

### 2. 注册工具

在 `AndroidToolManager.initialize()` 中添加：

```java
tools.put("vibrate", new VibrateTool(getActivity()));
```

### 3. 更新 tools.json

```json
"enum": ["display_notification", "show_toast", "read_clipboard", "take_screenshot", "vibrate"]
```

## 测试

1. 构建: `./gradlew assembleDebug`
2. 安装: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. 测试: 向 Nanobot 发送 "请震动 200 毫秒"
4. 查看日志: `adb logcat | grep -i "AndroidToolManager\|callTool"`

## 注意事项

1. **上下文获取**: 使用 `getActivity()` 获取 Activity 上下文
2. **权限检查**: 运行时权限需要额外处理
3. **线程安全**: Android API 调用需在主线程或正确线程执行
4. **错误处理**: 始终返回 JSON 格式的错误信息

## 现有工具参考

| 工具 | 文件 | Android API |
|------|------|-------------|
| show_toast | ShowToastTool.java | Toast.makeText() |
| display_notification | DisplayNotificationTool.java | NotificationManager |
| read_clipboard | ReadClipboardTool.java | ClipboardManager |
| take_screenshot | TakeScreenshotTool.java | View.drawToBitmap() + MediaStore |
