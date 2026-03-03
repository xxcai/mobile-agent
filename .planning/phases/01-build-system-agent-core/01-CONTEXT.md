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

### Dependencies
- 使用 NDK 兼容库替换 vcpkg 依赖
  - nlohmann-json: header-only，直接包含源码
  - sqlite3: 使用 Android NDK 内置或 ndkports
  - curl: 使用 ndkports 或删除（简化版）
  - spdlog: 使用 ndkports 或替换为 Android log

### Build
- 编译为共享库 (.so)，命名为 libicraw.so
- ABI 支持: armeabi-v7a, arm64-v8a, x86, x86_64

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

None yet — discussion stayed within phase scope.

</deferred>

---

*Phase: 01-build-system-agent-core*
*Context gathered: 2026-03-03*
