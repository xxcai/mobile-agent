# Android 工具扩展指南

本文档基于当前工程代码，说明如何为宿主应用添加新的 Android Tool。

当前工具能力的接入方式是：

- Tool 实现在宿主 `app` 层
- Tool 接口定义在 `agent-core` 的 `com.hh.agent.core.tool`
- Tool 注册与多通道 schema 生成由 `agent-android` 的 `AgentInitializer` 和 `AndroidToolManager` 完成
- `NativeMobileAgentApi` 当前位于 `com.hh.agent.core.api.impl`

如果需要添加复杂工作流而不是单个工具，请参考 [Android Skill 扩展指南](./android-skill-extension.md)。

如果你要扩展的是页面感知 / UI 执行链路，而不是宿主业务工具，先看：

- [Observation-Bound Execution 协议说明](./observation-bound-execution.md)

## 当前注册机制

当前代码不是在 `Activity` 里逐个注册 Tool，而是在应用初始化时一次性传入 `Map<String, ToolExecutor>`：

```java
import com.hh.agent.core.tool.ToolExecutor;

Map<String, ToolExecutor> tools = new HashMap<>();
tools.put("display_notification", new DisplayNotificationTool(this));
tools.put("read_clipboard", new ReadClipboardTool(this));
tools.put("search_contacts", new SearchContactsTool());
tools.put("send_im_message", new SendImMessageTool());

AgentInitializer.initialize(this, voiceRecognizer, tools, () -> {
    // Agent 初始化完成
});
```

如果宿主需要把 `agent-android` 日志接入自己的日志体系，可以在初始化前可选调用：

```java
AgentInitializer.setLogger(yourAgentLogger);
```

当前仓库中的 `app` 示例没有额外实现宿主 logger，而是直接使用 `agent-android` 的默认日志实现。

如果宿主要接入自定义 logger，建议保持 `agent-android` 当前的结构化日志格式和分级约定，具体见：

- `docs/agent-android-logging.md`

参考现有实现：

- `app/src/main/java/com/hh/agent/app/App.java`
- `agent-android/src/main/java/com/hh/agent/android/AgentInitializer.java`
- `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`
- `agent-core/src/main/java/com/hh/agent/core/tool/`

## 步骤 1: 创建 Tool 类

建议放在宿主应用的工具目录，例如：

```text
app/src/main/java/com/hh/agent/tool/MyTool.java
```

示例：

```java
package com.hh.agent.tool;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

public class MyTool implements ToolExecutor {

    @Override
    public String getName() {
        return "my_tool";
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder("执行示例业务动作", "处理一个字符串值并返回结果")
                .intentExamples("执行 demo 工具", "调用 my_tool 处理一个值")
                .stringParam("value", "示例参数", true, "demo")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        try {
            String value = args.optString("value", "");
            return ToolResult.success().with("result", "done: " + value);
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }
}
```

注意点：

- 接口包名是 `com.hh.agent.core.tool.ToolExecutor`，不是旧文档中的 `com.hh.agent.library.ToolExecutor`
- `getName()` 返回值要和注册到 `Map` 里的 key 保持一致
- `getDefinition()` 负责提供模型选择工具所需的结构化信息
- `ToolDefinition` 当前通过 builder/DSL 构建，核心字段包括：
  - `title`
  - `description`
  - `intentExamples`（可选）
  - 参数定义及其 example
- `description` 建议认真填写，它会直接进入传给 LLM 的工具说明文本，影响工具匹配质量
- `execute(...)` 的入参类型是 `org.json.JSONObject`
- `execute(...)` 的返回值是 `ToolResult`
- JSON 字符串序列化由 Android tool routing 边界统一完成，不需要工具实现自己手写结果 JSON

## 步骤 2: 在应用初始化时注册

把 Tool 放进传给 `AgentInitializer.initialize(...)` 的 `Map<String, ToolExecutor>` 中。

示例：

```java
import com.hh.agent.core.tool.ToolExecutor;

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

当前相关类型位置：

- `ToolExecutor` / `ToolDefinition` / `ToolResult`: `com.hh.agent.core.tool`
- `NativeMobileAgentApi`: `com.hh.agent.core.api.impl`
- `AgentEventListener`: `com.hh.agent.core.event`

## 步骤 3: 构建并验证

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

启动应用后，向 Agent 发送一条会触发该工具的请求，确认工具被正确调用。

## 返回格式约定

当前推荐统一返回 `ToolResult`：

```java
return ToolResult.success()
        .with("result", "done: " + value);
```

```java
return ToolResult.error("missing_required_param")
        .with("param", "value");
```

```java
return ToolResult.error("execution_failed", e.getMessage());
```

最终序列化后的基础结构约定仍然是：

- 成功: `{"success": true, ...}`
- 失败: `{"success": false, "error": "error_code", "message": "...", ...}`

因此工具实现只需要返回结构化结果，不需要自己拼 JSON 字符串。

## 工具如何暴露给 Agent

当前 Android 侧已经支持多通道工具：

- `call_android_tool`
  宿主 App 业务工具通道
- `android_view_context_tool`
  页面观察通道，负责拿当前页面的 `nativeViewXml` / observation snapshot
- `android_gesture_tool`
  UI 执行通道，当前支持 observation 引用参数，后续将优先基于 observation 执行

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

- 工具描述
- 常见意图示例（如果提供）
- 参数 schema
- 最小参数样例

因此新增业务 Tool 时，通常只需要补好 `ToolDefinition`，不需要再手改统一提示词。

`android_view_context_tool` 和 `android_gesture_tool` 这两个通道现在推荐配合使用：

1. 先用 `android_view_context_tool` 获取当前页面 observation
2. 再把 `snapshotId`、目标节点索引、目标 bounds 等引用信息带进 `android_gesture_tool`

这套协议的设计原因、字段含义和聊天页例子见：

- [Observation-Bound Execution 协议说明](./observation-bound-execution.md)

`android_gesture_tool` 当前仍然是 mock 运行时框架，适合用于验证通道选择和参数结构，还没有完全切到真实 observation-bound 执行。

## 当前示例工具

当前 `app` 中已有的工具实现：

- `app/src/main/java/com/hh/agent/tool/DisplayNotificationTool.java`
- `app/src/main/java/com/hh/agent/tool/ReadClipboardTool.java`
- `app/src/main/java/com/hh/agent/tool/SearchContactsTool.java`
- `app/src/main/java/com/hh/agent/tool/SendImMessageTool.java`

可以直接按这些实现的结构新增。

## 最小接入模板

如果你只需要一个最小可运行模板，可以直接套下面这段：

```java
public class MyTool implements ToolExecutor {

    @Override
    public String getName() {
        return "my_tool";
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder("执行示例业务动作", "处理一个字符串值并返回结果")
                .stringParam("value", "示例参数", true, "demo")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        if (!args.has("value")) {
            return ToolResult.error("missing_required_param")
                    .with("param", "value");
        }
        return ToolResult.success()
                .with("result", "done: " + args.optString("value", ""));
    }
}
```
