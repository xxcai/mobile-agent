# Android Tool 架构说明

本文档面向第一次接触本项目的读者，说明当前工程中：

- LLM 看到的 Android tool 是如何组织出来的
- native 层如何调用到 Java 层的 tool
- Java 层的业务 tool、tool channel、native agent 之间的职责边界
- 后续如果要新增一个 Android tool，应该改哪些位置

如果你更关心“如何新增宿主业务 Tool”，请先看 [Android 工具扩展指南](../guides/android-tool-extension.md)。

## 一句话理解

当前 Android tool 体系可以概括为：

- Java 侧定义 Android 顶层 tool channel，并负责真正执行
- native 侧把这些 channel 注册成 LLM 可调用的工具
- 当 LLM 发起 tool call 时，native 通过 JNI callback 回到 Java 执行

也就是说：

- native 持有的是工具注册与调度入口
- Java 持有的是 Android 能力的实际实现

## 先看两张图

建议先看下面两张图，再看后面的文字。

第一张图回答：

- 这个系统里有哪些角色
- 它们分别在 Java 侧还是 native 侧
- schema 注册和 callback 注册分别经过哪里

![Android Tool Architecture Overview](./android-tool-architecture-overview.svg)

第二张图回答：

- 初始化阶段怎么把 Java tools 注册给 native
- 一次实际 tool call 是怎么从 LLM 走到 Java channel 再返回的

![Android Tool Call Sequence](./android-tool-call-sequence.svg)

如果只记 3 件事，可以先记这 3 句：

1. 宿主业务 shortcut 先注册到 Java 的 `AndroidToolManager`
2. `AndroidToolManager` 会把已注册 channel 聚合成 `tools.json`
3. 真正执行时，native 通过 callback 回调 Java channel

## 代码中的关键角色

### 1. `AgentInitializer`

入口在 [AgentInitializer.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentInitializer.java)。

初始化时它会做几件事：

- 创建 `AndroidToolManager`
- 注册宿主传入的业务 `ShortcutExecutor`
- 注册内置 channel
- 生成 `tools.json`
- 初始化 native agent

这里可以把它理解成“Java 侧 Android tool 体系的装配入口”。

### 2. `AndroidToolManager`

核心在 [AndroidToolManager.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java)。

它有两个最重要的职责：

- 聚合所有顶层 tool channel，生成给 LLM 的 `tools.json`
- 实现 `AndroidToolCallback`，接住 native 发回来的 tool call

它内部维护三类状态：

- `tools`
  遗留宿主业务工具，类型是 `Map<String, ToolExecutor>`
- `shortcutRuntime`
  宿主业务 shortcut runtime；运行已显式注册的 `ShortcutExecutor`
- `channels`
  顶层 Android tool channel，类型是 `Map<String, AndroidToolChannelExecutor>`

当前默认注册的顶层 channel 有：

- `run_shortcut`
- `android_gesture_tool`
- `android_view_context_tool`

### 3. `AndroidToolChannelExecutor`

接口在 [AndroidToolChannelExecutor.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/channel/AndroidToolChannelExecutor.java)。

它定义了每个顶层 channel 需要提供的能力：

- `getChannelName()`
- `buildToolDefinition()`
- `execute(JSONObject params)`

因此，从架构上说，当前项目把 Android tool 分成了“顶层 channel”这一层，而不是直接把所有细碎能力都平铺给 native。

### 4. `ShortcutExecutor` / `ShortcutRuntime`

宿主业务 shortcut 接口在 `agent-core` 的 `com.hh.agent.core.shortcut.ShortcutExecutor`。

这类 shortcut 不会直接暴露给 native，而是先注册进 `ShortcutRuntime`。

可以把它理解成：

- `ShortcutExecutor` 是宿主业务原子动作
- `ShortcutRuntime` 是宿主业务动作运行时
- `ShortcutRuntimeChannel` 是顶层对外通道

当前相关实现位于：

- [ShortcutDefinition.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/shortcut/ShortcutDefinition.java)
- [ShortcutExecutor.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/shortcut/ShortcutExecutor.java)
- [ShortcutRuntime.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/shortcut/ShortcutRuntime.java)
- [ShortcutRuntimeChannel.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/channel/ShortcutRuntimeChannel.java)

### 5. `AndroidToolCallback`

桥接接口在 [AndroidToolCallback.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/tool/AndroidToolCallback.java)。

它非常简单，只有一个方法：

- `callTool(String toolName, String argsJson)`

这意味着 native 并不直接依赖 Java 侧的具体 channel 类，它只知道：

- 工具名是什么
- 参数 JSON 是什么
- 返回结果 JSON 是什么

### 6. `NativeMobileAgentApi` / `NativeAgent`

Java 到 native 的桥主要在：

- [NativeMobileAgentApi.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/api/impl/NativeMobileAgentApi.java)
- [NativeAgent.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/NativeAgent.java)

这里提供两个关键入口：

- 注册 Java callback 给 native
- 把 Java 生成的 `tools.json` 传给 native

### 7. Native `AndroidTools`

native 侧对应抽象在：

- [android_tools.hpp](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/include/icraw/platform/android/android_tools.hpp)
- [android_tools.cpp](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/src/platform/android/android_tools.cpp)

这一层只做两件事：

- 保存 callback
- 在收到 tool call 时转发给 callback

它不直接实现 Android 页面观察、点击、滑动或宿主业务逻辑。

## 初始化链路

初始化阶段的主流程如下：

