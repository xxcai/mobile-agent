# Android 工具扩展指南

本文档基于当前工程代码，说明如何为宿主应用添加新的 Android Tool。

当前工具能力的接入方式是：

- Tool 实现在宿主 `app` 层
- Tool 接口定义在 `agent-core`
- Tool 注册与多通道 schema 生成由 `agent-android` 的 `AgentInitializer` 和 `AndroidToolManager` 完成

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
import com.hh.agent.core.ToolDefinition;
import org.json.JSONObject;

import java.util.Arrays;

public class MyTool implements ToolExecutor {

    @Override
    public String getName() {
        return "my_tool";
    }

    @Override
    public ToolDefinition getDefinition() {
        try {
            return new ToolDefinition(
                    "执行示例业务动作",
                    Arrays.asList("执行 demo 工具", "调用 my_tool 处理一个值"),
                    new JSONObject()
                            .put("type", "object")
                            .put("properties", new JSONObject()
                                    .put("value", new JSONObject()
                                            .put("type", "string")
                                            .put("description", "示例参数"))),
                    new JSONObject().put("value", "demo")
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool definition", e);
        }
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
}
```

注意点：

- 接口包名是 `com.hh.agent.core.ToolExecutor`，不是旧文档中的 `com.hh.agent.library.ToolExecutor`
- `getName()` 返回值要和注册到 `Map` 里的 key 保持一致
- `getDefinition()` 负责提供模型选择工具所需的结构化信息
- `ToolDefinition` 当前至少包含：
  - `summary`
  - `intentExamples`
  - `argsSchema`
  - `argsExample`
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

## 工具如何暴露给 Agent

当前 Android 侧已经支持多通道工具：

- `call_android_tool`
  宿主 App 业务工具通道
- `android_gesture_tool`
  坐标手势通道

其中你在宿主 `app` 层注册的 `ToolExecutor`，目前都会被聚合进 `call_android_tool`。

也就是说，业务 Tool 的实际调用格式仍然是：

```json
{
  "function": "my_tool",
  "args": {
    "value": "demo"
  }
}
```

当前 `call_android_tool` 的 schema 会自动聚合每个工具的：

- 工具摘要
- 常见意图示例
- 参数 schema
- 最小参数样例

因此新增业务 Tool 时，通常只需要补好 `ToolDefinition`，不需要再手改统一提示词。

`android_gesture_tool` 目前已存在，但仍然是 mock 运行时框架，适合用于验证通道选择和参数结构，不执行真实点击或滑动。

## 当前示例工具

当前 `app` 中已有的工具实现：

- `app/src/main/java/com/hh/agent/tool/DisplayNotificationTool.java`
- `app/src/main/java/com/hh/agent/tool/ReadClipboardTool.java`
- `app/src/main/java/com/hh/agent/tool/SearchContactsTool.java`
- `app/src/main/java/com/hh/agent/tool/SendImMessageTool.java`

可以直接按这些实现的结构新增。
