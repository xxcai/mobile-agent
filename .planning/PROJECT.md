# Mobile Agent - 手机上的 AI Agent

## What This Is

在 Android 手机上运行的 AI Agent，通过文字聊天（后续支持语音）作为交互界面，能调用手机应用提供的各种能力（打卡、发消息等）来自动化工作流。

## Core Value

让用户通过自然对话，指挥手机自动完成日常任务。

## Requirements

### Validated

- ✓ Android 聊天界面 (Java) — 现有代码库
- ✓ MVP 架构模式 — 现有代码库
- ✓ OkHttp HTTP 客户端 — 现有代码库
- ✓ C++ NDK 集成基础 — v1.0
- ✓ C++ Agent 引擎 — v1.0
- ✓ JNI 通信 — v1.0
- ✓ API Key 配置 — v1.1
- ✓ 清理 agent 模块 — v1.2
- ✓ 预置 workspace — v1.3 (shipped 2026-03-04)
- ✓ Android Tools 通道 — v1.4 (shipped 2026-03-05)
- ✓ LLM → Android 调用管道 — v1.5 (shipped 2026-03-05)
- ✓ 自定义 Skills 机制 — v1.6 (shipped 2026-03-06)
- ✓ Agent 调用 Tools — v1.6 (shipped 2026-03-06)
- ✓ Nanobot → MobileAgent 重命名 — v2.0 (shipped 2026-03-09)
- ✓ 代码迁移 (平台逻辑从 agent 到 app) — v2.0 (shipped 2026-03-09)
- ✓ 纯 Java AAR (无 Android 依赖) — v2.0 (shipped 2026-03-09)
- ✓ JSON String API 接口 — v2.0 (shipped 2026-03-09)
- ✓ agent-android 模块 — v2.1 (shipped 2026-03-09)
- ✓ agent → agent-core 重命名 — v2.1 (shipped 2026-03-09)
- ✓ app 简化为壳 — v2.1 (shipped 2026-03-09)
- ✓ 代码下沉 (Tools/Manager → agent-android) — v2.1 (shipped 2026-03-09)
- ✓ 启动流程优化 (clearContext 内存泄漏修复) — v2.1 (shipped 2026-03-09)
- ✓ 接入文档 (README + API) — v2.1 (shipped 2026-03-09)
- ✓ App 层 Tool 动态注册接口 — v2.2 (shipped 2026-03-10)
- ✓ Tool 生命周期管理 — v2.2 (shipped 2026-03-10)
- ✓ 动态 Tool 调用与验证 — v2.2 (shipped 2026-03-10)
- ✓ 语音按钮 UI — v2.3 (shipped 2026-03-11)
- ✓ 按压说话交互 — v2.3 (shipped 2026-03-11)
- ✓ 实时语音转文字 — v2.3 (shipped 2026-03-11)
- ✓ 语音能力依赖注入 — v2.3 (shipped 2026-03-11)
- ✓ 悬浮球入口 — v2.5 (shipped 2026-03-12)
- ✓ 容器Activity — v2.5 (shipped 2026-03-12)
- ✓ Broadcast 通信机制 — v2.5 (shipped 2026-03-12)
- ✓ Android 录音权限处理 — v2.3 (shipped 2026-03-11)

### Active

暂无

## Current Milestone: Planning

**Last milestone completed:** v2.5 容器Activity模块 (shipped 2026-03-12)

**Next milestone:** 待规划

## Current Milestone: v2.6 主界面重构

**Goal:** 重构 app 主界面，实现 MainActivity 启动页 + Fragment 容器化 + 悬浮球合并 + Agent 后台运行

**Target features:**
1. floating-ball 模块合并到 android-agent
2. 新建 MainActivity 作为启动页
3. 悬浮球在满足条件时浮在 MainActivity 上
4. AgentActivity 界面用 Fragment 承载到 ContainerActivity
5. Agent 后台运行 + 记忆恢复能力
6. 界面优化
7. 保留现有语音功能

## Context

**现有代码库:**
- `mobile-agent` - Android 应用（含 C++ Agent 引擎）
- `cxxplatform` - Windows C++ Agent 原型（参考）
- `agent` - Android C++ Agent 模块

**架构演进:**
- ~~旧架构：App 通过 HTTP 连接 PC 上的 Nanobot 服务（需要 adb reverse）~~
- **新架构：Agent 直接运行在手机本地**，不依赖 PC

**Agent 运行时:**
- LLM 调用在手机本地执行
- 不需要网络代理或 adb 端口转发
- 保护隐私，离线可用（需要 LLM API key）

## Constraints

- **技术栈**: C++ (原生) + Java (Android UI) — 必须
- **兼容性**: minSdk 24 (Android 7.0)
- **NDK**: NDK 26.3.11579264
- **构建**: Gradle 8.12.1, AGP 8.3.2

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 本地运行 | Agent 运行在手机本地，保护隐私 | ✓ |
| 保持 UI 不变 | 减少回归风险 | ✓ |
| JNI 通信 | 标准 Java/C++ 互操作方式 | ✓ |
| local.properties | 管理本地 apiKey 配置 | ✓ |
| workspace 预置 | 让 Agent 有初始身份和能力 | ✓ v1.3 shipped |
| Android Tools 通道 | 统一的 JNI 回调 + 工具注册机制 | ✓ v1.4 shipped |
| 通用 Android 调用管道 | LLM 通过 JSON 参数调用 Android 功能 | ✓ v1.5 shipped |
| 完全自主调用 | LLM 直接执行，无需用户确认 | ✓ v1.5 shipped |
| 自定义 Skills | Agent 通过 Skill 调用 Android Tools | ✓ v1.6 shipped |
| Skill 调用 Tool | 多步骤工具链，Tool 结果返回给 LLM | ✓ v1.6 shipped |
| Nanobot → MobileAgent | 统一品牌命名 | ✓ v2.0 shipped |
| 平台分离 | Tools/Manager 在 app，agent 纯 Java | ✓ v2.0 shipped |
| Callback 接口 | AndroidToolCallback 解耦 agent/app | ✓ v2.0 shipped |
| JSON String API | app 读取文件，agent 只接收数据 | ✓ v2.0 shipped |
| 语音转文字接口注入 | 语音能力由上层 app 通过接口注入 | ✓ v2.3 shipped |
| 悬浮球单例模式 | 不使用 Service，使用 WindowManager | ✓ v2.5 shipped |
| Broadcast 通信 | Activity 与悬浮球通过广播协调 | ✓ v2.5 shipped |
| SingleTop Activity | 避免容器Activity重复创建 | ✓ v2.5 shipped |

---
*Last updated: 2026-03-12 — v2.5 completed*
