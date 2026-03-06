# Phase 1: Build System & Agent Core - Context

**Gathered:** 2026-03-03
**Status:** Context gathered

<domain>
## Phase Boundary

C++ NDK 编译环境和 Agent 引擎基础。确保 cxxplatform 代码可以在 Android 上编译为可用的 .so 库文件，并提供基本的聊天功能。

**Scope includes:**
- Gradle 构建配置 (CMake 集成)
- C++ 依赖库处理 (sqlite3, curl, spdlog, nlohmann-json)
- MobileAgent 类的 JNI 暴露
- 日志系统适配 Android logcat

</domain>

<decisions>
## Implementation Decisions

### 移植策略
- 先进行最小范围移植，通过编译
  - cxxplatform/include 头文件
  - cxxplatform/src 实现文件
- 复制必要的文件到目标模块进行构建，不改变原有cxxplatform目录中的代码

### agent模块结构
- 规划成一个标准的android native library
- 是cxxplatform移植的目标模块

### 任务执行顺序
- 优先完成conan依赖的安装和编译并且确认可以编译通过
- 然后再进行移植任务，移植后进行编译测试

### Dependencies
- 使用 Conan 安装c++依赖库
  - nlohmann-json: header-only，直接包含源码
  - spdlog: header-only，直接包含源码
  - sqlite3: 使用conan引入配方，编译成android可用的so
  - curl: 使用conan引入配方，编译成android可用的so
- Conan配置
  - 依赖的版本，对齐/Users/caixiao/Workspace/projects/android-cxx-thirdpartylib/lib/conanfile.py
  - conan install使用的profile，对齐/Users/caixiao/Workspace/projects/android-cxx-thirdpartylib/lib/android.profile
  - Android的Library集成Conan，参考https://docs.conan.io/2/examples/cross_build/android/android_studio.html

### Build
- 编译为共享库 (.so)，命名为 libicraw.so
- 其他库也使用共享库的形式比如curl和sqlite3
- ABI 支持: arm64-v8a

### Logging
- 使用 Android logcat (__android_log_print)
- 保留 spdlog 接口，内部转发到 Android log

### Configuration
- 程序化配置：Java 创建配置对象，通过 JNI 传入 C++
- LLM API Key 等敏感信息由 Java 层管理

</decisions>

<specifics>
## Specific Ideas

No specific requirements yet — discussing implementation options.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `cxxplatform/CMakeLists.txt` - 已有 CMake 配置，有 ANDROID 平台条件
- `cxxplatform/include/icraw/mobile_agent.hpp` - MobileAgent 类定义
- `lib/build.gradle` - 现有 NDK 模块配置

### Established Patterns
- 现有 lib 模块使用 CMake 构建 C++ 代码
- NDK 26.3.11579264 已配置

### Integration Points
- Java 代码将通过 JNI 调用 C++ MobileAgent
- 需要创建 JNI 绑定层

</code_context>

<deferred>
## Deferred Ideas

- Phase 1 需要分为两个阶段执行：
  1. 首先完成 Conan 依赖的安装和编译
  2. 然后进行 cxxplatform 移植工作

</deferred>

---

*Phase: 01-build-system-agent-core*
*Context gathered: 2026-03-03*
