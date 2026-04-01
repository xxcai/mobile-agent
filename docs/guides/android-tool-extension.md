# Android 工具扩展指南

本文档基于当前工程代码，说明如何为宿主应用添加新的 Android Tool。

如果你还不了解当前项目里 native agent、Java tool channel 和宿主业务工具之间的整体关系，建议先看：

- [Android Tool 架构说明](../architecture/android-tool-architecture.md)

当前工具能力的接入方式是：

- shortcut 实现在宿主 `app` 层
- shortcut 接口定义在 `agent-core` 的 `com.hh.agent.core.shortcut`
- shortcut 注册与多通道 schema 生成由 `agent-android` 的 `AgentInitializer` 和 `AndroidToolManager` 完成
- 已注册的业务 shortcut 会通过 `run_shortcut` 暴露给 LLM
- `NativeMobileAgentApi` 当前位于 `com.hh.agent.core.api.impl`

当前推荐搭配方式是：

- 新增业务原子能力时，先新增 `ShortcutExecutor`
- 再补对应 skill 或接入现有 skill
- 如某个 shortcut 的细节较多，再补 `references/`

如果需要添加复杂工作流而不是单个工具，请参考 [Android Skill 扩展指南](./android-skill-extension.md)。

如果你要扩展的是页面感知 / UI 执行链路，而不是宿主业务工具，先看：

- [Observation-Bound Execution 协议说明](../protocols/observation-bound-execution.md)

## 当前注册机制

当前代码不是在 `Activity` 里逐个注册 Tool，而是在应用初始化时一次性传入 `Collection<? extends ShortcutExecutor>`：

```java
import com.hh.agent.core.shortcut.ShortcutExecutor;

List<ShortcutExecutor> shortcuts = new ArrayList<>();
shortcuts.add(new SearchContactsShortcut());
shortcuts.add(new SendImMessageShortcut());

AgentInitializer.initialize(this, voiceRecognizer, shortcuts, viewContextSourcePolicy, () -> {
    // Agent 初始化完成
});
```

如果宿主需要把 `agent-android` 日志接入自己的日志体系，可以在初始化前可选调用：

```java
AgentInitializer.setLogger(yourAgentLogger);
```

当前仓库中的 `app` 示例没有额外实现宿主 logger，而是直接使用 `agent-android` 的默认日志实现。

如果宿主要接入自定义 logger，建议保持 `agent-android` 当前的结构化日志格式和分级约定，具体见：

- `docs/logging/agent-android-logging.md`

参考现有实现：

- `app/src/main/java/com/hh/agent/app/App.java`
- `agent-android/src/main/java/com/hh/agent/android/AgentInitializer.java`
- `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`
- `agent-core/src/main/java/com/hh/agent/core/shortcut/`

## 步骤 1: 创建 Shortcut 类

建议放在宿主应用的工具目录，例如：

```text
app/src/main/java/com/hh/agent/shortcut/MyShortcut.java
```

示例：

```java
package com.hh.agent.shortcut;

import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;

public class MyShortcut implements ShortcutExecutor {

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder("my_tool", "执行示例业务动作", "处理一个字符串值并返回结果")
                .domain("demo")
                .requiredSkill("my_skill")
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

- 接口包名是 `com.hh.agent.core.shortcut.ShortcutExecutor`
- `ShortcutDefinition.builder(name, title, description)` 的 `name` 就是对外暴露的 shortcut 名
- `getDefinition()` 负责提供模型选择 shortcut 所需的结构化信息
- `ShortcutDefinition` 当前通过 builder/DSL 构建，核心字段包括：
  - `name`
  - `title`
  - `description`
  - `domain`
  - `requiredSkill`
  - 参数定义及其 example
- `description` 建议认真填写，它会直接进入传给 LLM 的工具说明文本，影响工具匹配质量
- `execute(...)` 的入参类型是 `org.json.JSONObject`
- `execute(...)` 的返回值是 `ToolResult`
- JSON 字符串序列化由 Android tool routing 边界统一完成，不需要工具实现自己手写结果 JSON

## 步骤 2: 在应用初始化时注册

把 shortcut 放进传给 `AgentInitializer.initialize(...)` 的 `Collection<? extends ShortcutExecutor>` 中。

示例：

```java
import com.hh.agent.core.shortcut.ShortcutExecutor;

