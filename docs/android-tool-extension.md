# Android 工具扩展指南

本文档基于当前工程代码，说明如何为宿主应用添加新的 Android Tool。

当前工具能力的接入方式是：

- Tool 实现在宿主 `app` 层
- Tool 接口定义在 `agent-core`
- Tool 注册与 schema 生成由 `agent-android` 的 `AgentInitializer` 和 `AndroidToolManager` 完成

如果需要添加复杂工作流而不是单个工具，请参考 [Android Skill 扩展指南](./android-skill-extension.md)。

## 当前注册机制

当前代码不是在 `Activity` 里逐个注册 Tool，而是在应用初始化时一次性传入 `Map<String, ToolExecutor>`：

```java
Map<String, ToolExecutor> tools = new HashMap<>();
tools.put("display_notification", new DisplayNotificationTool(this));
tools.put("read_clipboard", new ReadClipboardTool(this));
tools.put("search_contacts", new SearchContactsTool());
tools.put("send_im_message", new SendImMessageTool());

AgentInitializer.initialize(this, voiceRecognizer, tools, () -> {
    // Agent 初始化完成
});
```

参考现有实现：

- `app/src/main/java/com/hh/agent/app/App.java`
- `agent-android/src/main/java/com/hh/agent/android/AgentInitializer.java`
- `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`

## 步骤 1: 创建 Tool 类

建议放在宿主应用的工具目录，例如：

```text
app/src/main/java/com/hh/agent/tool/MyTool.java
```

示例：

```java
package com.hh.agent.tool;

import com.hh.agent.core.ToolExecutor;
import org.json.JSONObject;

public class MyTool implements ToolExecutor {

    @Override
    public String getName() {
        return "my_tool";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            String value = args.optString("value", "");
            return "{\"success\": true, \"result\": \"done: " + value + "\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"execution_failed\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getDescription() {
        return "工具描述";
    }

    @Override
    public String getArgsDescription() {
        return "value: 参数说明";
    }

    @Override
    public String getArgsSchema() {
        return "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\",\"description\":\"参数说明\"}}}";
    }
}
```

注意点：

- 接口包名是 `com.hh.agent.core.ToolExecutor`，不是旧文档中的 `com.hh.agent.library.ToolExecutor`
- `getName()` 返回值要和注册到 `Map` 里的 key 保持一致
- `execute(...)` 的入参类型是 `org.json.JSONObject`
- 返回值是 JSON 字符串，供 native 层继续处理

## 步骤 2: 在应用初始化时注册

把 Tool 放进传给 `AgentInitializer.initialize(...)` 的 `Map<String, ToolExecutor>` 中。

示例：

```java
Map<String, ToolExecutor> tools = new HashMap<>();
tools.put("my_tool", new MyTool());

AgentInitializer.initialize(
        this,
        voiceRecognizer,
        tools,
        () -> {
            // 初始化完成后的逻辑
        }
);
```

`AgentInitializer` 内部会完成这些事：

1. 创建 `AndroidToolManager`
2. 调用 `registerTools(tools)`
3. 调用 `initialize()`
4. 生成 tools schema 并传给 `NativeMobileAgentApi`

因此外部通常不需要手动 new `AndroidToolManager` 再逐个注册。

## 步骤 3: 构建并验证

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

启动应用后，向 Agent 发送一条会触发该工具的请求，确认工具被正确调用。

## 返回格式约定

建议遵循现有工具实现风格：

- 成功: `{"success": true, "result": "..."}`
- 失败: `{"success": false, "error": "error_code", "message": "..."}`

如果返回的不是标准 JSON，后续工具结果展示和 Agent 推理会更难处理。

## 工具描述如何暴露给 Agent

`AndroidToolManager` 不会把每个 Tool 单独注册成一个 function，而是会生成一个统一的 `call_android_tool` 工具：

- `function` 表示要调用的具体工具名
- `args` 表示工具参数

也就是说，Skill 或模型实际调用的是：

```json
{
  "function": "my_tool",
  "args": {
    "value": "demo"
  }
}
```

## 当前示例工具

当前 `app` 中已有的工具实现：

- `app/src/main/java/com/hh/agent/tool/DisplayNotificationTool.java`
- `app/src/main/java/com/hh/agent/tool/ReadClipboardTool.java`
- `app/src/main/java/com/hh/agent/tool/SearchContactsTool.java`
- `app/src/main/java/com/hh/agent/tool/SendImMessageTool.java`

可以直接按这些实现的结构新增。