1. 宿主应用调用 `AgentInitializer.initialize(...)`
2. Java 创建 `AndroidToolManager`
3. `AndroidToolManager` 注册宿主业务 shortcut 和内置 channel
4. 已注册的 `ShortcutExecutor` 会被注册进 `ShortcutRuntime`
5. `AndroidToolManager` 聚合所有 channel 的 schema，生成 `tools.json`
6. Java 通过 `NativeMobileAgentApi` 把 `tools.json` 传给 native
7. Java 同时把 `AndroidToolManager` 注册成 `AndroidToolCallback`
8. native 侧把这些 schema 注册进自己的 tool registry

这一阶段最重要的事实是：

- tool schema 的来源在 Java
- native 只是消费这些 schema 并注册

## 执行链路

一次实际 tool call 的链路如下：

1. LLM 在 native agent loop 中选择某个 Android tool
2. native 根据 tool 名称找到注册过的外部 schema
3. native 通过 JNI callback 调用 Java 的 `callTool(toolName, argsJson)`
4. `AndroidToolManager` 根据 `toolName` 找到对应的 `AndroidToolChannelExecutor`
5. 如果命中的是 `run_shortcut`，channel 会再根据 `shortcut` 字段查找 `ShortcutRuntime`
6. Java channel 执行自己的 `execute(...)`
7. Java 返回 `ToolResult`，再序列化成 JSON 字符串
8. native 收到结果，继续后续 agent loop

这里的关键点是：

- native 负责“何时调工具”
- Java 负责“工具具体做什么”

## 当前有哪些依赖

### native 对 Java 的依赖

当前 native 对 Java 有两类依赖：

1. schema 依赖

- native 不自己定义 Android tool schema
- 它依赖 Java 生成 `tools.json`

2. callback 依赖

- native 不自己执行 Android tool
- 它依赖 Java 注册进来的 `AndroidToolCallback`

### Java 对 native 的依赖

Java 侧不依赖 native 的具体业务实现，但依赖 JNI 提供的桥接能力：

- 注册 callback
- 设置 tools schema
- 初始化和运行 native agent

### 是否强耦合

是有依赖的，但不是“native 直接 new Java channel”这种强耦合。

更准确地说，当前是：

- native 依赖 Java 暴露的抽象能力
- Java 依赖 native 暴露的 JNI 桥入口
- 双方通过字符串化的 `toolName + argsJson + resultJson` 协议交互

所以当前耦合点主要在：

- schema JSON 协议
- callback 方法签名
- tool 名称约定

## 为什么要有 channel 这一层

如果没有 channel，native 看到的会是很多零散工具，管理和演进都比较困难。

当前引入 channel 的好处是：

- 把宿主业务工具统一放到 `run_shortcut`
- 把页面观察能力统一放到 `android_view_context_tool`
- 把 UI 执行动作统一放到 `android_gesture_tool`

这样带来的收益有两个：

1. native 侧看到的是少量稳定的顶层工具
2. Java 侧可以在 channel 内部继续演进自己的对象模型，而不必频繁改 native 桥

这也是为什么最近 `GestureToolChannel` 和 `ViewContextToolChannel` 都在往“对象化内部结构、稳定外部 contract”的方向收。

## 如果要新增一个 Android tool，该改哪里

### 场景 1：新增宿主业务工具

如果是宿主业务动作，例如“搜索联系人”“发送消息”，通常：

- 实现一个新的 `ShortcutExecutor`
- 在宿主 `app` 初始化时显式注入 `Collection<? extends ShortcutExecutor>`
- 由 `run_shortcut` 统一暴露给 LLM

通常不需要改 native 桥。

## 兼容状态

当前仓库里 `LegacyAndroidToolChannel` 代码仍然保留，但不再是默认注册通道。

这意味着：

- 默认暴露给 LLM 的宿主业务入口已经切到 `run_shortcut`
- `call_android_tool` 仅保留为兼容实现，不应继续作为新增 skill 或新文档的默认协议
- `ToolExecutor` 仍存在于仓库中，但不是新增业务能力的推荐接入方式

### 场景 2：新增一个顶层 Android channel

如果是新的顶层能力，例如未来新增一个独立的页面分析通道，通常需要：

- 新增一个 `AndroidToolChannelExecutor` 实现
- 在 `AndroidToolManager` 注册这个 channel
- 让它参与 `tools.json` 聚合

native 侧通常也不需要改具体业务代码，只会自动看到新的 schema。

### 场景 3：扩展现有 channel 的内部能力

例如给 `android_view_context_tool` 新增真实 `web_dom` 抓取能力，通常：

- 在 Java channel 内部新增 source/provider/runtime
- 保持外部顶层 tool 名称稳定
- 尽量不改 JNI bridge

这是当前架构最推荐的扩展方式。

## 推荐的阅读顺序

如果你第一次看这个项目，建议按这个顺序读代码：

1. [AgentInitializer.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentInitializer.java)
2. [AndroidToolManager.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java)
3. [AndroidToolChannelExecutor.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/channel/AndroidToolChannelExecutor.java)
4. [AndroidToolCallback.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/tool/AndroidToolCallback.java)
5. [NativeMobileAgentApi.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/api/impl/NativeMobileAgentApi.java)
6. [NativeAgent.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/core/NativeAgent.java)
7. [android_tools.hpp](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/include/icraw/platform/android/android_tools.hpp)
8. [native_agent.cpp](/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/src/platform/android/native_agent.cpp)

## 小结

当前项目里的 Android tool 架构，不是 native 和 Java 各自维护一套互不相关的工具系统，而是：

- Java 定义并实现 Android 侧工具能力
- native 把这些能力注册成 LLM 可调用工具
- 实际执行时再通过 callback 回到 Java

因此，最值得记住的一句话是：

**native 持有工具壳和调度入口，Java 持有 Android 工具的真实实现。**
