# Codebase Structure

**Analysis Date:** 2026-03-12

## Directory Layout

```
mobile-agent/
├── app/                         # Android 应用主模块
├── agent-android/               # Android Library - UI和工具管理层
├── agent-core/                  # Android Library - Java API + C++ 核心
├── floating-ball/               # 悬浮球模块
├── cxxplatform/                 # 独立 C++ 平台 (测试/参考)
├── docs/                        # 文档
├── build.gradle                 # 根构建文件
├── settings.gradle              # Gradle 设置
└── config.json.template         # 配置模板
```

## Directory Purposes

### app/ (Android 应用主模块)

**Purpose:** Android 应用主模块，包含入口 Activity、工具实现、工作空间管理

**Contains:**
- 入口 Activity: `LauncherActivity.java`
- 应用类: `App.java`, `AppLifecycleObserver.java`
- 工具实现: `tool/` 目录
- 语音识别: `voice/MockVoiceRecognizer.java`

**Key files:**
- `app/src/main/java/com/hh/agent/LauncherActivity.java` - 应用启动入口
- `app/src/main/java/com/hh/agent/app/App.java` - Application 类

---

### agent-android/ (UI层)

**Purpose:** Android Library 模块，包含 MVP UI 组件、工具管理

**Contains:**
- Activity: `AgentActivity.java`
- MVP Contract: `contract/MainContract.java`
- Presenter: `presenter/MainPresenter.java`
- UI 组件: `ui/MessageAdapter.java`
- 工具管理: `AndroidToolManager.java`, `WorkspaceManager.java`
- 初始化: `AgentInitializer.java`
- 语音识别接口: `voice/IVoiceRecognizer.java`, `voice/VoiceRecognizerHolder.java`

**Key files:**
- `agent-android/src/main/java/com/hh/agent/android/AgentActivity.java` - 主界面 Activity
- `agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java` - 业务逻辑
- `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java` - 工具管理

---

### agent-core/ (核心层)

**Purpose:** Android Library 模块，包含 Java API 接口、C++ 原生代码

**Contains:**
- Java API: `api/MobileAgentApi.java`, `api/NativeMobileAgentApi.java`
- JNI 绑定: `NativeAgent.java`
- 数据模型: `model/Message.java`, `model/Session.java`
- 工具接口: `ToolExecutor.java`, `AndroidToolCallback.java`
- C++ 核心: `src/mobile_agent.cpp`
- C++ 核心模块: `src/core/` - agent_loop, llm_provider, memory_manager 等

**Key files:**
- `agent-core/src/main/java/com/hh/agent/library/api/MobileAgentApi.java` - API 接口
- `agent-core/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` - API 实现
- `agent-core/src/main/cpp/native_agent.cpp` - JNI 入口
- `agent-core/src/main/cpp/src/mobile_agent.cpp` - C++ Agent 实现

---

### floating-ball/ (悬浮球模块)

**Purpose:** 悬浮球功能模块，提供浮动窗口和容器 Activity

**Contains:**
- `FloatingBallManager.java` - 悬浮球管理
- `FloatingBallView.java` - 悬浮球视图
- `FloatingBallReceiver.java` - 广播接收器
- `ContainerActivity.java` - 容器 Activity

**Key files:**
- `floating-ball/src/main/java/com/hh/agent/floating/FloatingBallManager.java` - 悬浮球管理
- `floating-ball/src/main/java/com/hh/agent/floating/ContainerActivity.java` - 容器 Activity

---

### cxxplatform/ (独立 C++ 平台)

**Purpose:** 独立 C++ 平台，用于测试和参考实现

**Contains:**
- C++ 源码: `src/` 目录
- 头文件: `include/` 目录
- 测试: `tests/` 目录
- 文档: `docs/` 目录

**Key files:**
- `cxxplatform/CMakeLists.txt` - 构建配置

---

### docs/ (文档)

