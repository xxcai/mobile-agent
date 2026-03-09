# Android 工具扩展指南

本文档说明如何在 Mobile Agent 中添加新的 Android 工具。

## 模块架构

Mobile Agent 采用三层模块化架构：

```
┌─────────────────────────────────────────────┐
│  app (演示壳)                                │
│  - MainActivity (UI 入口)                   │
│  - 配置加载与初始化绑定                       │
└─────────────────┬───────────────────────────┘
                  ↓ 依赖
┌─────────────────────────────────────────────┐
│  agent-android (Android 适配层)              │
│  - AndroidToolManager (工具管理)             │
│  - WorkspaceManager (工作空间)               │
│  - Android Tools (实现)                      │
└─────────────────┬───────────────────────────┘
                  ↓ 依赖
┌─────────────────────────────────────────────┐
│  agent-core (核心库)                          │
│  - C++ JNI 绑定                              │
│  - ToolExecutor 接口                        │
└─────────────────────────────────────────────┘
```

## 调用链路

```
LLM (Nanobot)
    ↓ call_android_tool{function: "xxx", args: {...}}
C++ tool_registry
    ↓ JNI nativeCallAndroidTool()
Java NativeMobileAgentApiAdapter
    ↓ AndroidToolManager.callTool()
Java ToolExecutor (your implementation)
    ↓
Android System APIs
```

## 扩展步骤

### 步骤 1: 创建 ToolExecutor 实现类

在 `agent-android/src/main/java/com/hh/agent/android/tools/` 目录下创建新类：

```java
package com.hh.agent.android.tools;

import android.content.Context;
import com.hh.agent.core.ToolExecutor;
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

编辑 `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`：

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
package com.hh.agent.android.tools;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import com.hh.agent.core.ToolExecutor;
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

## 配置说明

### config.json.template 示例

项目根目录的 `config.json.template` 是配置模板文件。使用时需要复制并填入真实的 API Key：

```bash
cp config.json.template config.json
```

配置项说明：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| provider.apiKey | string | 是 | LLM 服务商的 API Key |
| provider.baseUrl | string | 是 | API 基础 URL |
| agent.model | string | 是 | 使用的模型名称 |

**示例配置：**

```json
{
  "provider": {
    "apiKey": "sk-cp-xxxxxxxxxxxxx",
    "baseUrl": "https://api.minimaxi.com/v1"
  },
  "agent": {
    "model": "MiniMax-M2.5-highspeed"
  }
}
```

## 动态工具注册

Mobile Agent 支持运行时动态注册工具。通过 `AndroidToolManager` 的扩展方法：

```java
// 在自定义 Application 或初始化逻辑中
AndroidToolManager manager = AndroidToolManager.getInstance();
manager.registerTool("custom_tool", new CustomTool(context));
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

## API 参考

### ToolExecutor 接口

工具执行器接口，定义在 `agent-core` 模块中：

```java
package com.hh.agent.core;

import org.json.JSONObject;

/**
 * 工具执行器接口
 */
public interface ToolExecutor {

    /**
     * 获取工具名称
     * @return 工具名称，用于 function 路由
     */
    String getName();

    /**
     * 获取工具描述
     * @return 工具功能描述
     */
    default String getDescription() {
        return "A custom Android tool";
    }

    /**
     * 执行工具
     * @param args JSON 格式的参数
     * @return JSON 格式的执行结果
     */
    String execute(JSONObject args);
}
```

**返回格式约定：**

- 成功: `{"success": true, "result": "...", ...}`
- 失败: `{"success": false, "error": "error_code", "message": "..."}`

### AndroidToolManager API

Android 工具管理器，定义在 `agent-android` 模块中：

```java
package com.hh.agent.android;

/**
 * Android 工具管理器
 */
public class AndroidToolManager {

    /**
     * 获取单例实例
     */
    public static AndroidToolManager getInstance();

    /**
     * 初始化工具管理器
     * @param context Activity 上下文
     */
    public void initialize(Context context);

    /**
     * 调用工具
     * @param function 工具名称
     * @param args 参数
     * @return 执行结果
     */
    public String callTool(String function, JSONObject args);

    /**
     * 动态注册工具
     * @param name 工具名称
     * @param executor 工具执行器
     */
    public void registerTool(String name, ToolExecutor executor);

    /**
     * 移除工具
     * @param name 工具名称
     */
    public void unregisterTool(String name);

    /**
     * 获取所有已注册工具列表
     * @return 工具名称列表
     */
    public List<String> getToolNames();
}
```

### WorkspaceManager API

工作空间管理器，处理文件和数据存储：

```java
package com.hh.agent.android;

/**
 * 工作空间管理器
 */
public class WorkspaceManager {

    /**
     * 获取工作目录
     * @return File 工作目录
     */
    public File getWorkspaceDir();

    /**
     * 保存文件
     * @param filename 文件名
     * @param content 文件内容
     * @return 是否成功
     */
    public boolean saveFile(String filename, String content);

    /**
     * 读取文件
     * @param filename 文件名
     * @return 文件内容
     */
    public String readFile(String filename);

    /**
     * 删除文件
     * @param filename 文件名
     * @return 是否成功
     */
    public boolean deleteFile(String filename);

    /**
     * 列出工作目录下的所有文件
     * @return 文件名列表
     */
    public String[] listFiles();
}
```

### 使用示例

```java
// 在 Activity 中初始化
AndroidToolManager manager = AndroidToolManager.getInstance();
manager.initialize(this);

// 调用工具
String result = manager.callTool("show_toast", new JSONObject("{\"message\": \"Hello!\"}"));

// 动态注册自定义工具
manager.registerTool("my_tool", new MyTool(this));

// 获取工具列表
List<String> tools = manager.getToolNames();
```
