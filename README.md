# Mobile Agent

面向 Android 的移动端 Agent 工程，当前以两个库模块为核心：

- `agent-core`: Agent 核心能力，包含 Java API、JNI 桥接和 C++ 推理/工具执行引擎
- `agent-android`: Android 侧集成层，提供初始化入口、对话 UI、工作空间准备和 Android Tool 管理

本文档只覆盖当前工程里实际使用的 Android 方案，忽略 `cxxplatform/` 目录。

## 模块关系

```text
app (示例宿主)
  └─ agent-android
       └─ agent-core
```

- `agent-core` 负责底层能力，不直接提供完整 UI
- `agent-android` 依赖 `agent-core`，补齐 Android 场景下的初始化、Fragment UI、语音识别接入和悬浮球入口
- `agent-android` 当前也承载了 route tooling 的通用运行时、bridge 适配器和 route 打开时序
- `app` 是示例工程，用来演示如何把两个库模块接到应用里，并保留宿主数据源、demo 页面和装配代码

## 模块说明

### agent-core

路径: `agent-core/`

职责:

- 定义对外 Java API，如 `com.hh.agent.core.api.MobileAgentApi`
- 提供当前默认实现 `com.hh.agent.core.api.impl.NativeMobileAgentApi`
- 通过 `NativeAgent` 连接 JNI 与本地 C++ 引擎
- 管理消息、工具调用、历史记录等核心模型
- 打包 workspace 相关提示词和技能资源

主要目录:

```text
agent-core/
├── src/main/java/com/hh/agent/core/
│   ├── api/                  # Java API 接口
│   │   └── impl/             # 当前默认实现，如 NativeMobileAgentApi
│   ├── event/                # 流式事件接口
│   ├── model/                # Message / ToolCall 等模型
│   ├── shortcut/             # Shortcut contract 类型
│   ├── tool/                 # ToolResult 等通用结果类型
│   └── NativeAgent.java      # JNI bridge
├── src/main/cpp/
│   ├── include/icraw/
│   │   ├── core/             # 可跨平台复用的核心头文件
│   │   ├── log/              # 日志公开头
│   │   └── platform/android/ # Android bridge 相关头文件
│   └── src/
│       ├── core/             # C++ 核心流程
│       ├── log/              # 日志实现
│       └── platform/android/ # Android/JNI bridge 实现
└── src/main/assets/workspace/ # 内置 workspace 提示词和 skills
```

构建特征:

- Android Library
- `minSdk 24`
- `compileSdk 34`
- Java 21
- 使用 CMake + NDK 构建 native 层
- 当前 `abiFilters` 为 `arm64-v8a`

### agent-android

路径: `agent-android/`

职责:

- 提供 Android 侧初始化入口 `AgentInitializer`
- 提供可嵌入宿主页面的 `AgentFragment`
- 管理 Android Tools 注册与 schema 生成
- 提供 route tooling 运行时与 route 解析/打开能力
- 初始化工作空间目录
- 提供语音识别、悬浮球、容器页等 Android 集成能力

主要目录:

```text
agent-android/
├── src/main/java/com/hh/agent/android/
│   ├── AgentInitializer.java
│   ├── AgentFragment.java
│   ├── AndroidToolManager.java
│   ├── route/               # RouteHint / RouteResolver / RouteOpener / bridge 适配器
│   └── WorkspaceManager.java
└── src/main/res/             # Fragment UI、动画、drawable、主题资源
```

构建特征:

- Android Library
- 依赖 `project(':agent-core')`
- 内置基于 RecyclerView 的对话界面
- 集成 Markwon 用于 Markdown 渲染

## 快速开始

### 环境要求

- Android Studio
- Android SDK 34
- NDK `26.3.11579264`
- Java 21

### 构建项目

构建 `agent-core` 前，先在 `agent-core/` 目录准备 Android C++ 依赖：

```bash
cd agent-core
conan install . -pr android.profile -s arch=armv8 --build missing
cd ..
```

构建全部模块：

```bash
./gradlew assembleDebug
```

只构建两个核心库：

```bash
./gradlew :agent-core:assembleDebug :agent-android:assembleDebug
```

构建产物默认位于：

- `agent-core/build/outputs/aar/agent-core-debug.aar`
- `agent-android/build/outputs/aar/agent-android-debug.aar`

## 宿主应用接入

### 1. 添加依赖

如果宿主和本工程同仓，可直接依赖模块：

```gradle
dependencies {
    implementation project(':agent-core')
    implementation project(':agent-android')
}
```

如果宿主通过 AAR 集成，需要自行配置 `flatDir` 或 `files(...)`，并补齐三方依赖。

### 2. 提供 `assets/config.json`

`AgentInitializer` 会从宿主应用的 `assets/config.json` 读取配置，因此接入方需要在自己的应用里创建这个文件。

最小可用示例：

```json
{
  "provider": {
    "apiKey": "your-api-key",
    "baseUrl": "https://api.openai.com/v1"
  },
  "agent": {
    "model": "gpt-4.1"
  }
}
```

当前初始化阶段实际会读取这些字段：

- `provider.apiKey`
- `provider.baseUrl`
- `agent.model`

