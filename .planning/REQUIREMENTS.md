# Requirements: Mobile Agent - C++ 移植版

**Defined:** 2026-03-03
**Core Value:** 在 Android 设备上运行本地 AI Agent，提供实时对话和设备控制能力，无需依赖远程服务器。

## v1 Requirements

### Agent 核心

- [x] **AGEN-01**: C++ Agent 引擎可以在 Android NDK 环境中编译运行
- [x] **AGEN-02**: Agent 引擎支持基本的对话循环 (接收输入 → 处理 → 输出响应)
- [x] **AGEN-03**: Agent 引擎可以通过 JNI 与 Java 层双向通信

### JNI 桥接

- [ ] **JNI-01**: Java 代码可以调用 C++ Agent 引擎的初始化方法
- [ ] **JNI-02**: Java 代码可以向 C++ Agent 发送消息并接收响应
- [ ] **JNI-03**: C++ 层的日志可以输出到 Android logcat

### API 集成

- [ ] **API-01**: 创建 Java NanobotApi 接口的本地实现 (NativeNanobotApi)
- [ ] **API-02**: 本地实现支持 getSession、getHistory、sendMessage 方法
- [ ] **API-03**: 保持与现有 HTTP 实现 (HttpNanobotApi) 的接口兼容性

### 系统集成

- [x] **SYS-01**: Gradle 构建脚本包含 C++ 编译任务
- [x] **SYS-02**: C++ 代码使用 CMake 构建
- [x] **SYS-03**: 支持 armeabi-v7a, arm64-v8a, x86, x86_64 架构

## v2 Requirements

### 高级功能

- **ADV-01**: Agent 支持流式输出 (streaming response)
- **ADV-02**: Agent 支持多模态输入 (文本 + 图片)
- **ADV-03**: Agent 可以访问 Android 系统 API (获取设备信息、发送通知等)

### 性能优化

- **PERF-01**: Agent 冷启动时间 < 3 秒
- **PERF-02**: 内存占用 < 100MB

## Out of Scope

| Feature | Reason |
|---------|--------|
| 远程服务器模式 | v1 专注于本地运行 |
| 语音交互 | v2 考虑 |
| 视频处理 | 超出范围 |
| iOS 支持 | 独立工作 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AGEN-01 | Phase 1 | Complete |
| AGEN-02 | Phase 1 | Complete |
| AGEN-03 | Phase 1 | Complete |
| JNI-01 | Phase 2 | Pending |
| JNI-02 | Phase 2 | Pending |
| JNI-03 | Phase 2 | Pending |
| API-01 | Phase 3 | Pending |
| API-02 | Phase 3 | Pending |
| API-03 | Phase 3 | Pending |
| SYS-01 | Phase 1 | Complete |
| SYS-02 | Phase 1 | Complete |
| SYS-03 | Phase 1 | Complete |

**Coverage:**
- v1 requirements: 12 total
- Mapped to phases: 12
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-03*
*Last updated: 2026-03-03 after initial definition*