**Purpose:** 项目文档目录

**Contains:**
- Android 工具扩展指南
- 其他技术文档

---

## Key File Locations

### Entry Points

- **应用启动:** `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/LauncherActivity.java`
- **主界面:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentActivity.java`
- **Agent 初始化:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentInitializer.java`

### Configuration

- **配置模板:** `/Users/caixiao/Workspace/projects/mobile-agent/config.json.template`
- **Gradle 配置:** `/Users/caixiao/Workspace/projects/mobile-agent/build.gradle`

### Core Logic

- **Java API:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/java/com/hh/agent/library/api/`
- **JNI 入口:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/native_agent.cpp`
- **C++ Agent:** `/Users/caixiao/Workspace/projects/mobile-agent/agent-core/src/main/cpp/src/mobile_agent.cpp`

### Testing

- **单元测试:** `/Users/caixiao/Workspace/projects/mobile-agent/app/src/test/java/com/hh/agent/`

---

## Naming Conventions

### Files

**Java:**
- 类文件: `PascalCase.java` - 如 `MainPresenter.java`, `AndroidToolManager.java`
- 包名: 全小写，如 `com.hh.agent.android`

**C++:**
- 源文件: `snake_case.cpp` - 如 `native_agent.cpp`, `mobile_agent.cpp`
- 头文件: `snake_case.hpp` 或 `snake_case.h`
- 类/命名空间: `PascalCase` - 如 `MobileAgent`, `AgentLoop`

### Directories

**Java:**
- 包目录: 全小写，用点分隔 - 如 `com/hh/agent/android/presenter`
- 资源目录: 小写 - 如 `res/layout/`, `res/values/`

**C++:**
- 目录: `snake_case` - 如 `src/core/`, `include/icraw/`

---

## Where to Add New Code

### New Feature (Android UI)

1. **UI 实现:**
   - 如果是 Activity: 放在 `agent-android/src/main/java/com/hh/agent/android/`
   - 如果是 View 组件: 放在 `agent-android/src/main/java/com/hh/agent/android/ui/`

2. **业务逻辑:**
   - Presenter: `agent-android/src/main/java/com/hh/agent/android/presenter/`
   - Contract: `agent-android/src/main/java/com/hh/agent/android/contract/`

3. **测试:**
   - 单元测试: `app/src/test/java/com/hh/agent/`

---

### New Android Tool

1. **工具实现:**
   - 放在 `app/src/main/java/com/hh/agent/tool/`
   - 实现 `ToolExecutor` 接口

2. **注册工具:**
   - 在 `AgentInitializer.java` 或 `AndroidToolManager` 中注册

---

### New C++ Feature

1. **核心逻辑:**
   - 放在 `agent-core/src/main/cpp/src/core/`

2. **工具相关:**
   - 放在 `agent-core/src/main/cpp/src/tools/`

3. **头文件:**
   - 放在 `agent-core/src/main/cpp/include/icraw/`

---

## Special Directories

### res/ (资源目录)

**Purpose:** Android 资源文件（布局、字符串、样式等）

**Location:** `app/src/main/res/`, `agent-android/src/main/res/`, `floating-ball/src/main/res/`

**Generated:** No (committed)

---

### assets/ (资源目录)

**Purpose:** 应用资源文件（配置文件、图片等）

**Location:** `app/src/main/assets/`, `agent-android/src/main/assets/`

**Generated:** No (committed)

---

### cpp/ (C++ 源码)

**Purpose:** C++ 原生代码

**Location:** `agent-core/src/main/cpp/`

**Contains:**
- JNI 绑定: `native_agent.cpp`, `android_tools.cpp`
- 核心实现: `src/` 子目录

**Generated:** No (committed)

---

### test/ (测试代码)

**Purpose:** 单元测试

**Location:** `app/src/test/java/com/hh/agent/`

**Generated:** No (committed)

---

*Structure analysis: 2026-03-12*