说明：

- `workspacePath` 由 `agent-android` 初始化 workspace 后自动注入，宿主通常不需要手动配置
- `provider.timeout`、`agent.maxIterations`、`agent.maxTokens`、`agent.compaction` 等字段在配置结构中也支持，但当前 Android 初始化代码没有显式从 `config.json` 逐项透传这些字段时，建议先只使用上面的最小集合

### 3. 初始化 Agent

初始化入口在 `agent-android` 的 `AgentInitializer`。

典型流程：

1. 准备可选的语音识别实现 `IVoiceRecognizer`
2. 准备业务侧 shortcut 集合 `Collection<? extends ShortcutExecutor>`
3. 如需接入宿主自定义日志体系，可选调用 `AgentInitializer.setLogger(...)`
4. 调用 `AgentInitializer.initialize(...)`
5. 在初始化完成后按需调用 `initializeFloatingBall(...)`

示意代码：

```java
import com.hh.agent.core.shortcut.ShortcutExecutor;

List<ShortcutExecutor> shortcuts = new ArrayList<>();
shortcuts.add(new SearchContactsShortcut());
shortcuts.add(new SendImMessageShortcut());

AgentInitializer.initialize(
        applicationContext,
        voiceRecognizer,
        shortcuts,
        viewContextSourcePolicy,
        () -> {
            // native agent 初始化完成
        }
);
```

如果需要启用默认悬浮球入口，可在初始化完成后调用：

```java
AgentInitializer.initialize(
        applicationContext,
        voiceRecognizer,
        shortcuts,
        viewContextSourcePolicy,
        () -> AgentInitializer.initializeFloatingBall(
                (Application) applicationContext,
                null
        )
);
```

`initializeFloatingBall(...)` 现在会自动注册默认的 `ActivityLifecycleCallbacks`，默认规则如下：

- `ContainerActivity` 展示时隐藏悬浮球
- 宿主 App 在前台时显示悬浮球
- 宿主 App 退到后台时隐藏悬浮球

如果宿主还需要额外指定某些页面隐藏悬浮球，可直接传入这些页面的完整类名列表：

```java
AgentInitializer.initialize(
        applicationContext,
        voiceRecognizer,
        shortcuts,
        viewContextSourcePolicy,
        () -> AgentInitializer.initializeFloatingBall(
                (Application) applicationContext,
                Arrays.asList(
                        "com.example.YourHiddenActivity",
                        "com.example.OtherHiddenActivity"
                )
        )
);
```

当前只保留一个公开入口：

- `initializeFloatingBall(Application application, List<String> hiddenActivityClassNames)`

logger 注入入口：

- `setLogger(AgentLogger logger)`
- `getLogger()`

说明：

- `setLogger(...)` 是可选能力；不调用时，`agent-android` 默认使用内置 `DefaultAgentLogger`
- 当前宿主业务能力统一通过 `ShortcutExecutor -> ShortcutRuntime -> run_shortcut` 暴露给模型
- 如缺少某个 shortcut 的细节定义，应优先调用 `describe_shortcut`
- 当前仓库里的 `app` 模块没有额外实现宿主 logger，而是直接使用库默认实现
- `agent-android` 当前日志格式、事件清单和排查命令见 `docs/logging/agent-android-logging.md`
- Prompt 构建顺序和工具展示方式见 `docs/architecture/prompt-construction.md`
- route tooling 的接入方式、URI 规则和 App 层替换点见 `docs/guides/android-route-tooling.md`

调用约定：

- 传 `null` 表示只使用默认规则
- 传 `List<String>` 表示额外追加需要隐藏悬浮球的 Activity 完整类名列表

### 4. 嵌入对话界面

`agent-android` 提供了 `AgentFragment`，宿主应用可直接嵌入该 Fragment 作为默认对话 UI。

### 5. 使用底层 API

如果你只需要核心能力，也可以直接使用 `agent-core` 中当前默认实现 `NativeMobileAgentApi`：

```java
import com.hh.agent.core.api.impl.NativeMobileAgentApi;

NativeMobileAgentApi api = NativeMobileAgentApi.getInstance();
api.sendMessageStream(content, sessionKey, listener);
```

当前对外接口定义见：

- `agent-core/src/main/java/com/hh/agent/core/api/MobileAgentApi.java`
- `agent-core/src/main/java/com/hh/agent/core/api/impl/NativeMobileAgentApi.java`

说明：

- `MobileAgentApi` 是稳定接口层
- `NativeMobileAgentApi` 是当前 Android 集成链路使用的实现类
- `NativeAgent` 仍位于根包，当前用于保持 JNI 符号名兼容

## 目录概览

```text
mobile-agent/
├── agent-core/               # Agent 核心库
├── agent-android/            # Android 集成层和默认 UI
├── app/                      # 示例宿主应用
├── docs/                     # 补充文档
├── build.gradle
└── settings.gradle
```

## 说明

- 当前 `README` 只描述 `agent-core`、`agent-android` 和示例宿主 `app`
- `cxxplatform/` 为独立目录，不作为当前 Android 集成方案说明的一部分
- 补充文档总索引见 `docs/README.md`
