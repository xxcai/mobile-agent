# ROADMAP: Mobile Agent - C++ 移植版

## Project Overview

**Core Value:** 在 Android 设备上运行本地 AI Agent，提供实时对话和设备控制能力，无需依赖远程服务器。

**Depth:** Quick (3-5 phases)
**Total Requirements:** 12 v1

---

## Milestones

### v1.0: C++ 移植 (已完成)

- Phase 1-3: Build System, JNI Bridge, API Integration

### v1.1: App 集成 (已完成)

---

## Phases

- [x] **Phase 1: Build System & Agent Core** - C++ NDK 编译环境和 Agent 引擎基础 (completed 2026-03-03)
- [x] **Phase 2: JNI Bridge** - Java ↔ C++ 双向通信层 (completed 2026-03-03)
- [x] **Phase 3: API Integration** - NativeNanobotApi 实现替换 HTTP (completed 2026-03-03)

- [x] **Phase 4: App 集成** - 将 NativeNanobotApi 集成到 MainActivity (completed 2026-03-03)

---

## Phase Details

### Phase 1: Build System & Agent Core

**Goal:** C++ Agent 引擎可以在 Android NDK 环境中编译运行，支持基本对话循环

**Depends on:** Nothing (first phase)

**Requirements:** AGEN-01, AGEN-02, AGEN-03, SYS-01, SYS-02, SYS-03

**Success Criteria** (what must be TRUE):
  1. Gradle 构建成功生成 C++ .so 库文件
  2. C++ 代码为所有目标架构 (armeabi-v7a, arm64-v8a, x86, x86_64) 编译成功
  3. Agent 引擎可以初始化并进入对话循环状态
  4. Java 层可以通过 JNI 调用 C++ Agent 方法
  5. CMake 构建配置正确集成到 Gradle

**Plans:** 3/3 plans complete

---

### Phase 2: JNI Bridge

**Goal:** Java 代码可以与 C++ Agent 引擎双向通信，日志输出到 logcat

**Depends on:** Phase 1

**Requirements:** JNI-01, JNI-02, JNI-03

**Success Criteria** (what must be TRUE):
  1. Java 代码可以调用 C++ Agent 引擎的初始化方法
  2. Java 代码可以向 C++ Agent 发送消息并同步接收响应
  3. C++ 层的日志 (LOGD/LOGE) 输出到 Android logcat
  4. JNI 层正确处理字符串和对象引用生命周期

**Plans:** 1/1 plans complete

---

### Phase 3: API Integration

**Goal:** 创建 NativeNanobotApi 实现，替换现有 HTTP 实现

**Depends on:** Phase 2

**Requirements:** API-01, API-02, API-03

**Success Criteria** (what must be TRUE):
  1. NativeNanobotApi 类实现 NanobotApi 接口
  2. getSession 方法返回正确的 Session 对象
  3. getHistory 方法返回历史消息列表
  4. sendMessage 方法发送消息并返回 Agent 响应
  5. 现有 Android UI 可以无缝切换到本地实现 (接口兼容)

**Plans:** 1/1 plans complete

---

## Coverage

| Phase | Goal | Requirements |
|-------|------|--------------|
| 1 - Build System & Agent Core | C++ NDK 编译环境 + Agent 引擎基础 | Complete    | 2026-03-03 | 2 - JNI Bridge | Java ↔ C++ 双向通信 | Complete    | 2026-03-03 | 3 - API Integration | NativeNanobotApi 实现 | Complete    | 2026-03-03 | Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Build System & Agent Core | 3/3 | Complete | SYS-01, SYS-02, SYS-03 |
| 2. JNI Bridge | 1/1 | Complete | JNI-01, JNI-02, JNI-03 |
| 3. API Integration | 0/1 | Not started | - |

---

*Generated: 2026-03-03*