List<ShortcutExecutor> shortcuts = new ArrayList<>();
shortcuts.add(new MyShortcut());

AgentInitializer.initialize(
        this,
        voiceRecognizer,
        shortcuts,
        viewContextSourcePolicy,
        () -> {
            // 初始化完成后的逻辑
        }
);
```

`AgentInitializer` 内部会完成这些事：

1. 创建 `AndroidToolManager`
2. 调用 `registerShortcuts(shortcuts)`
3. 调用 `initialize()`
4. 已注册的 `ShortcutExecutor` 接入 `ShortcutRuntime`
5. 生成 tools schema 并传给 `NativeMobileAgentApi`

因此外部通常不需要手动 new `AndroidToolManager` 再逐个注册。

当前相关类型位置：

- `ShortcutExecutor` / `ShortcutDefinition`: `com.hh.agent.core.shortcut`
- `ToolResult`: `com.hh.agent.core.tool`
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

- `run_shortcut`
  宿主 App 业务 shortcut runtime 通道
- `android_view_context_tool`
  页面观察通道，负责拿当前页面的 `nativeViewXml` / observation snapshot
- `android_gesture_tool`
  UI 执行通道，当前支持 observation 引用参数，并已具备真实 in-process 执行能力

其中你在宿主 `app` 层注册的 `ShortcutExecutor`，会通过 `ShortcutRuntime` 进入 `run_shortcut`。

也就是说，业务 Tool 的实际调用格式仍然是：

```json
{
  "shortcut": "my_tool",
  "args": {
    "value": "demo"
  }
}
```

当前 `run_shortcut` 顶层 schema 已经收窄，不会再全量暴露所有 shortcut 细节。

因此新增业务 shortcut 时，通常只需要补好 `ShortcutDefinition`，并在需要时通过：

- `describe_shortcut`
  按需查询某个 shortcut 的描述、参数 schema 和最小参数样例
- 对应 skill
  告诉 Agent 什么时候该使用这个 shortcut

`android_view_context_tool` 和 `android_gesture_tool` 这两个通道现在推荐配合使用：

1. 先用 `android_view_context_tool` 获取当前页面 observation
2. 再把 `snapshotId`、目标节点索引、目标 bounds 等引用信息带进 `android_gesture_tool`

这套协议的设计原因、字段含义和聊天页例子见：

- [Observation-Bound Execution 协议说明](../protocols/observation-bound-execution.md)

`android_gesture_tool` 当前已经不再是纯 mock 通道：

- `tap` 已支持 observation-bound 的真实 in-process 执行
- `swipe` 已支持高层滚动意图参数
- 当前默认 runtime 仍处于过渡阶段：
  - `tap` 主要基于 bounds 命中后的 `performClick / performItemClick`
  - `swipe` 主要基于容器滚动实现

下一阶段计划切到“Activity 内构造 `MotionEvent` 并通过 `dispatchTouchEvent` 注入”的主路径，以统一 `tap / swipe / long_press / double_tap` 的手势模拟方式。

## `web_dom` / `android_web_action_tool` 扩展顺序

如果你要在当前工程里继续补真实 H5 DOM 抓取和 Web 动作注入，建议按下面的顺序推进，不要一开始就同时改所有层。

### 第 1 步：先完善 WebView 选择策略

主入口：

- [WebViewFinder.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/viewcontext/WebViewFinder.java)

优先关注的方法：

- `findPrimaryWebView(...)`
- `collectCandidates(...)`
- `isVisibleCandidate(...)`
- `computeVisibleArea(...)`

推荐做法：

- 优先在 `findPrimaryWebView(...)` 内升级“主 WebView 选择”规则
- 如果只需要补候选过滤，优先收敛到 `isVisibleCandidate(...)`
- 如果需要从“面积最大”升级为“面积 + ready 状态 + 其他优先级”，也尽量先集中在 `findPrimaryWebView(...)`

### 第 2 步：再替换真实 DOM 抓取

主入口：

- [WebDomSnapshotProvider.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/viewcontext/WebDomSnapshotProvider.java)
- [MockWebDomSnapshotProvider.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/viewcontext/MockWebDomSnapshotProvider.java)
- [MainThreadRunner.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/thread/MainThreadRunner.java)

优先关注的方法：

- `MockWebDomSnapshotProvider.getCurrentWebDomSnapshot(...)`
- `MockWebDomSnapshotProvider.createDefault()`

推荐做法：

- 先保持 `WebDomSnapshotProvider` 接口不变
- 在 `getCurrentWebDomSnapshot(...)` 中把 mock DOM 替换为真实 DOM 抓取
- 使用 `MainThreadRunner.call(...)` 包裹：
  - 前台 `Activity` 获取
  - `WebViewFinder.findPrimaryWebView(...)`
  - `WebView` 属性访问
  - 后续 `evaluateJavascript(...)` 发起逻辑
- 如果真实实现已经明显复杂到不适合继续放在 mock 类里，新增 `RealWebDomSnapshotProvider`，然后只调整 `createDefault()` 的实例化

### 第 3 步：再替换真实 Web 动作注入

主入口：

- [WebActionExecutor.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/webaction/WebActionExecutor.java)
- [MockWebActionExecutor.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/webaction/MockWebActionExecutor.java)
- [WebActionRequest.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/webaction/WebActionRequest.java)

优先关注的方法：

- `MockWebActionExecutor.execute(...)`

推荐做法：

- 首版先只支持一个动作，例如 `click`
- 在 `execute(...)` 内按 `request.action` 做有限分支
- 先基于 `request.selector` 和 `request.observation.snapshotId` 完成真实执行
- 保持既有返回字段不变，只替换内部执行逻辑

### 第 4 步：最后才微调 channel 接线

主入口：

- [WebDomViewContextSourceHandler.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/channel/WebDomViewContextSourceHandler.java)
- [WebActionToolChannel.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/channel/WebActionToolChannel.java)

优先关注的方法：

- `WebDomViewContextSourceHandler.execute(...)`
- `WebActionToolChannel.execute(...)`
- `WebActionToolChannel.validate(...)`

推荐做法：

- 这两层只保留参数校验、provider / executor 调度和统一结果包装
- 除非字段契约确实需要扩展，否则尽量不要在这里塞真实业务逻辑
- 不要把真实 Web 动作逻辑混回现有 `android_gesture_tool`

### 不建议动的稳定边界

除非明确评审通过，否则不要顺手改这些稳定边界：

- [ViewContextToolChannel.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-android/src/main/java/com/hh/agent/android/channel/ViewContextToolChannel.java)
- 现有 `android_gesture_tool` 的 native touch 路径
- 已冻结字段：
  - `source=web_dom`
  - `interactionDomain=web`
  - `channel=android_web_action_tool`

### 建议联调入口

优先使用：

- [ToolChannelTestActivity.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/app/src/main/java/com/hh/agent/ToolChannelTestActivity.java)
- [BusinessWebActivity.java](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/app/src/main/java/com/hh/agent/BusinessWebActivity.java)

当前仓库已经准备了：

- debug panel
- `Static / Delayed / Form` 本地模板页
- `Run View Context`
- `Run Web Action`

推荐先在这些入口完成基线联调，再接真实业务 WebView 页面。

## 当前示例工具

当前 `app` 中已有的工具实现：

- `app/src/main/java/com/hh/agent/shortcut/SearchContactsShortcut.java`
- `app/src/main/java/com/hh/agent/shortcut/SendImMessageShortcut.java`

可以直接按这些实现的结构新增。

## 当前默认路径

当前仓库已经删除 `LegacyAndroidToolChannel` 和 `call_android_tool` 旧路径。

新增业务 shortcut 时，应默认使用：

- `ShortcutExecutor` -> `ShortcutRuntime` -> `run_shortcut`
- 如缺少 shortcut 细节，则先调用 `describe_shortcut`

## 最小接入模板

如果你只需要一个最小可运行模板，可以直接套下面这段：

```java
public class MyShortcut implements ShortcutExecutor {

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder("my_tool", "执行示例业务动作", "处理一个字符串值并返回结果")
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
